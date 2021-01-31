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
package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * @author djb61
 * @version $Id$
 */
public class DH1080 {

    private static final Logger logger = LogManager.getLogger(DH1080.class);

    private static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

    private static final int[] IA = new int[256];

    // DH1080 'p' value
    private static final String PRIME = "++ECLiPSE+is+proud+to+present+latest+FiSH+release+featuring+even+more+security+for+you+++shouts+go+out+to+TMG+for+helping+to+generate+this+cool+sophie+germain+prime+number++++/C32L";

    // DH1080 'g' value
    private static final String GENERATOR = "2";

    private static final int KEY_BYTE_LENGTH = 135;

    static {
        Arrays.fill(IA, -1);
        for (int i = 0; i < CA.length; i++) {
            IA[CA[i]] = i;
        }
    }

    private BigInteger _privateInt;

    private BigInteger _publicInt;

    public DH1080() {
        try {
            // We do not need to reseed as this is only used during handshake
            SecureRandom sRNG = SecureRandom.getInstance("SHA1PRNG", "SUN");
            // we clear bit position '0' to mimic what fish for mIRC does
            // We clear bit 1079 and set 1078 to ensure 'strong' private key
            _privateInt = new BigInteger(1080, sRNG).clearBit(0).clearBit(1079).setBit(1078);
            BigInteger primeInt = new BigInteger(1, decodeB64(PRIME));
            _publicInt = new BigInteger(GENERATOR).modPow(_privateInt, primeInt);
        } catch (NoSuchAlgorithmException | NoSuchProviderException e) {
            logger.warn("Algorithm and/or Provider for DH1080 random number generator not available", e);
        }
    }

    /**
     * This is an alternate base64 decoder, this is required as
     * the DH1080 implementation used by everyone relies on a non
     * RFC compliant base64 implementation, therefore the other
     * base64 methods in the blowfish class cannot be used. This method
     * implements the broken version used by DH1080 and should
     * not be used for anything else.
     */
    private static byte[] decodeB64(String input) {
        byte[] dArr = new byte[input.length() * 6 >> 3];

        for (int i = 0, z = 0; z < dArr.length; ) {
            dArr[z++] = (byte) ((IA[input.charAt(i)] << 2) | (IA[input.charAt(i + 1)] >> 4));
            i++;
            if (z >= dArr.length) {
                break;
            }
            dArr[z++] = (byte) ((IA[input.charAt(i)] << 4) | (IA[input.charAt(i + 1)] >> 2));
            i++;
            if (z >= dArr.length) {
                break;
            }
            dArr[z++] = (byte) ((IA[input.charAt(i)] << 6) | IA[input.charAt(i + 1)]);
            i += 2;
        }
        return dArr;
    }

    /**
     * This is an alternate base64 encoder, this is required as
     * the DH1080 implementation used by everyone relies on a non
     * RFC compliant base64 implementation, therefore the other
     * base64 methods in the blowfish class cannot be used. This method
     * implements the broken version used by DH1080 and should
     * not be used for anything else.
     */
    private static String encodeB64(byte[] input) {
        int i;
        int p = 0;
        char m, t;
        char[] dArr = new char[((input.length / 3 + 1) << 2) - (3 - input.length % 3)];

        m = 0x80;
        for (i = 0, t = 0; i < (input.length << 3); i++) {
            if ((input[(i >> 3)] & m) != 0) {
                t |= 1;
            }
            if ((m >>= 1) == 0) {
                m = 0x80;
            }
            if (((i + 1) % 6) == 0) {
                dArr[p++] = CA[t];
                t &= 0;
            }

            t <<= 1;
        }
        m = (char) (5 - (i % 6));
        t <<= m;
        if (m != 0) {
            dArr[p] = CA[t];
        }
        return new String(dArr);
    }

    /**
     *
     * @return The string representing the (bytes) public key, and will always be 135 bytes
     */
    public String getPublicKey() {
        return encodeB64(getBytes(_publicInt));
    }

    public boolean validatePublicKey(String peerPublicKey) {
        byte[] peerPublicKeyDecoded = decodeB64(peerPublicKey);
        if (peerPublicKeyDecoded.length != KEY_BYTE_LENGTH) {
            logger.warn("Received a peer DH1080 public key that does not conform to the correct specifications, received {} bytes", peerPublicKeyDecoded.length);
            return false;
        }
        BigInteger peerPublicKeyInt = new BigInteger(1, peerPublicKeyDecoded);
        if (peerPublicKeyInt.bitCount() <= 1) {
            logger.warn("Received a peer DH1080 public key that does not conform to the correct specifications, need at least 2 bits set");
            return false;
        }
        BigInteger primeInt = new BigInteger(1, decodeB64(PRIME));
        if (peerPublicKeyInt.compareTo(BigInteger.TWO) < 0 || peerPublicKeyInt.compareTo(primeInt.subtract(BigInteger.ONE)) >= 0) {
            logger.warn("Received a peer DH1080 public key that does not conform to the correct specifications, out of bounds '2 < (public key) < PRIME'");
            return false;
        }
        return true;
    }

    public String getSharedSecret(String peerPublicKey) {
        if (!validatePublicKey(peerPublicKey)) {
            return null;
        }
        BigInteger peerPublicKeyInt = new BigInteger(1, decodeB64(peerPublicKey));
        BigInteger primeInt = new BigInteger(1, decodeB64(PRIME));
        BigInteger shareInt = peerPublicKeyInt.modPow(_privateInt, primeInt);
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashed = md.digest(getBytes(shareInt));
            return encodeB64(hashed);
        } catch (NoSuchAlgorithmException e) {
            logger.debug("Algorithm for DH1080 shared secret hashing not available", e);
        }
        return null;
    }

    /**
     * By default BigInteger.toByteArray() returns bytes including a sign
     * bit, since our numbers are always positive this bit is always zero
     * so not too much of a problem. However if the number is an exact
     * multiple of 8 bits then the addition of a sign bit will cause an
     * extra byte to be added to the resulting array. This will cause problems
     * so in this case we strip the first byte off the array before returning.
     */
    private byte[] getBytes(BigInteger big) {
        byte[] bigBytes = big.toByteArray();
        // Fix sign mis interpretation here (adds an extra byte)
        if ((big.bitLength() % 8) == 0) {
            byte[] smallerBytes = new byte[big.bitLength() / 8];
            System.arraycopy(bigBytes, 1, smallerBytes, 0, smallerBytes.length);
            bigBytes = smallerBytes;
        }
        // bigInteger strips leading bytes that represent '0', which we not wish... so we add them here.
        if (bigBytes.length != KEY_BYTE_LENGTH) {
            int missing = KEY_BYTE_LENGTH - bigBytes.length;
            logger.debug("Restoring leading '0' byte bytes, we need to add {} extra '0' byte bytes to get to {}", missing, KEY_BYTE_LENGTH);
            byte[] missingBytes = new byte[KEY_BYTE_LENGTH];
            System.arraycopy(bigBytes, 0, missingBytes, missing, bigBytes.length);
            bigBytes = missingBytes;
        }
        return bigBytes;
    }
}
