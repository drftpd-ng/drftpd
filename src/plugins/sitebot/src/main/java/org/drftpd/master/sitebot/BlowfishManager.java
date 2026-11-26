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
import org.drftpd.master.sitebot.blowfish.Blowfish;
import org.drftpd.master.sitebot.blowfish.BlowfishCBC;
import org.drftpd.master.sitebot.blowfish.BlowfishECB;

class BlowfishManager {

    static final String CBC = "cbc";
    static final String ECB = "ecb";
    private static final Logger logger = LogManager.getLogger(Blowfish.class);
    private static final String PREFIX_OK = "+OK ";
    private static final String PREFIX_MCPS = "mcps ";
    private static final String CBC_MARKER = "*";
    private final BlowfishECB _ecb;
    private final BlowfishCBC _cbc;
    private final boolean _cbcMode;
    private final boolean _ecbFallback;

    /*
     * Constructor of BlowfishManager class Key param
     */
    BlowfishManager(String key, String mode) {
        this(key, mode, false);
    }

    /**
     * @param key         the blowfish key
     * @param mode        the configured mode, used for outgoing lines
     * @param ecbFallback accept incoming ECB lines when the configured mode is CBC
     */
    BlowfishManager(String key, String mode, boolean ecbFallback) {
        _cbcMode = CBC.equals(mode.toLowerCase());
        _ecbFallback = ecbFallback;
        _ecb = new BlowfishECB(key);
        _cbc = new BlowfishCBC(key);
    }

    boolean isCBC() {
        return _cbcMode;
    }

    /**
     * Largest number of plaintext bytes whose encrypted line fits in the given
     * number of characters, for the configured mode.
     * CBC: "+OK *" + standard base64 of an 8 byte random block and the text padded to 8 bytes.
     * ECB: "+OK " + 12 characters for each 8 byte block of the padded text.
     *
     * @param lineBudget the room available for the encrypted line
     * @return the plaintext budget in bytes, 0 if nothing fits
     */
    int maxPlaintextBytes(int lineBudget) {
        if (_cbcMode) {
            int chars = lineBudget - PREFIX_OK.length() - CBC_MARKER.length();
            if (chars <= 0) {
                return 0;
            }
            int blocks = ((chars / 4) * 3) / 8;
            return Math.max(0, (blocks - 1) * 8);
        }
        int chars = lineBudget - PREFIX_OK.length();
        if (chars <= 0) {
            return 0;
        }
        return (chars / 12) * 8;
    }

    /**
     * Encrypt a line with the configured mode
     *
     * @param toEncrypt the text to encrypt
     * @return the encrypted line, or null if encryption failed and nothing must be sent
     */
    String encrypt(String toEncrypt) {
        try {
            return _cbcMode ? _cbc.encrypt(toEncrypt) : _ecb.encrypt(toEncrypt);
        } catch (Exception e) {
            logger.error("Error encrypting", e);
            return null;
        }
    }

    /**
     * Decrypt a line the way FiSH does: "+OK *" (or "mcps *") is CBC, "+OK " or
     * "mcps " without the "*" is ECB, both with the same key, whatever the
     * configured mode is. ECB lines on a CBC key are only accepted when the
     * fallback is enabled.
     *
     * @param toDecrypt the line as received
     * @return the decrypted text, or null if the line is not encrypted, could not be
     * decrypted or must be ignored
     */
    String decrypt(String toDecrypt) {
        String payload;
        if (toDecrypt.startsWith(PREFIX_OK)) {
            payload = toDecrypt.substring(PREFIX_OK.length());
        } else if (toDecrypt.startsWith(PREFIX_MCPS)) {
            payload = toDecrypt.substring(PREFIX_MCPS.length());
        } else {
            return null;
        }
        // Text after the first space is not encrypted (timestamps added by a BNC);
        // FiSH does not decrypt it either, the bot drops it
        int space = payload.indexOf(' ');
        if (space >= 0) {
            logger.debug("Ignoring unencrypted text after the encrypted line");
            payload = payload.substring(0, space);
        }
        try {
            if (payload.startsWith(CBC_MARKER)) {
                return _cbc.decryptPayload(payload.substring(CBC_MARKER.length()));
            }
            if (_cbcMode && !_ecbFallback) {
                logger.debug("Ignoring ECB encrypted line for a CBC key (blowfish.ecb.fallback is disabled)");
                return null;
            }
            return _ecb.decryptPayload(payload);
        } catch (Exception e) {
            logger.warn("Could not decrypt line: {}", e.getMessage());
            logger.debug("Decryption error", e);
            return null;
        }
    }
}
