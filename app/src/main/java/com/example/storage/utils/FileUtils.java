package com.example.storage.utils;

import android.annotation.SuppressLint;
import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;

public class FileUtils {
    public static final String ISO_DATE = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";

    /**
     * Obtain all the filenames for the internally stored files
     *
     * @param c the context to list files for
     * @return an array of strings containing the names of existing files
     */
    @SuppressLint("SimpleDateFormat")
    public static ArrayList<String> list(Context c) {
       String[] files = c.getFilesDir().list();
       ArrayList<String> res = new ArrayList<>();

       if (files != null) {
           for (String file : files) {
               try {
                   DateFormat isoDate = new SimpleDateFormat(FileUtils.ISO_DATE);
                   isoDate.parse(file); // Ensure the list we get back only has our backlog
                   res.add(file);
               } catch (Exception e) {
                   // Unused
               }
           }
       }

       return res;
    }

    /**
     * Writes a file to the internal storage of the app
     *
     * @param name The name of the file to write
     * @param data The data to write to the file
     * @param c The context to be used
     */
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

    /**
     * Obtains the stored data of a given internal storage file
     *
     * @param name The name of the file to look for
     * @param c The context of the application
     * @return A byte array of data contained within the file
     */
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

        return new byte[]{};
    }

    /**
     * Deletes a file from internal storage
     *
     * @param name The name of the file to delete
     * @param c The context to delete from
     */
    public static void delete(String name, Context c) {
        c.deleteFile(name);
    }
}
