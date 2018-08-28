package com.kingsly.mtpdemo.utils;

/**
 * Borrowed from old impl
 */
public class ByteUtil {

    public static int byte2int16Be(byte[] bytes) {
        int addr = (bytes[0] & 0xFF);
        addr = (addr << 8) + (bytes[1] & 0xFF);
        return addr;
    }

    public static int byte2int16Le(byte[] bytes, int offset) {
        int number = bytes[offset + 1] & 0xFF;
        number = (number << 8) + (bytes[offset] & 0xFF);
        return number;
    }

    public static int byte2int16Be(byte[] bytes, int offset) {
        int number = bytes[offset] & 0xFF;
        number = (number << 8) + (bytes[offset + 1] & 0xFF);
        return number;
    }

    public static int bytes2int32Be(byte[] bytes) {
        int addr = 0;
        addr = bytes[0] & 0xFF;
        addr = (addr << 8) + (bytes[1] & 0xFF);
        addr = (addr << 8) + (bytes[2] & 0xFF);
        addr = (addr << 8) + (bytes[3] & 0xFF);
        return addr;
    }

    public static int bytes2int32Be(byte[] bytes, int offset) {
        int number = 0;
        number = bytes[offset + 0] & 0xFF;
        number = (number << 8) + (bytes[offset + 1] & 0xFF);
        number = (number << 8) + (bytes[offset + 2] & 0xFF);
        number = (number << 8) + (bytes[offset + 3] & 0xFF);
        return number;
    }

    public static int bytes2int32Le(byte[] bytes) {
        int addr = 0;
        addr = bytes[3] & 0xFF;
        addr = (addr << 8) + (bytes[2] & 0xFF);
        addr = (addr << 8) + (bytes[1] & 0xFF);
        addr = (addr << 8) + (bytes[0] & 0xFF);
        return addr;
    }

    public static int bytes2int32Le(byte[] bytes, int offset) {
        int addr = 0;
        addr = bytes[offset + 3] & 0xFF;
        addr = (addr << 8) + (bytes[offset + 2] & 0xFF);
        addr = (addr << 8) + (bytes[offset + 1] & 0xFF);
        addr = (addr << 8) + (bytes[offset + 0] & 0xFF);
        return addr;
    }

    public static void int2BytesLe(int v, byte[] bytes, int offset) {
        bytes[offset + 0] = (byte) (v & 0xFF);
        bytes[offset + 1] = (byte) ((v >> 8) & 0xFF);
        bytes[offset + 2] = (byte) ((v >> 16) & 0xFF);
        bytes[offset + 3] = (byte) ((v >> 24) & 0xFF);
    }

    public static void int2BytesBe(int v, byte[] bytes, int offset) {
        bytes[offset + 3] = (byte) (v & 0xFF);
        bytes[offset + 2] = (byte) ((v >> 8) & 0xFF);
        bytes[offset + 1] = (byte) ((v >> 16) & 0xFF);
        bytes[offset] = (byte) ((v >> 24) & 0xFF);
    }

    public static void short2BytesLe(int v, byte[] bytes, int offset) {
        bytes[offset + 0] = (byte) (v & 0xFF);
        bytes[offset + 1] = (byte) ((v & 0xFF00) >> 8);
    }

    public static void short2BytesBe(int v, byte[] bytes, int offset) {
        bytes[offset + 0] = (byte) ((v & 0xFF00) >> 8);
        bytes[offset + 1] = (byte) (v & 0xFF);
    }
}
