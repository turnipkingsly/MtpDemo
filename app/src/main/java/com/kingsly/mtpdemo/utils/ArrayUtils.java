package com.kingsly.mtpdemo.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;

public class ArrayUtils {

    public static Object[] splitAry(int[] ary, int count) {

        int subSize = ary.length % count == 0 ? ary.length / count : ary.length / count + 1;

        List<List<Integer>> subAryList = new ArrayList<>();

        for (int i = 0; i < count; i++) {
            int index = i * subSize;

            List<Integer> list = new ArrayList<>();
            int j = 0;
            while (j < subSize && index < ary.length) {
                list.add(ary[index++]);
                j++;
            }
            subAryList.add(list);
        }

        Object[] subAry = new Object[subAryList.size()];

        for (int i = 0; i < subAryList.size(); i++) {
            List<Integer> subList = subAryList.get(i);

            int[] subAryItem = new int[subList.size()];
            for (int j = 0; j < subList.size(); j++) {
                subAryItem[j] = subList.get(j).intValue();
            }
            subAry[i] = subAryItem;
        }
        return subAry;
    }

    public static void arrayToQueue(BlockingQueue<Integer> queue, int[] array) {
        for (int anArray : array) {
            queue.offer(anArray);
        }
    }

    public static Object bytesToObject(byte[] bytes) throws Exception {
        //byte转object
        ByteArrayInputStream in = new ByteArrayInputStream(bytes);
        ObjectInputStream sIn = new ObjectInputStream(in);
        return sIn.readObject();
    }


    public static byte[] objectToBytes(Object obj) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ObjectOutputStream sOut = null;
        try {
            sOut = new ObjectOutputStream(out);
            sOut.writeObject(obj);
            sOut.flush();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (sOut != null) {
                try {
                    sOut.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return out.toByteArray();
    }

    public static int byteArrayToInt(byte[] ary) {
        int value;
        value = (int) ((ary[0] & 0xFF)
                | ((ary[1] << 8) & 0xFF00)
                | ((ary[2] << 16) & 0xFF0000)
                | ((ary[3] << 24) & 0xFF000000));
        return value;

    }

    public static byte[] intToByteArray(int value) {
        byte[] byte_src = new byte[4];
        byte_src[3] = (byte) ((value & 0xFF000000) >> 24);
        byte_src[2] = (byte) ((value & 0x00FF0000) >> 16);
        byte_src[1] = (byte) ((value & 0x0000FF00) >> 8);
        byte_src[0] = (byte) ((value & 0x000000FF));
        return byte_src;

    }
}
