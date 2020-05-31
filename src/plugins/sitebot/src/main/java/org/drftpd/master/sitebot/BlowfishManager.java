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
    private final Blowfish blowfish;

    /*
     * Constructor of BlowfishManager class Key param
     */
    BlowfishManager(String key, String mode) {
        if (CBC.equals(mode.toLowerCase())) {
            blowfish = new BlowfishCBC(key);
        } else {
            blowfish = new BlowfishECB(key);
        }
    }

    /* encrypt function	 */
    String encrypt(String toEncrypt) {
        try {
            return blowfish.encrypt(toEncrypt);
        } catch (Exception e) {
            logger.error("Error encrypting", e);
            return ENCRYPTION_ERROR_MESSAGE;
        }
    }

    /* decrypt function */
    String decrypt(String toDecrypt) {
        try {
            return blowfish.decrypt(toDecrypt);
        } catch (Exception e) {
            logger.error("Error decrypting", e);
            return DECRYPTION_ERROR_MESSAGE;
        }
    }
}
