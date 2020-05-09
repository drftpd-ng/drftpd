package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.sitebot.blowfish.Blowfish;
import org.drftpd.master.sitebot.blowfish.BlowfishCBC;
import org.drftpd.master.sitebot.blowfish.BlowfishECB;

import java.util.HashMap;
import java.util.Map;

class BlowfishManager {

    private static final Logger logger = LogManager.getLogger(Blowfish.class);

    static final String CBC = "cbc";
    static final String ECB = "ecb";

    private static final String ENCRYPTION_ERROR_MESSAGE = "An error occurred in encryption process. Check your logs";
    private static final String DECRYPTION_ERROR_MESSAGE = "An error occurred in decryption process. Check your logs";

    /**
     * standard prefix
     */
    private static final String STANDARD_PREFIX = "+OK ";

    /**
     * MCPS prefix
     */
    private static final String MCPS_PREFIX = "mcps ";

    // In the future more blowfish encryption are expected
    private final Map<String, Blowfish> blowfish;

    private final String encryptMode;

    /*
     * Constructor of BlowfishManager class Key param
     */
    BlowfishManager(String key, String mode) {
        encryptMode = mode.toLowerCase();
        blowfish = new HashMap<>();
        blowfish.put(CBC, new BlowfishCBC(key));
        blowfish.put(ECB, new BlowfishECB(key));
    }

    /* encrypt function	 */
    String encrypt(String toEncrypt) {
        try {
            return blowfish.get(encryptMode).encrypt(toEncrypt);
        } catch (Exception e) {
            logger.error("Error encrypting", e);
            return ENCRYPTION_ERROR_MESSAGE;
        }
    }

    /* decrypt function */
    String decrypt(String toDecrypt) {
        String line;
        if (toDecrypt.startsWith(STANDARD_PREFIX)) {
            line = toDecrypt.substring(STANDARD_PREFIX.length());
        } else if (toDecrypt.startsWith(MCPS_PREFIX)) {
            line = toDecrypt.substring(MCPS_PREFIX.length());
        } else {
            logger.warn("Incorrect start of string to decrypt");
            return DECRYPTION_ERROR_MESSAGE;
        }
        if (line.contains(STANDARD_PREFIX) || line.contains(MCPS_PREFIX)) {
            logger.warn("Found another start of an encrypted string within the same string");
            return DECRYPTION_ERROR_MESSAGE;
        }

        // We default to cbc mode
        String detectedMode = CBC;
        if (line.startsWith("*")) {
            line = line.substring(1);
        } else {
            // Fallback for compatability
            detectedMode = ECB;
        }

        // Guard that we do not introduce a security issue
        // If it is indeed ECB the decryption will fail below
        if (encryptMode.equals(CBC) && !detectedMode.equals(encryptMode)) {
            logger.warn("We detected " + detectedMode + ", but we do not allow lesser security as our encryption method is " + encryptMode);
            detectedMode = encryptMode;
        }

        try {
            return blowfish.get(detectedMode).decrypt(line);
        } catch (Exception e) {
            logger.error("Error decrypting", e);
        }
        return DECRYPTION_ERROR_MESSAGE;
    }
}
