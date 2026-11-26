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
    private static final String ENCRYPTION_ERROR_MESSAGE = "An error occurred in encryption process. Check your logs";
    private static final String DECRYPTION_ERROR_MESSAGE = "An error occurred in decryption process. Check your logs";
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

    /* encrypt function	 */
    String encrypt(String toEncrypt) {
        try {
            return _cbcMode ? _cbc.encrypt(toEncrypt) : _ecb.encrypt(toEncrypt);
        } catch (Exception e) {
            logger.error("Error encrypting", e);
            return ENCRYPTION_ERROR_MESSAGE;
        }
    }

    /**
     * Decrypt a line the way FiSH does: "+OK *" (or "mcps *") is CBC, "+OK " or
     * "mcps " without the "*" is ECB, both with the same key, whatever the
     * configured mode is. ECB lines on a CBC key are only accepted when the
     * fallback is enabled.
     *
     * @param toDecrypt the line as received
     * @return the decrypted text, or null if the line must be ignored
     */
    String decrypt(String toDecrypt) {
        String payload;
        if (toDecrypt.startsWith(PREFIX_OK)) {
            payload = toDecrypt.substring(PREFIX_OK.length());
        } else if (toDecrypt.startsWith(PREFIX_MCPS)) {
            payload = toDecrypt.substring(PREFIX_MCPS.length());
        } else {
            //Not correct encrypted string, return the source string
            return toDecrypt;
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
            logger.error("Error decrypting", e);
            return DECRYPTION_ERROR_MESSAGE;
        }
    }
}
