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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.drftpd.master.usermanager.javabeans.BeanUserManager;
import org.springframework.security.crypto.bcrypt.BCrypt;

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

    @JsonIgnore
    private transient EncryptedBeanUserManager _um;

    /*
     * Converts BeanUser to EncryptedBeanUser
     */
    public EncryptedBeanUser(EncryptedBeanUserManager manager, BeanUser user) {
        super(manager, user.getName());
        _um = manager;

        this.setCredits(user.getCredits());
        this.setDeleted(user.isDeleted());
        this.setEncryption(user.getEncryption());

        this.setGroup(user.getGroup());

        this.setConfigurations(user.getConfigurations());
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

        this.setPassword(user.getPassword());
        this.commit();
    }

    /*
     * Constructor to tell Parent class all is good
     */
    @SuppressWarnings("unused")
    public EncryptedBeanUser() {
        super();
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
     * Sets EncryptedBeanUserManager
     */
    @Override
    public void setUserManager(BeanUserManager manager) {
        _um = (EncryptedBeanUserManager) manager;
        super.setUserManager(manager);
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
            case 1:
                encryptedPassword = Encrypt(password, "MD2");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 2:
                encryptedPassword = Encrypt(password, "MD5");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 3:
                encryptedPassword = Encrypt(password, "SHA-1");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 4:
                encryptedPassword = Encrypt(password, "SHA-256");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 5:
                encryptedPassword = Encrypt(password, "SHA-384");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 6:
                encryptedPassword = Encrypt(password, "SHA-512");
                if (encryptedPassword != null && encryptedPassword.equals(storedPassword)) result = true;
                break;
            case 7:
                boolean checkpw = BCrypt.checkpw(password, storedPassword);
                if (checkpw) result = true;
                break;
            default:
                if (password.equals(storedPassword)) result = true;
                break;
        }

        // Crypt activated after start, enable with SITE RELOAD
        if (getEncryption() != _um.getPasscrypt()) {
            logger.debug("Converting Password To Current Encryption");
            this.changePassword(password);
            super.commit();
        }

        return result;
    }

    private String encrypt(int expectedEncryption, String password) {
        return switch (expectedEncryption) {
            case 1 -> Encrypt(password, "MD2");
            case 2 -> Encrypt(password, "MD5");
            case 3 -> Encrypt(password, "SHA-1");
            case 4 -> Encrypt(password, "SHA-256");
            case 5 -> Encrypt(password, "SHA-384");
            case 6 -> Encrypt(password, "SHA-512");
            case 7 -> BCrypt.hashpw(password, BCrypt.gensalt(_workload));
            default -> throw new UnsupportedOperationException("Unknown encryption type" + expectedEncryption);
        };
    }

    @Override
    public void changePassword(String password) {
        if (password.length() < 2) {
            logger.debug("Failed To Set Password, Length Too Short");
        }
        super.setPassword(encrypt(_um.getPasscrypt(), password));
    }

    /*
     * Set the password, encrypt on the fly is needed
     */
    @Override
    public void setPassword(String password) {
        int expectedEncryption = _um.getPasscrypt();
        if (getEncryption() == expectedEncryption) {
            // Encryption match, just load the password in memory
            super.setPassword(password);
        } else if (getEncryption() == 0) {
            // Password is currently clear but encryption is now activated
            // In this case we can encrypt on the fly
            setEncryption(expectedEncryption);
            changePassword(password);
        } else {
            throw new UnsupportedOperationException(
                    "Users are already encrypted with " + getEncryption() + " encryption mode," +
                            " its not possible to move to " + expectedEncryption + " mode");
        }
    }
}
