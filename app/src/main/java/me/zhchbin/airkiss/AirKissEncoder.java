package me.zhchbin.airkiss;

import java.util.Arrays;
import java.util.Random;

public class AirKissEncoder {
    private int mEncodedData[] = new int[2 << 14];
    private int mLength = 0;

    // Random char should be in range [0, 127).
    private char mRandomChar = (char)(new Random().nextInt(0x7F));

    public AirKissEncoder(String ssid, String password) {
        int times = 5;
        while (times-- > 0) {
            leadingPart();
            magicCode(ssid, password);

            for (int i = 0; i < 15; ++i) {
                prefixCode(password);
                String data = password + mRandomChar + ssid;
                int index;
                byte content[] = new byte[4];
                for (index = 0; index < data.length() / 4; ++index) {
                    System.arraycopy(data.getBytes(), index * 4, content, 0, content.length);
                    sequence(index, content);
                }

                if (data.length() % 4 != 0) {
                    content = new byte[data.length() % 4];
                    System.arraycopy(data.getBytes(), index * 4, content, 0, content.length);
                    sequence(index, content);
                }
            }
        }
    }

    public int[] getEncodedData() {
        return Arrays.copyOf(mEncodedData, mLength);
    }

    public char getRandomChar() {
        return mRandomChar;
    }

    private void appendEncodedData(int length) {
        mEncodedData[mLength++] = length;
    }

    private int CRC8(byte data[]) {
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
        return (crc & 0xFF);
    }

    private int CRC8(String stringData) {
        return CRC8(stringData.getBytes());
    }

    private void leadingPart() {
        for (int i = 0; i < 50; ++i) {
            for (int j = 1; j <= 4; ++j)
                appendEncodedData(j);
        }
    }

    private void magicCode(String ssid, String password) {
        int length = ssid.length() + password.length() + 1;
        int magicCode[] = new int[4];
        magicCode[0] = 0x00 | (length >>> 4 & 0xF);
        if (magicCode[0] == 0)
            magicCode[0] = 0x08;
        magicCode[1] = 0x10 | (length & 0xF);
        int crc8 = CRC8(ssid);
        magicCode[2] = 0x20 | (crc8 >>> 4 & 0xF);
        magicCode[3] = 0x30 | (crc8 & 0xF);
        for (int i = 0; i < 20; ++i) {
            for (int j = 0; j < 4; ++j)
                appendEncodedData(magicCode[j]);
        }
    }

    private void prefixCode(String password) {
        int length = password.length();
        int prefixCode[] = new int[4];
        prefixCode[0] = 0x40 | (length >>> 4 & 0xF);
        prefixCode[1] = 0x50 | (length & 0xF);
        int crc8 = CRC8(new byte[] {(byte)length});
        prefixCode[2] = 0x60 | (crc8 >>> 4 & 0xF);
        prefixCode[3] = 0x70 | (crc8 & 0xF);
        for (int j = 0; j < 4; ++j)
            appendEncodedData(prefixCode[j]);
    }

    private void sequence(int index, byte data[]) {
        byte content[] = new byte[data.length + 1];
        content[0] = (byte)(index & 0xFF);
        System.arraycopy(data, 0, content, 1, data.length);
        int crc8 = CRC8(content);
        appendEncodedData(0x80 | crc8);
        appendEncodedData(0x80 | index);
        for (byte aData : data)
            appendEncodedData(aData | 0x100);
    }
}
