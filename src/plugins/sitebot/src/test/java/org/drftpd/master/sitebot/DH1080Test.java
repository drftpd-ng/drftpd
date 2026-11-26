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
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class DH1080Test {
    private static final Logger logger = LogManager.getLogger(DH1080Test.class);

    public static String[] correctKeys = new String[] {
        "ZitCYCAe3OrlWlYkzVqeSdr6jAk2yst2B7bPsYeldwATjQjq6U62RTQJA00TMu9O/E0bjob6qR/f6xNFyWr3Oybp51IcviiIOusScdsuVMIO6M5EmoAZoEU66hf9sJCYlm3F47E9Zf0eh3sI3ZFeYZZjItLNPPenAEs9zwm2fZzi2Xkc4dMnA",
        "UPLJ9yRki+zHbE8M1/DBSXuXUCpNf90gXtZAMabJTiYeI4L6BZjPXtZAzuPFmq9GfePD4bCuv9h0pLOysVK0P2IwFjMW0gV7sx7KpqjXsk7Uf42SsI3C4SoWotdbnGqKcWTQjhCf6qHb3yReIsB+W2xM7J93+7wWd94n5csmmR9uhvmC5wsmA",
        "l3RtgoMik15PRUwEFl3zTCDhH+aREtwAyjtijFVcfJBCHJw0nN7p5nGDfI0awOiQC/kyitreDeExPFk2cZu7KlakdwrYsUpiC1q77WVlw3WCiBJgzW2ZKVSu/YhDT+4KTvpkwkQuU6+MYw/jQnnLJDz0+npBGoImTAFkBTVEQWE/hvK0TT64A"
    };

    public static String[] incorrectKeys = new String[] {
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAABA",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACA",
        "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++",
        "++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++",
        "PPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPPP",
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA@",
    };

    @RepeatedTest(1000)
    public void testDH1080BruteForce() {
        DH1080 dh1 = new DH1080();
        DH1080 dh2 = new DH1080();
        assertFalse(dh1.isNotAValidPublicKey(dh2.getPublicKey()));
        assertFalse(dh2.isNotAValidPublicKey(dh1.getPublicKey()));
        assertNotNull(dh1.getSharedSecret(dh2.getPublicKey()));
    }

    @Test
    public void testDH1080IncorrectKeys() {
        DH1080 testDH = new DH1080();
        for (String key : incorrectKeys) {
            logger.debug("Testing Incorrect key: [{}]", key);
            assertNull(testDH.getSharedSecret(key));
        }
    }

    @Test
    public void testDH1080AgreeOnSecret() {
        DH1080 dh1 = new DH1080();
        DH1080 dh2 = new DH1080();
        String secret1 = dh1.getSharedSecret(dh2.getPublicKey());
        String secret2 = dh2.getSharedSecret(dh1.getPublicKey());
        assertNotNull(secret1);
        assertEquals(secret1, secret2);
    }

    @Test
    public void testDecodeB64InvalidInput() {
        assertEquals(0, DH1080.decodeB64(null).length);
        assertEquals(0, DH1080.decodeB64("").length);
        assertEquals(0, DH1080.decodeB64("AAAA=").length);
        // Characters above 0xFF must not overflow the lookup table
        assertEquals(0, DH1080.decodeB64("AAA€").length);
        assertEquals(0, DH1080.decodeB64("中文").length);
        assertNull(new DH1080().getSharedSecret("€" + correctKeys[0].substring(1)));
    }

    @Test
    public void testParseMessage() {
        String key = correctKeys[0];
        DH1080.Message m = DH1080.parseMessage("DH1080_INIT " + key + " CBC");
        assertEquals(DH1080.INIT, m.command());
        assertEquals(key, m.publicKey());
        assertEquals("CBC", m.modeToken());

        m = DH1080.parseMessage("DH1080_INIT " + key);
        assertEquals(key, m.publicKey());
        assertNull(m.modeToken());

        m = DH1080.parseMessage("DH1080_FINISH " + key);
        assertEquals(DH1080.FINISH, m.command());
        assertEquals(key, m.publicKey());
        assertNull(m.modeToken());

        m = DH1080.parseMessage("  DH1080_FINISH   " + key + "   CBC  ");
        assertEquals(DH1080.FINISH, m.command());
        assertEquals(key, m.publicKey());
        assertEquals("CBC", m.modeToken());
    }

    @Test
    public void testParseCbcCommandSuffix() {
        String key = correctKeys[0];
        DH1080.Message m = DH1080.parseMessage("DH1080_INIT_cbc " + key);
        assertEquals(DH1080.INIT, m.command());
        assertEquals(key, m.publicKey());
        assertEquals("CBC", m.modeToken());

        m = DH1080.parseMessage("DH1080_FINISH_CBC " + key + " CBC");
        assertEquals(DH1080.FINISH, m.command());
        assertEquals("CBC", m.modeToken());

        // Contradictory or incomplete
        assertNull(DH1080.parseMessage("DH1080_INIT_cbc " + key + " ECB"));
        assertNull(DH1080.parseMessage("DH1080_INIT_cbc"));
        assertNull(DH1080.parseMessage("DH1080_INIT_ecb " + key));
    }

    @Test
    public void testParseMalformedMessage() {
        assertNull(DH1080.parseMessage(null));
        assertNull(DH1080.parseMessage(""));
        assertNull(DH1080.parseMessage("DH1080_INIT"));
        assertNull(DH1080.parseMessage("DH1080_INIT  "));
        assertNull(DH1080.parseMessage("DH1080_FINISH"));
        assertNull(DH1080.parseMessage("DH1080_FINISH  "));
        assertNull(DH1080.parseMessage("DH1080_FINISH " + correctKeys[0] + " CBC extra"));
        assertNull(DH1080.parseMessage("DH1080_INITX " + correctKeys[0]));
        assertNull(DH1080.parseMessage("DH1080_FOO " + correctKeys[0]));
    }

    @Test
    public void testNegotiateMode() {
        // CBC token (FiSH 10 default)
        assertEquals(BlowfishManager.CBC, DH1080.negotiateMode("CBC", true, false));
        assertEquals(BlowfishManager.CBC, DH1080.negotiateMode("cbc", true, true));
        assertNull(DH1080.negotiateMode("CBC", false, true));
        // No token (older FiSH, or FiSH 10 with dh1080_cbc=0) means ECB
        assertEquals(BlowfishManager.ECB, DH1080.negotiateMode(null, true, true));
        assertNull(DH1080.negotiateMode(null, true, false));
        // Explicit ECB token
        assertEquals(BlowfishManager.ECB, DH1080.negotiateMode("ECB", false, true));
        assertNull(DH1080.negotiateMode("ECB", true, false));
        // Unknown token
        assertNull(DH1080.negotiateMode("XYZ", true, true));
    }

    @Test
    public void testModeSuffix() {
        assertEquals(" CBC", DH1080.modeSuffix(BlowfishManager.CBC));
        assertEquals("", DH1080.modeSuffix(BlowfishManager.ECB));
        // What we send must be understood by our own parser and give back the same mode
        String key = new DH1080().getPublicKey();
        DH1080.Message m = DH1080.parseMessage(DH1080.FINISH + " " + key + DH1080.modeSuffix(BlowfishManager.CBC));
        assertEquals(BlowfishManager.CBC, DH1080.negotiateMode(m.modeToken(), true, false));
        m = DH1080.parseMessage(DH1080.FINISH + " " + key + DH1080.modeSuffix(BlowfishManager.ECB));
        assertEquals(BlowfishManager.ECB, DH1080.negotiateMode(m.modeToken(), false, true));
    }

    @Test
    public void testDH1080CorrectKeys() {
        DH1080 testDH = new DH1080();
        for (String key : correctKeys) {
            logger.debug("Testing Correct key: [{}]", key);
            assertNotNull(testDH.getSharedSecret(key));
        }
    }
}
