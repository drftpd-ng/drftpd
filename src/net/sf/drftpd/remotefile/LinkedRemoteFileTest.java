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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.BasicConfigurator;

import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java,v 1.3 2004/06/06 21:33:47 zubov Exp $
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
		public FC(Properties cfg,String cfgFileName,ConnectionManager cm) {
			
		}
	}
	
	public void testRemerge() throws IOException {
		Properties p = new Properties();
		FC cfg = new FC(p,null,null);
		RemoteSlave slave1 = new RemoteSlave("slave1",Collections.EMPTY_LIST);
		RemoteSlave slave2 = new RemoteSlave("slave2",Collections.EMPTY_LIST);
		List slave1List = new ArrayList();
		slave1List.add(slave1);
		List slave2List = new ArrayList();
		slave2List.add(slave2);
		List slaveBothList = new ArrayList();
		slaveBothList.add(slave1);
		slaveBothList.add(slave2);
		LinkedRemoteFile masterroot = new LinkedRemoteFile(cfg);
		masterroot.addFile(new StaticRemoteFile(slaveBothList,"conflicttest",null,null,1000,System.currentTimeMillis()));
		masterroot.addFile(new StaticRemoteFile(slave1List,"addslavetest",null,null,1000,System.currentTimeMillis()));
		masterroot.addFile(new StaticRemoteFile(slaveBothList,"removeslavetest",null,null,1000,System.currentTimeMillis()));
		masterroot.addFile(new StaticRemoteFile(slave2List,"removefile",null,null,1000,System.currentTimeMillis()));
		masterroot.addFile(new StaticRemoteFile(null,"dirtest",null,null,0,System.currentTimeMillis()));
		LinkedRemoteFileInterface masterdir = masterroot.getFile("dirtest");
		masterdir.addFile(new StaticRemoteFile(slaveBothList, "testfileindir", null, null, 1000, System.currentTimeMillis()));
		
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(new StaticRemoteFile(slave2List,"conflicttest",null,null,1001,System.currentTimeMillis()));
		slaveroot.addFile(new StaticRemoteFile(slave2List,"addslavetest",null,null,1000,System.currentTimeMillis()));
		slaveroot.addFile(new StaticRemoteFile(null,"dirtest",null,null,0,System.currentTimeMillis()));
		LinkedRemoteFileInterface slavedir = slaveroot.getFile("dirtest");
		slavedir.addFile(new StaticRemoteFile(slave2List, "testfileindir", null, null, 1000, System.currentTimeMillis()));
		
		masterroot.remerge(slaveroot,slave2);
		
		assertNotNull(masterroot.getFile("conflicttest"));
		assertNotNull(masterroot.getFile("conflicttest.slave2.conflict"));
		LinkedRemoteFileInterface testfile = masterroot.getFile("addslavetest");
		System.out.println("testfile = " + testfile);
		assertTrue(testfile.getSlaves().containsAll(slaveBothList));
		testfile = masterroot.getFile("removeslavetest");
		assertFalse(testfile.getSlaves().contains(slave2));
		assertFalse(masterroot.hasFile("removefile"));
		testfile = masterdir.getFile("testfileindir");
		assertTrue(testfile.getSlaves().contains(slave2));

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
