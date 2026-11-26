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

import org.junit.jupiter.api.RepeatedTest;

import org.drftpd.master.sitebot.blowfish.BlowfishCBC;
import org.drftpd.master.sitebot.blowfish.BlowfishECB;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BlowfishTest  {

    @RepeatedTest(10000)
    public void testCBC() {
        BlowfishManager manager = new BlowfishManager("drftpd", "CBC");
        String text = "You need to specify a Ident@IP to add " + Math.random();
        String encrypt = manager.encrypt(text);
        String decrypt = manager.decrypt(encrypt);
        assertEquals(decrypt, text);
    }

    @RepeatedTest(10000)
    public void testECB() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        String text = "You need to specify a Ident@IP to add " + Math.random();
        String encrypt = ecb.encrypt(text);
        String decrypt = ecb.decrypt(encrypt);
        assertEquals(decrypt, text);
    }

    @Test
    public void testEcbKeyDecryptsCbcLine() {
        BlowfishManager cbc = new BlowfishManager("drftpd", "CBC");
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        String line = cbc.encrypt("hello from a cbc client");
        assertTrue(line.startsWith("+OK *"));
        assertEquals("hello from a cbc client", ecb.decrypt(line));
    }

    @Test
    public void testCbcKeyIgnoresEcbLineByDefault() {
        String line = new BlowfishManager("drftpd", "ECB").encrypt("hello from an ecb client");
        assertNull(new BlowfishManager("drftpd", "CBC").decrypt(line));
    }

    @Test
    public void testCbcKeyAcceptsEcbLineWithFallback() {
        String line = new BlowfishManager("drftpd", "ECB").encrypt("hello from an ecb client");
        assertEquals("hello from an ecb client", new BlowfishManager("drftpd", "CBC", true).decrypt(line));
    }

    @Test
    public void testMcpsPrefix() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        BlowfishManager cbc = new BlowfishManager("drftpd", "CBC");
        String ecbLine = "mcps " + ecb.encrypt("mcps ecb").substring(4);
        String cbcLine = "mcps " + cbc.encrypt("mcps cbc").substring(4);
        assertEquals("mcps ecb", ecb.decrypt(ecbLine));
        assertEquals("mcps cbc", ecb.decrypt(cbcLine));
        assertEquals("mcps cbc", cbc.decrypt(cbcLine));
    }

    @Test
    public void testCutEcbLineKeepsCompleteBlocks() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        String text = "0123456789abcdefghijklmnopqrst"; // 4 blocks
        String line = ecb.encrypt(text);
        assertEquals(text, ecb.decrypt(line));
        // lose 5 characters of the last block
        assertEquals("0123456789abcdefghijklmn", ecb.decrypt(line.substring(0, line.length() - 5)));
    }

    @Test
    public void testCutCbcLineKeepsCompleteBlocks() {
        BlowfishManager cbc = new BlowfishManager("drftpd", "CBC");
        String text = "0123456789abcdefghijklmnopqrst"; // IV + 4 blocks = 40 bytes = 54 base64 chars + "=="
        String line = cbc.encrypt(text);
        assertEquals(text, cbc.decrypt(line));
        // only the padding is lost
        assertEquals(text, cbc.decrypt(line.substring(0, line.length() - 2)));
        for (int lost = 3; lost <= 13; lost++) {
            String result = cbc.decrypt(line.substring(0, line.length() - lost));
            assertEquals("0123456789abcdefghijklmn", result, "lost " + lost);
        }
    }

    @Test
    public void testCutInsideMultibyteCharacter() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        // 7 ASCII bytes then a 2 byte character across the block boundary
        String text = "abcdefgéxyz";
        String line = ecb.encrypt(text);
        assertEquals(text, ecb.decrypt(line));
        assertEquals("abcdefg", ecb.decrypt(line.substring(0, line.length() - 1)));
    }

    @Test
    public void testTrailingTextIgnored() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        assertEquals("hello", ecb.decrypt(ecb.encrypt("hello") + " [12:00]"));
        BlowfishManager cbc = new BlowfishManager("drftpd", "CBC");
        assertEquals("hello", cbc.decrypt(cbc.encrypt("hello") + " [12:00]"));
    }

    @Test
    public void testInvalidPayloadRejected() {
        BlowfishECB ecb = new BlowfishECB("drftpd");
        assertThrows(IllegalArgumentException.class, () -> ecb.decryptPayload("abc!efghijkl"));
        assertThrows(IllegalArgumentException.class, () -> ecb.decryptPayload("short"));
        BlowfishCBC cbc = new BlowfishCBC("drftpd");
        assertThrows(IllegalArgumentException.class, () -> cbc.decryptPayload("AAAAAAAAAAA="));
        assertThrows(IllegalArgumentException.class, () -> cbc.decryptPayload("not!base64#at all"));
    }
}
