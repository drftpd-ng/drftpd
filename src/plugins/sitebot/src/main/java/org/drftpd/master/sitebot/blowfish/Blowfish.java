/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

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
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 */
package org.drftpd.master.sitebot.blowfish;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Blowfish abstract class
 *
 * @author k2r
 */
public abstract class Blowfish {

    static final Logger logger = LogManager.getLogger(Blowfish.class);

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
        // FiSH uses the key bytes as stored in blow.ini, which modern mIRC writes as UTF-8
        secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "Blowfish");
        this.mode = mode;
    }

    /**
     * Decrypt the payload of an encrypted line
     *
     * @param payload the encrypted text without the "+OK " / "mcps " prefix
     *                (and without the "*" CBC marker)
     * @return the decrypted text, flagged when the line was cut
     * @throws Exception if any error occurred
     */
    public abstract DecryptedText decryptPayload(String payload) throws Exception;

    /**
     * Encrypt the string
     *
     * @param rawString the string to encrypt
     * @return String the encrypted string
     * @throws Exception if any error occurred
     */
    public abstract String encrypt(String rawString) throws Exception;

    /**
     * Turn decrypted bytes into text. FiSH and mIRC send UTF-8, but older clients
     * may still send legacy 8-bit text, so fall back to ISO-8859-1 when the bytes
     * are not valid UTF-8.
     *
     * @param data the decrypted plaintext bytes (without padding)
     * @param cut  true if the line was cut, a multibyte UTF-8 character may then
     *             have lost its last bytes and is dropped
     * @return String the decoded text
     */
    static String decodeText(byte[] data, boolean cut) {
        if (cut) {
            data = dropIncompleteUtf8Tail(data);
        }
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(data, StandardCharsets.ISO_8859_1);
        }
    }

    /**
     * Remove a UTF-8 sequence that was started but not finished at the end of the data.
     */
    private static byte[] dropIncompleteUtf8Tail(byte[] data) {
        for (int back = 1; back <= 3 && back <= data.length; back++) {
            int b = data[data.length - back] & 0xFF;
            if ((b & 0xC0) == 0x80) {
                // continuation byte, keep looking for the lead byte
                continue;
            }
            int needed = (b & 0xE0) == 0xC0 ? 2 : (b & 0xF0) == 0xE0 ? 3 : (b & 0xF8) == 0xF0 ? 4 : 1;
            if (needed > back) {
                return Arrays.copyOf(data, data.length - back);
            }
            break;
        }
        return data;
    }

    /**
     * Plaintext up to the first 0x0 byte (the padding)
     */
    static byte[] untilFirstZero(byte[] data, int from) {
        int end = from;
        while (end < data.length && data[end] != 0x0) {
            end++;
        }
        return Arrays.copyOfRange(data, from, end);
    }

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
