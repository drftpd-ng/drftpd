package net.sf.drftpd;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: BytesTest.java,v 1.1 2004/01/30 14:56:08 mog Exp $
 */
public class BytesTest extends TestCase {
	public static TestSuite suite() {
		return new TestSuite(BytesTest.class);
	}

	public BytesTest() {
		super();
	}

	public BytesTest(String fName) {
		super(fName);
	}


	protected void setUp() throws Exception {
		super.setUp();
	}

	protected void tearDown() throws Exception {
		super.tearDown();
	}
	public void testFormat50KB() {
		assertEquals("50KB", Bytes.formatBytes(Bytes.KILO*50, false));
	}
	public void testFormat50KiB() {
		assertEquals("50KiB", Bytes.formatBytes(Bytes.KIBI*50, true));
	}
	
	public void testFormatByte() {
		assertEquals("123B", Bytes.formatBytes(123, false));
	}
	public void testFormatGB() {
		assertEquals("1GB", Bytes.formatBytes(Bytes.GIGA, false));
	}
	public void testFormatGiB() {
		assertEquals("1GiB", Bytes.formatBytes(Bytes.GIBI, true));
	}
	public void testFormatKB() {
		assertEquals("1KB", Bytes.formatBytes(Bytes.KILO, false));
	}
	public void testFormatKiB() {
		assertEquals("1KiB", Bytes.formatBytes(Bytes.KIBI, true));
	}

	public void testFormatMB() {
		assertEquals("1MB", Bytes.formatBytes(Bytes.MEGA, false));
	}
	
	public void testFormatMib() {
		assertEquals("1MiB", Bytes.formatBytes(Bytes.MEBI, true));
	}
	public void testFormatTB() {
		assertEquals("1TB", Bytes.formatBytes(Bytes.TERRA, false));
	}
	public void testFormatTiB() {
		assertEquals("1TiB", Bytes.formatBytes(Bytes.TEBI, true));
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
