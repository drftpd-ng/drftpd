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
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java,v 1.4 2004/06/09 10:56:39 mog Exp $
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

	public static class FC extends FtpConfig {
		public FC() {
		}
	}

	public void testRemerge() throws IOException {
		FC cfg = new FC();
		RemoteSlave slave1 = new RemoteSlave("slave1", Collections.EMPTY_LIST);
		RemoteSlave slave2 = new RemoteSlave("slave2", Collections.EMPTY_LIST);
		List slave1List = new ArrayList();
		slave1List.add(slave1);
		List slave2List = new ArrayList();
		slave2List.add(slave2);
		List slaveBothList = new ArrayList();
		slaveBothList.add(slave1);
		slaveBothList.add(slave2);
		_root = new LinkedRemoteFile(cfg);

		// build like files.mlst does
		buildRoot(_root, slaveBothList, slave1List, slave2List);

		// remerge slave 1
		remergeSlave1(_root, slave1);
		// remerge slave 2
		remergeSlave2(_root, slave2);

		{
			assertNotNull(_root.getFile("conflicttest"));
			assertNotNull(_root.getFile("conflicttest.slave2.conflict"));
			LinkedRemoteFileInterface testfile = _root.getFile("addslavetest");
			System.out.println("testfile = " + testfile);
			assertTrue(testfile.getSlaves().containsAll(slaveBothList));
			testfile = _root.getFile("removeslavetest");
			assertFalse(testfile.getSlaves().contains(slave2));
			assertFalse(_root.hasFile("removefile"));
			LinkedRemoteFileInterface masterdir = _root.getFile("dirtest");
			testfile = masterdir.getFile("testfileindir");
			assertTrue(testfile.getSlaves().contains(slave2));
		}
		System.out.println("serialize");
		MLSTSerialize.serialize(_root, new PrintWriter(System.out, true));
		System.out.println("/serialize");
	}

	/**
	 * @param _root
	 * @param slave2
	 */
	private static void remergeSlave2(
		LinkedRemoteFile root,
		RemoteSlave slave2)
		throws IOException {
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "conflicttest", 1001));
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "addslavetest", 1000));
		slaveroot.addFile(new StaticRemoteFile(null, "dirtest", 0));
		LinkedRemoteFileInterface slavedir = slaveroot.getFile("dirtest");
		slavedir.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"testfileindir",
				1000));
		root.remerge(slaveroot, slave2);
	}

	private static void buildRoot(
		LinkedRemoteFile root,
		List slaveBothList,
		List slave1List,
		List slave2List)
		throws FileNotFoundException {
		root.addFile(new StaticRemoteFile(slaveBothList, "conflicttest", 1000));
		root.addFile(new StaticRemoteFile(slave1List, "addslavetest", 1000));
		root.addFile(
			new StaticRemoteFile(slaveBothList, "removeslavetest", 1000));
		root.addFile(new StaticRemoteFile(slave2List, "removefile", 1000));
		root.addFile(new StaticRemoteFile(null, "dirtest", 0));
		LinkedRemoteFileInterface masterdir = root.getFile("dirtest");
		masterdir.addFile(
			new StaticRemoteFile(slaveBothList, "testfileindir", 1000));
	}

	private static void remergeSlave1(
		LinkedRemoteFile masterroot,
		RemoteSlave slave1)
		throws IOException {
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "conflicttest", 1000));
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "addslavetest", 1000));
		slaveroot.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"removeslavetest",
				1000));
		masterroot.remerge(slaveroot, slave1);
	}

	private LinkedRemoteFile _root;
	public void testEmptyRoot() throws FileNotFoundException {
		_root = new LinkedRemoteFile(null);
		assertEquals(0, _root.length());

		_root.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "test1", 1000));

		assertEquals(1000, _root.length());

		_root.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "test2", 10000));

		assertEquals(11000, _root.length());

		_root.getFile("test1").delete();

		assertEquals(10000, _root.length());
	}
}
