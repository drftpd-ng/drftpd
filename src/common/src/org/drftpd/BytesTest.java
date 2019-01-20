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
import org.junit.Assert;


/**
 * @author mog
 * @version $Id$
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
        Assert.assertEquals("50.0KB", Bytes.formatBytes(Bytes.KILO * 50, false));
    }

    public void testFormat50KiB() {
        Assert.assertEquals("50.0KiB", Bytes.formatBytes(Bytes.KIBI * 50, true));
    }

    public void testFormatByte() {
        Assert.assertEquals("123B", Bytes.formatBytes(123, false));
    }

    public void testFormatGB() {
        Assert.assertEquals("1.0GB", Bytes.formatBytes(Bytes.GIGA, false));
    }

    public void testFormatGiB() {
        Assert.assertEquals("1.0GiB", Bytes.formatBytes(Bytes.GIBI, true));
    }

    public void testFormatKB() {
        Assert.assertEquals("1.0KB", Bytes.formatBytes(Bytes.KILO, false));
    }

    public void testFormatKiB() {
        Assert.assertEquals("1.0KiB", Bytes.formatBytes(Bytes.KIBI, true));
    }

    public void testFormatMB() {
        Assert.assertEquals("1.0MB", Bytes.formatBytes(Bytes.MEGA, false));
    }

    public void testFormatMib() {
        Assert.assertEquals("1.0MiB", Bytes.formatBytes(Bytes.MEBI, true));
    }

    public void testFormatTB() {
        Assert.assertEquals("1.0TB", Bytes.formatBytes(Bytes.TERRA, false));
    }

    public void testFormatTiB() {
        Assert.assertEquals("1.0TiB", Bytes.formatBytes(Bytes.TEBI, true));
    }

    public void testParse1GB() {
        Assert.assertEquals(Bytes.GIGA, Bytes.parseBytes("1GB"));
    }

    public void testParse1GiB() {
        Assert.assertEquals(Bytes.GIBI, Bytes.parseBytes("1GiB"));
    }

    public void testParse1KB() {
        Assert.assertEquals(Bytes.KILO, Bytes.parseBytes("1KB"));
    }

    public void testParse1KiB() {
        Assert.assertEquals(Bytes.KIBI, Bytes.parseBytes("1KiB"));
    }

    public void testParse1MB() {
        Assert.assertEquals(Bytes.MEGA, Bytes.parseBytes("1MB"));
    }

    public void testParse1MiB() {
        Assert.assertEquals(Bytes.MEBI, Bytes.parseBytes("1MiB"));
    }

    public void testParse1TB() {
        Assert.assertEquals(Bytes.TERRA, Bytes.parseBytes("1TB"));
    }

    public void testParse1TiB() {
        Assert.assertEquals(Bytes.TEBI, Bytes.parseBytes("1TiB"));
    }

    public void testParseByte() {
        Assert.assertEquals(123, Bytes.parseBytes("123"));
    }
}
