package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collections;

import org.apache.log4j.BasicConfigurator;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java,v 1.1 2004/01/05 00:14:20 mog Exp $
 */
public class LinkedRemoteFileTest extends TestCase {
	public static TestSuite suite() {
		return new TestSuite(LinkedRemoteFileTest.class);
	}

	public LinkedRemoteFileTest(String fName) {
		super(fName);
	}
	
	public void setUp() {
		BasicConfigurator.configure();
	}
	
	private LinkedRemoteFile root;
	public void testEmptyRoot() throws FileNotFoundException {
		root = new LinkedRemoteFile(null);
		assertEquals(0, root.length());

		root.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"test1",
				null,
				null,
				1000,
				System.currentTimeMillis()));

		assertEquals(1000, root.length());

		root.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"test2",
				null,
				null,
				10000,
				System.currentTimeMillis()));

		assertEquals(11000, root.length());
		
		root.getFile("test1").delete();
		
		assertEquals(10000, root.length());
	}
}
