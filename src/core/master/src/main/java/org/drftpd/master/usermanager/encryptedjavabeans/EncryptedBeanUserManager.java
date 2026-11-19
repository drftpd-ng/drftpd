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
package org.drftpd.master.usermanager.encryptedjavabeans;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.drftpd.master.usermanager.javabeans.BeanUserManager;

import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.Properties;

public class EncryptedBeanUserManager extends BeanUserManager {

    protected static final Logger logger = LogManager.getLogger(EncryptedBeanUserManager.class);

    public static final int PASSCRYPT_NONE = 0;
    public static final int PASSCRYPT_MD2 = 1;
    public static final int PASSCRYPT_MD5 = 2;
    public static final int PASSCRYPT_SHA1 = 3;
    public static final int PASSCRYPT_SHA256 = 4;
    public static final int PASSCRYPT_SHA384 = 5;
    public static final int PASSCRYPT_SHA512 = 6;
    public static final int PASSCRYPT_BCRYPT = 7;
    public static final int PASSCRYPT_GLFTPD = 8;
    public static final int PASSCRYPT_ARGON2 = 9;

    /**
     * Marker used by {@link EncryptedBeanUser} to force a re-hash with the manager's configured
     * passcrypt (see {@link EncryptedBeanUser#setPassword(String)}). It is NOT the hash used when
     * the configuration is missing or invalid; that is {@link #PASSCRYPT_FALLBACK}.
     */
    public static final int PASSCRYPT_DEFAULT = PASSCRYPT_NONE;

    /**
     * Hash used when {@code passcrypt} is missing or not recognised in encryptedbeanuser.conf.
     * A configuration mistake must never result in passwords being stored in clear text.
     */
    public static final int PASSCRYPT_FALLBACK = PASSCRYPT_BCRYPT;

    private static final int PASSCRYPT_UNKNOWN = -1;

    protected int _passcrypt = PASSCRYPT_FALLBACK;

    /*
     * Constructor to read encryption type, and subscribe to events
     */
    public EncryptedBeanUserManager() {
        AnnotationProcessor.process(this);
        readPasscrypt();
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        readPasscrypt();
    }

    /*
     * Reads the Password Encryption into memory for user
     */
    protected void readPasscrypt() {
        _passcrypt = resolvePasscrypt(ConfigLoader.loadConfig("encryptedbeanuser.conf"));
    }

    /**
     * Resolves the {@code passcrypt} setting from the given configuration, logging an error when it
     * is missing or invalid (and falling back to {@link #PASSCRYPT_FALLBACK}) and a warning when it
     * is configured to a weak scheme.
     */
    public static int resolvePasscrypt(Properties cfg) {
        String value = cfg.getProperty("passcrypt");
        int passcrypt = lookupPasscrypt(value);
        if (passcrypt == PASSCRYPT_UNKNOWN) {
            passcrypt = PASSCRYPT_FALLBACK;
            if (value == null) {
                logger.error("No 'passcrypt' set in encryptedbeanuser.conf, falling back to bcrypt");
            } else {
                logger.error("Unknown 'passcrypt' value '{}' in encryptedbeanuser.conf, falling back to bcrypt", value);
            }
        } else if (passcrypt == PASSCRYPT_NONE) {
            logger.warn("'passcrypt' is set to '{}': user passwords are stored in CLEAR TEXT. bcrypt or argon2 is strongly recommended", value.trim());
        } else if (isWeakPasscrypt(passcrypt)) {
            logger.warn("'passcrypt' is set to '{}', an unsalted fast hash that is easy to crack. bcrypt or argon2 is strongly recommended", value.trim());
        }
        return passcrypt;
    }

    /**
     * Parses a {@code passcrypt} value (case-insensitive, surrounding whitespace ignored).
     * {@code none}, {@code clear} and {@code empty} explicitly select clear text; a missing or
     * unrecognised value returns {@link #PASSCRYPT_FALLBACK}, never {@link #PASSCRYPT_NONE}.
     */
    public static int parsePasscrypt(String value) {
        int passcrypt = lookupPasscrypt(value);
        return passcrypt == PASSCRYPT_UNKNOWN ? PASSCRYPT_FALLBACK : passcrypt;
    }

    /**
     * @return true for clear text and for the unsalted fast hashes (md2, md5, sha-*)
     */
    public static boolean isWeakPasscrypt(int passcrypt) {
        return switch (passcrypt) {
            case PASSCRYPT_NONE, PASSCRYPT_MD2, PASSCRYPT_MD5, PASSCRYPT_SHA1,
                    PASSCRYPT_SHA256, PASSCRYPT_SHA384, PASSCRYPT_SHA512 -> true;
            default -> false;
        };
    }

    private static int lookupPasscrypt(String value) {
        if (value == null) {
            return PASSCRYPT_UNKNOWN;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "none", "clear", "empty" -> PASSCRYPT_NONE;
            case "md2" -> PASSCRYPT_MD2;
            case "md5" -> PASSCRYPT_MD5;
            case "sha-1" -> PASSCRYPT_SHA1;
            case "sha-256" -> PASSCRYPT_SHA256;
            case "sha-384" -> PASSCRYPT_SHA384;
            case "sha-512" -> PASSCRYPT_SHA512;
            case "bcrypt" -> PASSCRYPT_BCRYPT;
            case "glftpd" -> PASSCRYPT_GLFTPD;
            case "argon2" -> PASSCRYPT_ARGON2;
            default -> PASSCRYPT_UNKNOWN;
        };
    }

    /*
     * Returns current Passcrypt type
     */
    protected int getPasscrypt() {
        return _passcrypt;
    }

    /**
     * Creates a user named 'username' and adds it to the users map.
     */
    protected synchronized User createUserImpl(String username) {
        EncryptedBeanUser buser = new EncryptedBeanUser(this, username);
        _users.put(username, new SoftReference<>(buser));
        return buser;
    }

    @Override
    protected User loadUser(String userName) throws UserFileException {
        User user = super.loadUser(userName);
        if (!(user instanceof EncryptedBeanUser) && (user instanceof BeanUser)) {
            return new EncryptedBeanUser(this, (BeanUser) user);
        }
        return user;
    }
}
