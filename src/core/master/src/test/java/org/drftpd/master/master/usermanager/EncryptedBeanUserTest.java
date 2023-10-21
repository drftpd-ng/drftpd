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
package org.drftpd.master.master.usermanager;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUser;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager;

/**
 * @version $Id$
 */
public class EncryptedBeanUserTest {

    private class TestEncryptedBeanUserManager extends EncryptedBeanUserManager {
        public Integer testPasscrypt = null;

        public TestEncryptedBeanUserManager() {
            super();
        }

        @Override
        protected int getPasscrypt() {
            if (testPasscrypt != null) {
                return testPasscrypt;
            }
            return super.getPasscrypt();
        }
    }

    private class TestEncryptedBeanUser extends EncryptedBeanUser {
        public String testPassword = null;

        public TestEncryptedBeanUser(EncryptedBeanUserManager manager, String username) {
            super(manager, username);
        }

        @Override
        public String getPassword() {
            if (testPassword != null) {
                return testPassword;
            }
            return super.getPassword();
        }
    }

    @Test
    public void testBcryptPasswordHash() {
        // Test that a password can be hashed with bcrypt.
        // An improvement could be to have the hashing use a fixed salt value, and then verify the hash.
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_BCRYPT;

        EncryptedBeanUser user = new EncryptedBeanUser(manager, "bcrypt");
        user.setPassword("abc123xyz");

        assertEquals(true, user.checkPassword("abc123xyz"));
        assertEquals(false, user.checkPassword("Abc123xyz"));
    }

    @Test
    public void testBcryptPasswordVerify() {
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_BCRYPT;

        TestEncryptedBeanUser user = new TestEncryptedBeanUser(manager, "glftpd");
        user.setEncryption(EncryptedBeanUserManager.PASSCRYPT_BCRYPT);
        user.testPassword = "$2a$12$R9h/cIPz0gi.URNNX3kh2OPST9/PgBkqquzi.Ss7KIUgO2t0jWMUW";

        assertEquals(true, user.checkPassword("abc123xyz"));
        assertEquals(false, user.checkPassword("Abc123xyz"));
    }

    @Test
    public void testGlftpdPasswordHash() {
        // Test that a password can be hashed with glftpd's password hashing algorithm.
        // An improvement could be to have the hashing use a fixed salt value, and then verify the hash.
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_GLFTPD;

        EncryptedBeanUser user = new EncryptedBeanUser(manager, "glftpd");
        user.setPassword("glftpd");

        assertEquals(true, user.checkPassword("glftpd"));
        assertEquals(false, user.checkPassword("Glftpd"));
    }

    @Test
    public void testGlftpdPasswordVerify() {
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_GLFTPD;

        TestEncryptedBeanUser user = new TestEncryptedBeanUser(manager, "glftpd");
        user.setEncryption(EncryptedBeanUserManager.PASSCRYPT_GLFTPD);
        user.testPassword = "$c8aa2099$89be575337e36892c6d7f4181cad175d685162ad";

        assertEquals(true, user.checkPassword("glftpd"));
        assertEquals(false, user.checkPassword("Glftpd"));
    }

    @Test
    public void testArgon2PasswordHash() {
        // Test that a password can be hashed with argon2.
        // An improvement could be to have the hashing use a fixed parameters, and then verify the hash.
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_ARGON2;

        TestEncryptedBeanUser user = new TestEncryptedBeanUser(manager, "argon2");
        user.setEncryption(EncryptedBeanUserManager.PASSCRYPT_ARGON2);
        user.setPassword("password");

        assertEquals(true, user.checkPassword("password"));
        assertEquals(false, user.checkPassword("Password"));
    }

    @Test
    public void testArgon2PasswordVerify() {
        TestEncryptedBeanUserManager manager = new TestEncryptedBeanUserManager();
        manager.testPasscrypt = EncryptedBeanUserManager.PASSCRYPT_ARGON2;

        TestEncryptedBeanUser user = new TestEncryptedBeanUser(manager, "argon2");
        user.setEncryption(EncryptedBeanUserManager.PASSCRYPT_ARGON2);
        user.testPassword = "$argon2i$v=19$m=65536,t=2,p=4$c29tZXNhbHQ$RdescudvJCsgt3ub+b+dWRWJTmaaJObG";

        assertEquals(true, user.checkPassword("password"));
        assertEquals(false, user.checkPassword("Password"));
    }
}
