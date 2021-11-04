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
        assertEquals("123.0B", Bytes.formatBytes(123, false));
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
}
