/*
* BlowfishECB.java version 2.00.00
*
* Code Written by k2r (k2r.contact@gmail.com)
*
* Tks to Mouser for his precious help.
* Tks to Murx for correct Padding and Long key.
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*/
package org.drftpd.plugins.sitebot.blowfish;

import javax.crypto.*;
import java.io.*;

/**
 * Blowfish ECB
 *
 * @author k2r
 * Permit to crypt/decrypt string in ECB Blowfish mode
 */
public class BlowfishECB extends Blowfish {

    /**
     * Default charset for encoding
     */
    private static final String ENCODED_CHARSET = "ISO_8859_1";

    /**
     * Custom Base64 string
     */
    private static final String B64 = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * ECB standard prefix
     */
    private static final String ECB_STANDARD_PREFIX = "+OK ";

    /**
     * ECB MCPS prefix
     */
    private static final String ECB_MCPS_PREFIX = "mcps ";

    public BlowfishECB(String key) {
        super(key, "Blowfish/ECB/NoPadding");
    }

    public String encrypt(String textToEncrypt) throws Exception {
        // Mode cypher in encrypt mode
        getCipher().init(Cipher.ENCRYPT_MODE, getSecretKeySpec());

        byte[] BEncrypt = textToEncrypt.getBytes();
        int encryptSize = BEncrypt.length;
        int Limit = 8 - (BEncrypt.length % 8);
        byte[] buff = new byte[encryptSize + Limit];

        System.arraycopy(BEncrypt, 0, buff, 0, encryptSize);
        for (int i = encryptSize; i < encryptSize + Limit; i++) {
            buff[i] = 0x0;
        }

        // encrypt the padding string
        byte[] encrypted = getCipher().doFinal(buff);
        // B64 custom encryption
        String REncrypt = byteToB64(encrypted);
        REncrypt = ECB_STANDARD_PREFIX.concat(REncrypt);
        return REncrypt;
    }

    public String decrypt(String textToDecrypt) throws Exception {
        if (textToDecrypt.startsWith(ECB_STANDARD_PREFIX)) {
            textToDecrypt = textToDecrypt.substring(ECB_STANDARD_PREFIX.length(), textToDecrypt.length());
        } else if (textToDecrypt.startsWith(ECB_MCPS_PREFIX)) {
            textToDecrypt = textToDecrypt.substring(ECB_MCPS_PREFIX.length(), textToDecrypt.length());
        } else {
            //Not correct encrypted string, return the source string
            return textToDecrypt;
        }

        // B64 custom decrypt
        byte[] Again = B64ToByte(textToDecrypt);
        // Mode cypher in decrypt mode
        getCipher().init(Cipher.DECRYPT_MODE, getSecretKeySpec());
        byte[] decrypted = getCipher().doFinal(Again);

        // Get exact length
        int length = 0;
        while (length < decrypted.length && decrypted[length] != 0x0) {
            length++;
        }
        byte[] Final = new byte[length];
        // Format & Limit the Result String
        System.arraycopy(decrypted, 0, Final, 0, length);
        //Force again the encoding result string
        return new String(Final, ENCODED_CHARSET);
    }

    /**
     * Base64 to byte function
     *
     * @param data the data to encode
     * @return byte[]
     * @throws UnsupportedEncodingException if default charset is not supported
     */
    @SuppressWarnings("Duplicates")
    private byte[] B64ToByte(String data) throws UnsupportedEncodingException {
        StringBuilder builder = new StringBuilder();
        int k = -1;
        while (k < (data.length() - 1)) {

            int right = 0;
            int left = 0;
            int v;
            int w;
            int z;

            for (int i = 0; i < 6; i++) {
                k++;
                v = B64.indexOf(data.charAt(k));
                right |= v << (i * 6);
            }

            for (int i = 0; i < 6; i++) {
                k++;
                v = B64.indexOf(data.charAt(k));
                left |= v << (i * 6);
            }

            for (int i = 0; i < 4; i++) {
                w = ((left & (0xFF << ((3 - i) * 8))));
                z = w >> ((3 - i) * 8);
                if (z < 0) {
                    z = z + 256;
                }
                builder.append((char) z);
            }

            for (int i = 0; i < 4; i++) {
                w = ((right & (0xFF << ((3 - i) * 8))));
                z = w >> ((3 - i) * 8);
                if (z < 0) {
                    z = z + 256;
                }
                builder.append((char) z);
            }
        }
        // Force the encoding result string
        return builder.toString().getBytes(ENCODED_CHARSET);
    }

    /**
     * byte to Base64 custom function
     *
     * @param data the data to decode
     * @return String
     */
    private String byteToB64(byte[] data) {
        StringBuilder builder = new StringBuilder();

        int left;
        int right;
        int k = -1;
        int v;

        while (k < (data.length - 1)) {
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            left = v << 24;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            left += v << 16;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            left += v << 8;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            left += v;

            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            right = v << 24;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            right += v << 16;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            right += v << 8;
            k++;
            v = data[k];
            if (v < 0) {
                v += 256;
            }
            right += v;

            for (int i = 0; i < 6; i++) {
                builder.append(B64.charAt(right & 0x3F));
                right = right >> 6;
            }

            for (int i = 0; i < 6; i++) {
                builder.append(B64.charAt(left & 0x3F));
                left = left >> 6;
            }
        }
        return builder.toString();
    }
}
