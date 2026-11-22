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

import org.drftpd.master.tests.DummyEncryptedBeanUserManager;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUser;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager.*;
import static org.junit.jupiter.api.Assertions.*;

public class PasscryptConfigTest {

    @Test
    public void testValidNames() {
        assertEquals(PASSCRYPT_NONE, parsePasscrypt("none"));
        assertEquals(PASSCRYPT_NONE, parsePasscrypt("clear"));
        assertEquals(PASSCRYPT_NONE, parsePasscrypt("empty"));
        assertEquals(PASSCRYPT_MD2, parsePasscrypt("md2"));
        assertEquals(PASSCRYPT_MD5, parsePasscrypt("md5"));
        assertEquals(PASSCRYPT_SHA1, parsePasscrypt("sha-1"));
        assertEquals(PASSCRYPT_SHA256, parsePasscrypt("sha-256"));
        assertEquals(PASSCRYPT_SHA384, parsePasscrypt("sha-384"));
        assertEquals(PASSCRYPT_SHA512, parsePasscrypt("sha-512"));
        assertEquals(PASSCRYPT_BCRYPT, parsePasscrypt("bcrypt"));
        assertEquals(PASSCRYPT_GLFTPD, parsePasscrypt("glftpd"));
        assertEquals(PASSCRYPT_ARGON2, parsePasscrypt("argon2"));
    }

    @Test
    public void testWhitespaceAndCase() {
        assertEquals(PASSCRYPT_BCRYPT, parsePasscrypt("  BCrypt \t"));
        assertEquals(PASSCRYPT_ARGON2, parsePasscrypt("ARGON2 "));
        assertEquals(PASSCRYPT_SHA512, parsePasscrypt("SHA-512"));
        assertEquals(PASSCRYPT_NONE, parsePasscrypt(" None "));
    }

    @Test
    public void testFallbackIsBcrypt() {
        assertEquals(PASSCRYPT_BCRYPT, PASSCRYPT_FALLBACK);
    }

    @Test
    public void testUnknownFallsBackToBcrypt() {
        for (String value : new String[]{"", "   ", "argon", "bcypt", "sha256", "sha1", "plain", "0", "none,", "no ne"}) {
            assertEquals(PASSCRYPT_BCRYPT, parsePasscrypt(value), "value: '" + value + "'");
        }
    }

    @Test
    public void testNullFallsBackToBcrypt() {
        assertEquals(PASSCRYPT_BCRYPT, parsePasscrypt(null));
    }

    @Test
    public void testResolveFromConfig() {
        Properties cfg = new Properties();
        assertEquals(PASSCRYPT_BCRYPT, resolvePasscrypt(cfg), "missing key must not mean clear text");
        cfg.setProperty("passcrypt", "bcypt");
        assertEquals(PASSCRYPT_BCRYPT, resolvePasscrypt(cfg), "typo must not mean clear text");
        cfg.setProperty("passcrypt", "argon2");
        assertEquals(PASSCRYPT_ARGON2, resolvePasscrypt(cfg));
        cfg.setProperty("passcrypt", "md5");
        assertEquals(PASSCRYPT_MD5, resolvePasscrypt(cfg));
        cfg.setProperty("passcrypt", "none");
        assertEquals(PASSCRYPT_NONE, resolvePasscrypt(cfg));
    }

    @Test
    public void testWeakPasscrypt() {
        for (int p : new int[]{PASSCRYPT_NONE, PASSCRYPT_MD2, PASSCRYPT_MD5, PASSCRYPT_SHA1,
                PASSCRYPT_SHA256, PASSCRYPT_SHA384, PASSCRYPT_SHA512}) {
            assertTrue(isWeakPasscrypt(p), "expected weak: " + p);
        }
        for (int p : new int[]{PASSCRYPT_BCRYPT, PASSCRYPT_ARGON2, PASSCRYPT_GLFTPD}) {
            assertFalse(isWeakPasscrypt(p), "expected not weak: " + p);
        }
    }

    @Test
    public void testInvalidConfigDoesNotStoreClearText() {
        DummyEncryptedBeanUserManager manager = new DummyEncryptedBeanUserManager(parsePasscrypt("bcypt"));
        EncryptedBeanUser user = new EncryptedBeanUser(manager, "fallback");
        user.setPassword("abc123xyz");

        assertNotEquals("abc123xyz", user.getPassword());
        assertTrue(user.getPassword().startsWith("$2"), "expected a bcrypt hash");
        assertEquals(EncryptedBeanUserManager.PASSCRYPT_BCRYPT, user.getEncryption());
        assertTrue(user.checkPassword("abc123xyz"));
        assertFalse(user.checkPassword("Abc123xyz"));
    }
}
