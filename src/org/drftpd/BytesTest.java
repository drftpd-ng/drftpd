/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd;

import junit.framework.TestCase;
import junit.framework.TestSuite;


/**
 * @author mog
 * @version $Id: BytesTest.java,v 1.1 2004/11/09 18:59:53 mog Exp $
 */
public class BytesTest extends TestCase {
    public BytesTest() {
        super();
    }

    public BytesTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(BytesTest.class);
    }

    protected void setUp() throws Exception {
        super.setUp();
    }

    protected void tearDown() throws Exception {
        super.tearDown();
    }

    public void testFormat50KB() {
        assertEquals("50.0KB", Bytes.formatBytes(Bytes.KILO * 50, false));
    }

    public void testFormat50KiB() {
        assertEquals("50.0KiB", Bytes.formatBytes(Bytes.KIBI * 50, true));
    }

    public void testFormatByte() {
        assertEquals("123B", Bytes.formatBytes(123, false));
    }

    public void testFormatGB() {
        assertEquals("1.0GB", Bytes.formatBytes(Bytes.GIGA, false));
    }

    public void testFormatGiB() {
        assertEquals("1.0GiB", Bytes.formatBytes(Bytes.GIBI, true));
    }

    public void testFormatKB() {
        assertEquals("1.0KB", Bytes.formatBytes(Bytes.KILO, false));
    }

    public void testFormatKiB() {
        assertEquals("1.0KiB", Bytes.formatBytes(Bytes.KIBI, true));
    }

    public void testFormatMB() {
        assertEquals("1.0MB", Bytes.formatBytes(Bytes.MEGA, false));
    }

    public void testFormatMib() {
        assertEquals("1.0MiB", Bytes.formatBytes(Bytes.MEBI, true));
    }

    public void testFormatTB() {
        assertEquals("1.0TB", Bytes.formatBytes(Bytes.TERRA, false));
    }

    public void testFormatTiB() {
        assertEquals("1.0TiB", Bytes.formatBytes(Bytes.TEBI, true));
    }

    public void testParse1GB() {
        assertEquals(Bytes.GIGA, Bytes.parseBytes("1GB"));
    }

    public void testParse1GiB() {
        assertEquals(Bytes.GIBI, Bytes.parseBytes("1GiB"));
    }

    public void testParse1KB() {
        assertEquals(Bytes.KILO, Bytes.parseBytes("1KB"));
    }

    public void testParse1KiB() {
        assertEquals(Bytes.KIBI, Bytes.parseBytes("1KiB"));
    }

    public void testParse1MB() {
        assertEquals(Bytes.MEGA, Bytes.parseBytes("1MB"));
    }

    public void testParse1MiB() {
        assertEquals(Bytes.MEBI, Bytes.parseBytes("1MiB"));
    }

    public void testParse1TB() {
        assertEquals(Bytes.TERRA, Bytes.parseBytes("1TB"));
    }

    public void testParse1TiB() {
        assertEquals(Bytes.TEBI, Bytes.parseBytes("1TiB"));
    }

    public void testParseByte() {
        assertEquals(123, Bytes.parseBytes("123"));
    }
}
