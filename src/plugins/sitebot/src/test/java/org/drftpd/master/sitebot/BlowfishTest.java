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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
