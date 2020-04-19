/*
 * Important information :
 * To Use Key > 16 char, you must update two jar files in your jre or jdk.
 * 		Java Cryptography Extension (JCE)
 * 		Unlimited Strength Jurisdiction Policy Files 1.4.2
 * 		http://java.sun.com/j2se/1.4.2/download.html#docs
 * Update the two files in jre\lib\security
 * 		-> local_policy.jar
 * 		-> US_export_policy.jar
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 */
package org.drftpd.master.sitebot.blowfish;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

/**
 * Blowfish abstract class
 *
 * @author k2r
 */
public abstract class Blowfish {


    /**
     * secretKeySpec for crypto
     */
    private final SecretKeySpec secretKeySpec;
    private final String mode;

    /**
     * Constructor
     *
     * @param key  the blowfish key
     * @param mode the blowfish encryption mode
     */
    protected Blowfish(String key, String mode) {
        secretKeySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
        this.mode = mode;
    }

    /**
     * Decrypt the string
     *
     * @param encryptedString the string to decrypt
     * @return String the decrypted string
     * @throws Exception if any error occurred
     */
    public abstract String decrypt(String encryptedString) throws Exception;

    /**
     * Encrypt the string
     *
     * @param rawString the string to encrypt
     * @return String the encrypted string
     * @throws Exception if any error occurred
     */
    public abstract String encrypt(String rawString) throws Exception;

    /**
     * secretKeySpec getter
     *
     * @return SecretKeySpec
     */
    SecretKeySpec getSecretKeySpec() {
        return secretKeySpec;
    }

    /**
     * cipher getter
     *
     * @return Cipher
     */
    Cipher initCipher() {
        try {
            return Cipher.getInstance(mode);
        } catch (Exception e) {
            throw new RuntimeException("Blowfish initialization error", e);
        }
    }
}
