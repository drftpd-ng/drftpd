/*
* BlowfishCBC.java version 1.00.00
*
* Code Written by k2r (k2r.contact@gmail.com)
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License as published by
* the Free Software Foundation; either version 2 of the License, or
* (at your option) any later version.
*/
package org.drftpd.plugins.sitebot.blowfish;

import javax.crypto.*;
import javax.crypto.spec.*;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * BlowfishM CBC
 *
 * @author k2r
 * Permit to crypt/decrypt string in CBC Blowfish mode
 * Tks to Mouser for his precious help.
 * Tks to Murx for correct Padding and Long key.
 *
 * Use "encrypt" with the text to encrypt
 *      -> The function will encrypt and return the text with +OK * at the beginning"
 *
 * Use "decrypt" with the text to decrypt
 *      -> The text must include the +OK * at the front"
 *
 * To Use Key > 16 char, you must update two jar files in your jre or jdk.
 *      Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 8
 *      https://www.oracle.com/technetwork/java/javase/downloads/jce-all-download-5170447.html
 * Update the two files in jre\lib\security
 *      -> local_policy.jar
 *      -> US_export_policy.jar
 */
public class BlowfishCBC extends Blowfish {

    /**
     * Default charset for encoding
     */
    private static final String ENCODED_CHARSET = "ISO_8859_1";

    /**
     * Initial Vector
     */
    private final byte[] INIT_IV = {0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

    /**
     * CBC irc prefix
     */
    private static final String CBC_PREFIX = "+OK *";

    public BlowfishCBC(String key) {
        super(key, "Blowfish/CBC/NoPadding");
    }

    public String decrypt(String textToDecrypt) throws Exception {
        if (textToDecrypt.startsWith(CBC_PREFIX)) {
            textToDecrypt = textToDecrypt.substring(CBC_PREFIX.length(), textToDecrypt.length());
        } else {
            //Not correct encrypted string, return the source string
            return textToDecrypt;
        }

        //1- decrypt with BASE64 decoder
        byte[] base64Decoded = Base64.getDecoder().decode(textToDecrypt);

        //2- decrypt the string
        IvParameterSpec oIV = new IvParameterSpec(INIT_IV);
        getCipher().init(Cipher.DECRYPT_MODE, getSecretKeySpec(), oIV);
        byte[] lDecoded = getCipher().doFinal(base64Decoded);

        //3- Find the last 0x0 value in the decrypted string
        int lFinalIndex = findLast0byte(lDecoded) - 8;
        if (lFinalIndex <= 0) {
            lFinalIndex = lDecoded.length - 8;
        }

        //4- Delete the first 8 byte (IV) and the 0x0 value
        byte[] lFinalDecoded = new byte[lFinalIndex];
        System.arraycopy(lDecoded, 8, lFinalDecoded, 0, lFinalIndex);

        //5- Return the formatted String
        return new String(lFinalDecoded, ENCODED_CHARSET);
    }

    public String encrypt(String rawString) throws Exception {
        //1- Correct the padding
        byte[] rawStringBytes = rawString.getBytes();
        byte[] lToDecrypt = rawStringBytes.length % 8 != 0 ? correctPadding(rawStringBytes) : rawStringBytes;

        //2- Load the vector (IV) in front of the string
        byte[] lFinalToDecrypt = new byte[lToDecrypt.length + 8];
        System.arraycopy(INIT_IV, 0, lFinalToDecrypt, 0, INIT_IV.length);
        System.arraycopy(lToDecrypt, 0, lFinalToDecrypt, 8, lToDecrypt.length);

        //3- Generate a vector (IV) and crypt the string
        IvParameterSpec oIV = generateIV();
        getCipher().init(Cipher.ENCRYPT_MODE, getSecretKeySpec(), oIV);
        byte[] lEncoded = getCipher().doFinal(lFinalToDecrypt);

        //4- Encode with BASE64 encoder
        String base64Encoded = Base64.getEncoder().encodeToString(lEncoded);

        //5- Return the result
        return CBC_PREFIX.concat(base64Encoded);
    }

    /**
     * Vector generator
     *
     * @return IvParameterSpec
     */
    private IvParameterSpec generateIV() {
        byte[] b = new byte[8];
        SecureRandom sr = new SecureRandom();
        sr.nextBytes(b);
        return new IvParameterSpec(b);
    }

    /**
     * Function for correction of padding
     *
     * @param data the data to fixed
     * @return byte[]
     */
    private byte[] correctPadding(byte[] data) {
        int lSize = data.length;
        int lIndex = 8 - (data.length % 8);
        byte[] buff = new byte[lSize + lIndex];
        System.arraycopy(data, 0, buff, 0, lSize);
        for (int i = lSize; i < lSize + lIndex; i++) {
            buff[i] = 0x0;
        }
        return buff;
    }

    /**
     * Function that return the last index of 0x0 value in tab byte
     *
     * @param data the data to search
     * @return int
     */
    private int findLast0byte(byte[] data) {
        int lIndex = 0;
        for (int i = 0; i < data.length; i++) {
            if (i > 8 && data[i] == 0x0) {
                lIndex = i;
                break;
            }
        }
        return lIndex;
    }
}
