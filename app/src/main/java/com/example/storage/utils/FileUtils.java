package com.example.storage.utils;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class FileUtils {
    public static String[] list(Context c) {
       String[] res = c.getFilesDir().list();
       if (res != null && res.length > 0)
           System.out.println(res[res.length - 1]);

       return res;
    }

    public static void write(String name, byte[] data, Context c) {
        File file = new File(c.getFilesDir(), name);

        try {
            FileOutputStream fos = new FileOutputStream(file);
            fos.write(data);
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static byte[] retrieve(String name, Context c) {
        try {
            FileInputStream fis = c.openFileInput(name);
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            int b = fis.read();
            while (b != -1) {
                out.write(b);
                b = fis.read();
            }

            fis.close();
            return out.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return new byte[] {};
    }

    public static void delete(String name, Context c) {
        c.deleteFile(name);
    }
}
