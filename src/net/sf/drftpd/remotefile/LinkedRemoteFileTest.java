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
package net.sf.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.util.Collections;

import org.apache.log4j.BasicConfigurator;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java,v 1.2 2004/02/10 00:03:15 mog Exp $
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
