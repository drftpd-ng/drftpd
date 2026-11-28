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
package org.drftpd.master.common;

import org.drftpd.common.util.Bytes;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author mog
 * @version $Id$
 */
public class BytesTest {

    @Test
    public void testFormat50KB() {
        assertEquals("50.0KB", Bytes.formatBytes(Bytes.KILO * 50, false));
    }

    @Test
    public void testFormat50KiB() {
        assertEquals("50.0KiB", Bytes.formatBytes(Bytes.KIBI * 50, true));
    }

    @Test
    public void testFormatByte() {
        assertEquals("123B", Bytes.formatBytes(123, false));
    }

    @Test
    public void testFormatGB() {
        assertEquals("1.0GB", Bytes.formatBytes(Bytes.GIGA, false));
    }

    @Test
    public void testFormatGiB() {
        assertEquals("1.0GiB", Bytes.formatBytes(Bytes.GIBI, true));
    }

    @Test
    public void testFormatKB() {
        assertEquals("1.0KB", Bytes.formatBytes(Bytes.KILO, false));
    }

    @Test
    public void testFormatKiB() {
        assertEquals("1.0KiB", Bytes.formatBytes(Bytes.KIBI, true));
    }

    @Test
    public void testFormatMB() {
        assertEquals("1.0MB", Bytes.formatBytes(Bytes.MEGA, false));
    }

    @Test
    public void testFormatMib() {
        assertEquals("1.0MiB", Bytes.formatBytes(Bytes.MEBI, true));
    }

    @Test
    public void testFormatTB() {
        assertEquals("1.0TB", Bytes.formatBytes(Bytes.TERRA, false));
    }

    @Test
    public void testFormatTiB() {
        assertEquals("1.0TiB", Bytes.formatBytes(Bytes.TEBI, true));
    }

    @Test
    public void testMaxUnitUnsetKeepsDefault() {
        assertEquals("50.0GB", Bytes.formatBytes(50 * Bytes.GIGA, false, null));
        assertEquals("50.0GiB", Bytes.formatBytes(50 * Bytes.GIBI, true, null));
    }

    @Test
    public void testMaxUnitMegabytes() {
        assertEquals("50000.0MB", Bytes.formatBytes(50 * Bytes.GIGA, false, "M"));
        assertEquals("51200.0MiB", Bytes.formatBytes(50 * Bytes.GIBI, true, "M"));
    }

    @Test
    public void testMaxUnitLowercase() {
        assertEquals("50000.0MB", Bytes.formatBytes(50 * Bytes.GIGA, false, "m"));
    }

    @Test
    public void testMaxUnitGigabytesForTerabytes() {
        assertEquals("2000.0GB", Bytes.formatBytes(2 * Bytes.TERRA, false, "G"));
        assertEquals("2048.0GiB", Bytes.formatBytes(2 * Bytes.TEBI, true, "G"));
    }

    @Test
    public void testMaxUnitKilobytes() {
        assertEquals("5000.0KB", Bytes.formatBytes(5 * Bytes.MEGA, false, "K"));
        assertEquals("5120.0KiB", Bytes.formatBytes(5 * Bytes.MEBI, true, "K"));
    }

    @Test
    public void testMaxUnitDoesNotAffectSmallerValues() {
        assertEquals("1.0MB", Bytes.formatBytes(Bytes.MEGA, false, "G"));
        assertEquals("123B", Bytes.formatBytes(123, false, "K"));
    }

    @Test
    public void testMaxUnitInvalidIsIgnored() {
        assertEquals("50.0GB", Bytes.formatBytes(50 * Bytes.GIGA, false, "X"));
        assertEquals("50.0GB", Bytes.formatBytes(50 * Bytes.GIGA, false, "MB"));
        assertEquals("50.0GB", Bytes.formatBytes(50 * Bytes.GIGA, false, ""));
    }

    @Test
    public void testMaxUnitSystemProperty() {
        String old = System.getProperty("bytes.maxunit");
        try {
            System.setProperty("bytes.maxunit", "M");
            assertEquals("50000.0MB", Bytes.formatBytes(50 * Bytes.GIGA));
        } finally {
            if (old == null) {
                System.clearProperty("bytes.maxunit");
            } else {
                System.setProperty("bytes.maxunit", old);
            }
        }
    }

    @Test
    public void testParse1GB() {
        assertEquals(Bytes.GIGA, Bytes.parseBytes("1GB"));
    }

    @Test
    public void testParse1GiB() {
        assertEquals(Bytes.GIBI, Bytes.parseBytes("1GiB"));
    }

    @Test
    public void testParse1KB() {
        assertEquals(Bytes.KILO, Bytes.parseBytes("1KB"));
    }

    @Test
    public void testParse1KiB() {
        assertEquals(Bytes.KIBI, Bytes.parseBytes("1KiB"));
    }

    @Test
    public void testParse1MB() {
        assertEquals(Bytes.MEGA, Bytes.parseBytes("1MB"));
    }

    @Test
    public void testParse1MiB() {
        assertEquals(Bytes.MEBI, Bytes.parseBytes("1MiB"));
    }

    @Test
    public void testParse1TB() {
        assertEquals(Bytes.TERRA, Bytes.parseBytes("1TB"));
    }

    @Test
    public void testParse1TiB() {
        assertEquals(Bytes.TEBI, Bytes.parseBytes("1TiB"));
    }

    @Test
    public void testParseByte() {
        assertEquals(123, Bytes.parseBytes("123"));
    }

    @Test
    public void testParseTrimsAndAcceptsFractionsAndSign() {
        assertEquals(1500000000L, Bytes.parseBytes(" 1.5G "));
        assertEquals(-50 * Bytes.MEGA, Bytes.parseBytes("-50MB"));
        assertEquals(Bytes.MEGA, Bytes.parseBytes("1m"));
        assertEquals(Bytes.PETA, Bytes.parseBytes("1P"));
    }

    @Test
    public void testParseRejectsInvalidInput() {
        for (String bad : new String[]{"10x", "1.5.3G", "", "   ", "G", "B", "KB", "1e3", "NaN", "Infinity",
                "0x10", "1IB", "1 0G", "--1G", "1GG", "1,5G"}) {
            assertThrows(NumberFormatException.class, () -> Bytes.parseBytes(bad), "should reject '" + bad + "'");
        }
        assertThrows(NumberFormatException.class, () -> Bytes.parseBytes(null));
    }

    @Test
    public void testParseRejectsOverflow() {
        assertThrows(NumberFormatException.class, () -> Bytes.parseBytes("99999999999T"));
        assertThrows(NumberFormatException.class, () -> Bytes.parseBytes("10E"));
        assertThrows(NumberFormatException.class, () -> Bytes.parseBytes("-10E"));
        assertThrows(NumberFormatException.class, () -> Bytes.parseBytes("99999999999999999999"));
        assertEquals(9 * Bytes.PETA * 1000, Bytes.parseBytes("9E"));
    }
}
