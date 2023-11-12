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
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.drftpd.master.usermanager.javabeans.BeanUserManager;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCrypt;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * @author CyBeR
 * @version $Id: EncryptedBeanUser.java
 */
public class EncryptedBeanUser extends BeanUser {
    private static final Logger logger = LogManager.getLogger(EncryptedBeanUser.class);
    // BCrypt workload to use when generating password hashes.
    private static final int _workload = 12;
    private transient EncryptedBeanUserManager _um;
    private int _encryption = 0;

    /*
     * Converts BeanUser to EncryptedBeanUser
     */
    public EncryptedBeanUser(EncryptedBeanUserManager manager, BeanUser user) {
        super(manager, user.getName());
        _um = manager;

        this.setCredits(user.getCredits());
        this.setDeleted(user.isDeleted());

        this.setGroup(user.getGroup());

        this.setKeyedMap(user.getKeyedMap());
        this.setGroups(user.getGroups());
        this.setHostMaskCollection(user.getHostMaskCollection());
        this.setIdleTime(user.getIdleTime());
        this.setLastReset(user.getLastReset());

        this.setDownloadedBytes(user.getDownloadedBytes());
        this.setDownloadedBytesDay(user.getDownloadedBytesDay());
        this.setDownloadedBytesMonth(user.getDownloadedBytesMonth());
        this.setDownloadedBytesWeek(user.getDownloadedBytesWeek());

        this.setDownloadedFiles(user.getDownloadedFiles());
        this.setDownloadedFilesDay(user.getDownloadedFilesDay());
        this.setDownloadedFilesMonth(user.getDownloadedFilesMonth());
        this.setDownloadedFilesWeek(user.getDownloadedFilesWeek());

        this.setDownloadedTime(user.getDownloadedTime());
        this.setDownloadedTimeDay(user.getDownloadedTimeDay());
        this.setDownloadedTimeMonth(user.getDownloadedTimeMonth());
        this.setDownloadedTimeWeek(user.getDownloadedTimeWeek());

        this.setUploadedBytes(user.getUploadedBytes());
        this.setUploadedBytesDay(user.getUploadedBytesDay());
        this.setUploadedBytesMonth(user.getUploadedBytesMonth());
        this.setUploadedBytesWeek(user.getUploadedBytesWeek());

        this.setUploadedFiles(user.getUploadedFiles());
        this.setUploadedFilesDay(user.getUploadedFilesDay());
        this.setUploadedFilesMonth(user.getUploadedFilesMonth());
        this.setUploadedFilesWeek(user.getUploadedFilesWeek());

        this.setUploadedTime(user.getUploadedTime());
        this.setUploadedTimeDay(user.getUploadedTimeDay());
        this.setUploadedTimeMonth(user.getUploadedTimeMonth());
        this.setUploadedTimeWeek(user.getUploadedTimeWeek());

        for (int i = 0; i < 4; i++) {
            this.setDownloadedBytesForPeriod(i, user.getDownloadedBytesForPeriod(i));
            this.setDownloadedFilesForPeriod(i, user.getDownloadedFilesForPeriod(i));
            this.setDownloadedTimeForPeriod(i, user.getDownloadedTimeForPeriod(i));
            this.setUploadedBytesForPeriod(i, user.getUploadedBytesForPeriod(i));
            this.setUploadedFilesForPeriod(i, user.getUploadedFilesForPeriod(i));
            this.setUploadedTimeForPeriod(i, user.getUploadedTimeForPeriod(i));
        }

        this.setEncryption(EncryptedBeanUserManager.PASSCRYPT_DEFAULT);
        this.setPassword(user.getPassword());

        this.commit();
    }

    /*
     * Constructor to tell Parent class all is good
     */
    public EncryptedBeanUser(String username) {
        super(username);
    }

    /*
     * Constructor to tell Parent class all is good passing user manager
     */
    public EncryptedBeanUser(EncryptedBeanUserManager manager, String username) {
        super(manager, username);
        _um = manager;
    }

    /*
     * Encrypt the text using the "type" of hash
     *
     * Valid types are: MD2/MD5/SHA-1/SHA-256/SHA-384/SHA-512
     */
    private static String Encrypt(String text, String type) {
        try {
            MessageDigest md;
            md = MessageDigest.getInstance(type);
            md.update(text.getBytes(StandardCharsets.ISO_8859_1), 0, text.length());
            byte[] hash = md.digest();
            return convertToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            logger.debug("Encrypt - NoSuchAlgorithm", e);
        }
        return null;
    }

    /*
     * Converts the encrypted text into a readable string format
     */
    private static String convertToHex(byte[] data) {
        StringBuilder buf = new StringBuilder();
        for (byte aData : data) {
            int halfbyte = (aData >>> 4) & 0x0F;
            int two_halfs = 0;
            do {
                if ((0 <= halfbyte) && (halfbyte <= 9))
                    buf.append((char) ('0' + halfbyte));
                else
                    buf.append((char) ('a' + (halfbyte - 10)));
                halfbyte = aData & 0x0F;
            } while (two_halfs++ < 1);
        }
        return buf.toString();
    }

    /*
     * Returns current encryption type of password
     */
    public int getEncryption() {
        return _encryption;
    }

    /*
     * Sets encryption type for password
     */
    public void setEncryption(int encryption) {
        _encryption = encryption;
    }

    /*
     * Sets EncryptedBeanUserManager
     */
    @Override
    public void setUserManager(BeanUserManager manager) {
        _um = (EncryptedBeanUserManager) manager;
        super.setUserManager(manager);
    }

    /*
     * Glftpd uses PBKDF2-HMAC-SHA1 for hashing passwords. The hash is stored as:
     *   "$" + salt + "$" + hash
     * Both salt and hash are in hex
     * 
     * When encoding passwords:
     *   salt is the first 4 bytes (8 hex characters)
     *   hash is the next 20 bytes (40 hex characters)
     * 
     * glftpd's password code can be found in bin/sources/PassChk/passchk.c in any glftpd release
     */

     /*
     * Get a glftpd compatible password encoder
     */
    private Pbkdf2PasswordEncoder getGlftpdPasswordEncoder() {
        final int GLFTPD_SHA_SALT_LEN = 4;
        final int GLFTPD_ITERATIONS = 100;
        final int GLFTPD_SHA1_HASH_WIDTH = 160;

        return new Pbkdf2PasswordEncoder("", GLFTPD_SHA_SALT_LEN, GLFTPD_ITERATIONS, GLFTPD_SHA1_HASH_WIDTH);
    }

    /*
     * Check if a password matches a glftpd password hash
     */
    private boolean glftpdPasswordMatches(String password, String storedPassword) {
        String[] parts = storedPassword.split("\\$");
        if ((parts.length != 3) || (parts.length > 0 && parts[0].length() != 0) || (parts.length > 1 && parts[1].length() != 8) || (parts.length > 2 && parts[2].length() != 40))
        {
                logger.error("glftpdPasswordMatches: incorrect hash length, expected 48 bytes");
                return false;
        }

        String encodedPassword = parts[1] + parts[2];

        return getGlftpdPasswordEncoder().matches(password, encodedPassword);
    }

    /*
     * Hash a password with glftpd's password hashing algorithm
     */
    private String glftpdPasswordEncode(String password) {
        String hash = getGlftpdPasswordEncoder().encode(password);

        if (hash.length() != 48)
        {
                logger.error("glftpdPasswordEncode: incorrect hash length, expected 48 bytes");
                throw new UnsupportedOperationException("glftpdPasswordEncode: incorrect hash length, expected 48 bytes");
        }

        String salt = hash.substring(0, 8);
        String digest = hash.substring(8);

        return "$" + salt + "$" + digest;
    }

    /*
     * This checks the passwords first 3 lets to see which encryption/hash its using
     * then it try's to decode it, and if its not using the right now, it will convert it
     * to the current encryption type
     */
    @Override
    public boolean checkPassword(String password) {
        String storedPassword = this.getPassword();
        String encryptedPassword;
        boolean result = false;
        password = password.trim();

        switch (getEncryption()) {
            case EncryptedBeanUserManager.PASSCRYPT_MD2 -> {
                encryptedPassword = Encrypt(password, "MD2");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_MD5 -> {
                encryptedPassword = Encrypt(password, "MD5");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_SHA1 -> {
                encryptedPassword = Encrypt(password, "SHA-1");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_SHA256 -> {
                encryptedPassword = Encrypt(password, "SHA-256");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_SHA384 -> {
                encryptedPassword = Encrypt(password, "SHA-384");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_SHA512 -> {
                encryptedPassword = Encrypt(password, "SHA-512");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_BCRYPT -> {
                if (BCrypt.checkpw(password, storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_GLFTPD -> {
                if (glftpdPasswordMatches(password, storedPassword)) result = true;
            }
            case EncryptedBeanUserManager.PASSCRYPT_ARGON2 -> {
                Argon2PasswordEncoder encoder = new Argon2PasswordEncoder();
                if (encoder.matches(password, storedPassword)) result = true;
            }
            default -> {
                if (password.equals(storedPassword)) result = true;
            }
        }

        // TODO: if argon2 is the default, check Argon2PasswordEncoder.upgradeEncoding and rehash if necessary
        if (result && (getEncryption() != _um.getPasscrypt())) {
            logger.debug("Converting Password To Current Encryption");
            setEncryption(EncryptedBeanUserManager.PASSCRYPT_DEFAULT);
            this.setPassword(password);
            super.commit();
        }

        return result;
    }

    /*
     * This makes sure the password isn't encrypted already (when being read from xml)
     * It will encrypt the password in all other cases.
     */
    @Override
    public void setPassword(String password) {
        if (((getEncryption() == EncryptedBeanUserManager.PASSCRYPT_NONE) || (!password.equalsIgnoreCase(getPassword()))) && (_um != null)) {
            String pass;
            int encryptionMethod = _um.getPasscrypt();
            switch (encryptionMethod) {
                case EncryptedBeanUserManager.PASSCRYPT_NONE -> {
                    pass = password;
                }
                case EncryptedBeanUserManager.PASSCRYPT_MD2 -> {
                    pass = Encrypt(password, "MD2");
                }
                case EncryptedBeanUserManager.PASSCRYPT_MD5 -> {
                    pass = Encrypt(password, "MD5");
                }
                case EncryptedBeanUserManager.PASSCRYPT_SHA1 -> {
                    pass = Encrypt(password, "SHA-1");
                }
                case EncryptedBeanUserManager.PASSCRYPT_SHA256 -> {
                    pass = Encrypt(password, "SHA-256");
                }
                case EncryptedBeanUserManager.PASSCRYPT_SHA384 -> {
                    pass = Encrypt(password, "SHA-384");
                }
                case EncryptedBeanUserManager.PASSCRYPT_SHA512 -> {
                    pass = Encrypt(password, "SHA-512");
                }
                case EncryptedBeanUserManager.PASSCRYPT_BCRYPT -> {
                    pass = BCrypt.hashpw(password, BCrypt.gensalt(_workload));
                }
                case EncryptedBeanUserManager.PASSCRYPT_GLFTPD -> {
                    pass = glftpdPasswordEncode(password);
                }
                case EncryptedBeanUserManager.PASSCRYPT_ARGON2 -> {
                    Argon2PasswordEncoder encoder = new Argon2PasswordEncoder();
                    pass = encoder.encode(password);
                }
                default -> {
                    logger.error("Failed to set password, unknown password hash format");
                    throw new UnsupportedOperationException("Failed to set password, unknown password hash format");
                }
            }

            if (pass != null) {
                if (pass.length() < 2) {
                    logger.debug("Failed To Set Password, Length Too Short");
                } else {
                    setEncryption(encryptionMethod);
                    super.setPassword(pass);
                }
            }
        } else {
            super.setPassword(password);
        }
    }

}
