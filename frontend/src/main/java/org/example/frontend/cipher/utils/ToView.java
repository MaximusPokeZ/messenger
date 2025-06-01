package org.example.frontend.cipher.utils;

import lombok.experimental.UtilityClass;

@UtilityClass
public class ToView {
    public static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            hexString.append(String.format("%02X ", b));
        }
        return hexString.toString().trim();
    }

    public static String intToHex(int value) {
        return String.format("%08X", value);
    }

    public static String bytesToBinary(byte[] bytes) {
        StringBuilder bString = new StringBuilder();
        for (byte b : bytes) {
            bString.append(String.format("%8s", Integer.toBinaryString(b & 0xFF)).replace(' ', '0')).append(" ");
        }
        return bString.toString().trim();
    }

    public static String formatShortToBinary(short value) {

        byte highByte = (byte) ((value >> 8) & 0xFF);
        byte lowByte = (byte) (value & 0xFF);

        String highByteStr = String.format("%8s", Integer.toBinaryString(highByte & 0xFF)).replace(' ', '0');
        String lowByteStr = String.format("%8s", Integer.toBinaryString(lowByte & 0xFF)).replace(' ', '0');

        return highByteStr + " " + lowByteStr;
    }
}
