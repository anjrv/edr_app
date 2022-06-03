package com.example.storage.utils;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPOutputStream;

/**
 * Simple container class for GZip utils
 */
public class ZipUtils {

    /**
     * Converts String to compressed byte array using UTF-8 charsets
     *
     * @param str the String to compress
     * @return the compressed byte array
     */
    public static byte[] compress(String str) {
        if (str == null || str.length() == 0) {
            return null;
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        GZIPOutputStream gzip;

        try {
            gzip = new GZIPOutputStream(out);
            gzip.write(str.getBytes(StandardCharsets.UTF_8));
            gzip.close();
        } catch ( Exception e) {
            e.printStackTrace();
        }

        return out.toByteArray();
    }
}
