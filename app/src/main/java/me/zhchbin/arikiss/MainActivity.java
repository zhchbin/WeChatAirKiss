package me.zhchbin.arikiss;

import android.app.ProgressDialog;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Random;

public class MainActivity extends ActionBarActivity {
    private EditText mSSIDEditText;
    private EditText mPasswordEditText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mSSIDEditText = (EditText)findViewById(R.id.ssidEditText);
        mPasswordEditText = (EditText)findViewById(R.id.passwordEditText);

        Context context = getApplicationContext();
        ConnectivityManager connManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        if (networkInfo.isConnected()) {
            final WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            final WifiInfo connectionInfo = wifiManager.getConnectionInfo();
            if (connectionInfo != null) {
                mSSIDEditText.setText(connectionInfo.getSSID());
                mSSIDEditText.setEnabled(false);
            }
        }
    }

    public void onConnectBtnClick(View view) throws SocketException {
        String ssid = mSSIDEditText.getText().toString().replaceAll("^\"|\"$", "");
        String password = mPasswordEditText.getText().toString();
        if (ssid.isEmpty() || password.isEmpty()) {
            Context context = getApplicationContext();
            CharSequence text = "Please input ssid and password.";
            int duration = Toast.LENGTH_SHORT;
            Toast toast = Toast.makeText(context, text, duration);
            toast.show();
            return;
        }

        new AirKissTask(this).execute(ssid, password);
    }

    private class AirKissTask extends AsyncTask<String, Void, Void> {
        private static final int PORT = 10000;
        private final byte DUMMY_DATA[] = new byte[1500];

        private ProgressDialog dialog;
        private Context context;
        private DatagramSocket mSocket;

        public AirKissTask(ActionBarActivity activity) throws SocketException {
            context = activity;
            dialog = new ProgressDialog(context);
        }

        @Override
        protected void onPreExecute() {
            this.dialog.setMessage("Connecting :)");
            this.dialog.show();
        }

        private void sendPacketAndSleep(int length) {
            try {
                DatagramPacket pkg = new DatagramPacket(DUMMY_DATA, length, InetAddress.getByName("255.255.255.255"), PORT);
                mSocket.send(pkg);
                Thread.sleep(4);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void sendLeadingPart() {
            for (int i = 0; i < 50; ++i) {
                for (int j = 1; j <= 4; ++j)
                    sendPacketAndSleep(j);
            }
        }

        protected int CRC8(byte data[]) {
            int len = data.length;
            int i = 0;
            byte crc = 0x00;
            while (len-- > 0) {
                byte extract = data[i++];
                for (byte tempI = 8; tempI != 0; tempI--) {
                    byte sum = (byte) ((crc & 0xFF) ^ (extract & 0xFF));
                    sum = (byte) ((sum & 0xFF) & 0x01);
                    crc = (byte) ((crc & 0xFF) >>> 1);
                    if (sum != 0) {
                        crc = (byte)((crc & 0xFF) ^ 0x8C);
                    }
                    extract = (byte) ((extract & 0xFF) >>> 1);
                }
            }
            return (int) (crc & 0xFF);
        }

        protected int CRC8(String stringData) {
            return CRC8(stringData.getBytes());
        }

        private void sendMagicCode(String ssid, String password) {
            int length = ssid.length() + password.length() + 1;
            int magicCode[] = new int[4];
            magicCode[0] = 0x00 | (length >>> 4 & 0xF);
            magicCode[1] = 0x10 | (length & 0xF);
            int crc8 = CRC8(ssid);
            magicCode[2] = 0x20 | (crc8 >>> 4 & 0xF);
            magicCode[3] = 0x30 | (crc8 & 0xF);
            for (int i = 0; i < 20; ++i) {
                for (int j = 0; j < 4; ++j)
                    sendPacketAndSleep(magicCode[j]);
            }
        }

        private void sendPrefixCode(String password) {
            int length = password.length();
            int prefixCode[] = new int[4];
            prefixCode[0] = 0x40 | (length >>> 4 & 0xF);
            prefixCode[1] = 0x50 | (length & 0xF);
            int crc8 = CRC8(new byte[] {(byte)length});
            prefixCode[2] = 0x60 | (crc8 >>> 4 & 0xF);
            prefixCode[3] = 0x70 | (crc8 & 0xF);
            for (int j = 0; j < 4; ++j)
                sendPacketAndSleep(prefixCode[j]);
        }

        private void sendSequence(int index, byte data[]) {
            byte content[] = new byte[data.length + 1];
            content[0] = (byte)(index & 0xFF);
            System.arraycopy(data, 0, content, 1, data.length);
            int crc8 = CRC8(content);
            sendPacketAndSleep(0x80 | crc8);
            sendPacketAndSleep(0x80 | index);
            for (int i = 0; i < data.length; ++i)
                sendPacketAndSleep(data[i] | 0x100);
        }


        @Override
        protected Void doInBackground(String... params) {
            try {
                mSocket = new DatagramSocket();
                mSocket.setBroadcast(true);
            } catch (Exception e) {
                e.printStackTrace();
            }


            char randomChar = (char)(new Random().nextInt(0x7F));
            String ssid = params[0];
            String password = params[1];
            int times = 5;
            while (times-- > 0) {
                sendLeadingPart();
                sendMagicCode(ssid, password);

                for (int i = 0; i < 15; ++i) {
                    sendPrefixCode(password);
                    String data = password + randomChar + ssid;
                    int index;
                    byte content[] = new byte[4];
                    for (index = 0; index < data.length() / 4; ++index) {
                        System.arraycopy(data.getBytes(), index * 4, content, 0, content.length);
                        sendSequence(index, content);
                    }

                    if (data.length() % 4 != 0) {
                        content = new byte[data.length() % 4];
                        System.arraycopy(data.getBytes(), index * 4, content, 0, content.length);
                        sendSequence(index, content);
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(Void params) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }
}
