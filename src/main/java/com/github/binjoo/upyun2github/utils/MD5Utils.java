package com.github.binjoo.upyun2github.utils;

import org.apache.commons.codec.binary.Hex;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author liangj
 * @version 1.0
 * @date 2024-03-26 15:21
 */
public class MD5Utils {
    public static String encrypt(String str) {
        try {
            byte[] msg = str.getBytes();
            byte[] hash = null;
            MessageDigest md = MessageDigest.getInstance("MD5");
            hash = md.digest(msg);
            StringBuilder strBuilder = new StringBuilder();
            for (byte b : hash) {
                strBuilder.append(String.format("%02x", b));
            }
            return strBuilder.toString();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return "";
    }

    public static String encrypt(File file) {
        FileInputStream fileInputStream = null;
        try {
            MessageDigest MD5 = MessageDigest.getInstance("MD5");
            fileInputStream = new FileInputStream(file);
            byte[] buffer = new byte[8192];
            int length;
            while ((length = fileInputStream.read(buffer)) != -1) {
                MD5.update(buffer, 0, length);
            }
            return new String(Hex.encodeHex(MD5.digest()));
        } catch (Exception e) {
            e.printStackTrace();
            return "";
        } finally {
            try {
                if (fileInputStream != null) {
                    fileInputStream.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
