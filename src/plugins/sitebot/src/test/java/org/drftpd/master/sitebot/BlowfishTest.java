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

import org.drftpd.master.sitebot.blowfish.BlowfishCBC;
import org.drftpd.master.sitebot.blowfish.BlowfishECB;
import org.drftpd.master.sitebot.blowfish.DecryptedText;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        String text = "abcdefg\u00e9xyz";
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

    @Test
    public void testDecryptFailureGivesNull() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        BlowfishManager cbc = new BlowfishManager("drftpd", "CBC", true);
        assertNull(ecb.decrypt("+OK abc!efghijkl"));
        assertNull(ecb.decrypt("+OK short"));
        assertNull(cbc.decrypt("+OK *not!base64"));
        assertNull(cbc.decrypt("+OK *AAAA"));
        assertNull(cbc.decrypt("plain text"));
    }

    @Test
    public void testEncryptFailureGivesNull() {
        // Blowfish keys are limited to 56 bytes
        BlowfishManager tooLong = new BlowfishManager("x".repeat(60), "CBC");
        assertNull(tooLong.encrypt("hello"));
    }

    @Test
    public void testEcbFullBlockGetsNoExtraBlock() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        assertEquals(4 + 12, ecb.encrypt("12345678").length());
        assertEquals(4 + 24, ecb.encrypt("123456789").length());
        assertEquals("", ecb.decrypt(ecb.encrypt("")));
    }

    @Test
    public void testPlaintextBudgetFitsTheLine() {
        for (String mode : new String[]{"ECB", "CBC"}) {
            BlowfishManager manager = new BlowfishManager("drftpd", mode);
            for (int budget = 0; budget <= 450; budget++) {
                int max = manager.maxPlaintextBytes(budget);
                if (max == 0) {
                    continue;
                }
                String fits = "x".repeat(max);
                assertTrue(manager.encrypt(fits).length() <= budget, mode + " " + budget);
                assertTrue(manager.encrypt(fits + "x").length() > budget, mode + " " + budget + " not tight");
            }
        }
    }

    @Test
    public void testSplitOnUtf8Bytes() {
        String text = "\u00e9t\u00e9 \u00fcber \ud83d\ude00\ud83d\ude00 caf\u00e9 na\u00efve " + "\u00e9".repeat(40);
        for (int max = 4; max <= 30; max++) {
            List<String> lines = OutputWriter.splitUtf8(text, max);
            StringBuilder joined = new StringBuilder();
            for (String line : lines) {
                assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= max, max + ": " + line);
                assertFalse(line.isEmpty(), "empty line at " + max);
                // no broken surrogate pair
                assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)), max + ": " + line);
                joined.append(line);
            }
            assertEquals(text.replace(" ", ""), joined.toString().replace(" ", ""));
        }
        assertEquals(List.of("short"), OutputWriter.splitUtf8("short", 100));
        assertEquals(List.of("hello", "world"), OutputWriter.splitUtf8("hello world", 8));
        // a tiny or negative budget still splits, one character at least per line
        for (int max : new int[]{-5, 0, 1, 3}) {
            for (String line : OutputWriter.splitUtf8(text, max)) {
                assertTrue(line.getBytes(StandardCharsets.UTF_8).length <= 4, max + ": " + line);
            }
        }
    }

    /*
     * Vectors computed outside Java with pycryptodome, following
     * fish_10/src/blowfish.cpp (ECB) and blowfish_cbc.cpp (CBC, IV 01..08).
     */
    private static final String FISH_ECB_ASCII = "+OK FVU1G0UPghf.Ci8u/.qQFyB.lflgm/IqULz1";
    private static final String FISH_ECB_UTF8 = "+OK UXYCK0Jl3Ji0Unhep/QQlkh.";
    private static final String FISH_CBC_ASCII = "+OK *iwXpQeVhnQkEmgQnp/xbqR4YZzEYjJD0ARMRGJHwZEQ=";
    private static final String FISH_CBC_UTF8 = "+OK *xInM77wtBi0d2vaD/SJPaR/bSkxMWN3t";
    private static final String UTF8_TEXT = "caf\u00e9 \u00fcber \ud83d\ude00";

    @Test
    public void testFishEcbVectors() {
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        assertEquals(FISH_ECB_ASCII, ecb.encrypt("Hello from FiSH 10"));
        assertEquals("Hello from FiSH 10", ecb.decrypt(FISH_ECB_ASCII));
        BlowfishManager ecbUtf8 = new BlowfishManager("cl\u00e9", "ECB");
        assertEquals(FISH_ECB_UTF8, ecbUtf8.encrypt(UTF8_TEXT));
        assertEquals(UTF8_TEXT, ecbUtf8.decrypt(FISH_ECB_UTF8));
    }

    @Test
    public void testFishCbcVectors() {
        assertEquals("Hello from FiSH 10", new BlowfishManager("drftpd", "CBC").decrypt(FISH_CBC_ASCII));
        assertEquals(UTF8_TEXT, new BlowfishManager("cl\u00e9", "CBC").decrypt(FISH_CBC_UTF8));
        // auto-detect: an ECB key still reads a FiSH CBC line
        assertEquals("Hello from FiSH 10", new BlowfishManager("drftpd", "ECB").decrypt(FISH_CBC_ASCII));
    }

    @Test
    public void testLatin1Fallback() {
        // a legacy client sending ISO-8859-1 "caf\u00e9" (0xE9 alone is not valid UTF-8)
        BlowfishManager ecb = new BlowfishManager("drftpd", "ECB");
        assertEquals("caf\u00e9", ecb.decrypt("+OK H4uV.0JJ2B4/"));
        // ISO-8859-1 "\u00e9t\u00e9 caf\u00e9xyz", cut after the first block: the last
        // legacy byte is kept, it is not taken for a broken UTF-8 character
        String legacy = "+OK 9YfW80Rq6a21lzcs0.lAJ5..";
        assertEquals("\u00e9t\u00e9 caf\u00e9xyz", ecb.decrypt(legacy));
        assertEquals("\u00e9t\u00e9 caf\u00e9", ecb.decrypt(legacy.substring(0, legacy.length() - 1)));
    }

    @Test
    public void testUtf8RoundTrip() {
        String text = "caf\u00e9 \u00fcber na\u00efve \ud83d\ude00 \u4e2d\u6587";
        for (String mode : new String[]{"ECB", "CBC"}) {
            BlowfishManager manager = new BlowfishManager("cl\u00e9", mode);
            assertEquals(text, manager.decrypt(manager.encrypt(text)), mode);
        }
    }

    @Test
    public void testCutLineIsFlagged() {
        String text = "!request some.release.name-GROUP";
        for (String mode : new String[]{"ECB", "CBC"}) {
            BlowfishManager manager = new BlowfishManager("drftpd", mode);
            String line = manager.encrypt(text);
            DecryptedText whole = manager.decryptLine(line);
            assertEquals(text, whole.text(), mode);
            assertFalse(whole.cut(), mode);
            DecryptedText cut = manager.decryptLine(line.substring(0, line.length() - 7));
            assertTrue(cut.cut(), mode);
            assertTrue(text.startsWith(cut.text()) && cut.text().length() < text.length(), mode + ": " + cut.text());
        }
    }
}
