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
import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.BasicConfigurator;
import de.hampelratte.id3.ID3v1Tag;

/**
 * @author mog
 * @version $Id: LinkedRemoteFileTest.java,v 1.7 2004/07/14 12:52:00 teflon114 Exp $
 */
public class LinkedRemoteFileTest extends TestCase {
	public static class FC extends FtpConfig {
		public FC() {
		}
	}
	public static class RS extends RemoteSlave {

		public RS(String string, List list) {
			super(string, list);
		}
		public Slave getSlave() {
			return new Slave() {
				public long checkSum(String path)
					throws RemoteException, IOException {
					throw new NoSuchMethodError();
				}

				public Transfer listen(boolean encrypted)
					throws RemoteException, IOException {
					throw new NoSuchMethodError();
				}

				public Transfer connect(
					InetSocketAddress addr,
					boolean encrypted)
					throws RemoteException {
					throw new NoSuchMethodError();
				}

				public SlaveStatus getSlaveStatus() throws RemoteException {
					throw new NoSuchMethodError();
				}

				public void ping() throws RemoteException {
					throw new NoSuchMethodError();
				}

				public SFVFile getSFVFile(String path)
					throws RemoteException, IOException {
					throw new NoSuchMethodError();
				}

				public ID3v1Tag getID3v1Tag(String path)
					throws RemoteException, IOException {
					throw new NoSuchMethodError();
				}

				public void rename(
					String from,
					String toDirPath,
					String toName)
					throws RemoteException, IOException {
					// pretend we succeeded.
				}

				public void delete(String path)
					throws RemoteException, IOException {
					throw new NoSuchMethodError();
				}

				public LinkedRemoteFile getSlaveRoot() throws IOException {
					throw new NoSuchMethodError();
				}
			};
		}
	}

	private static void buildRoot(
		LinkedRemoteFile root,
		List slaveBothList,
		List slave1List,
		List slave2List)
		throws FileNotFoundException {
		root.addFile(new StaticRemoteFile(slaveBothList, "ConflictTest", 1000));
		root.addFile(new StaticRemoteFile(slave1List, "AddSlaveTest", 1000));
		root.addFile(
			new StaticRemoteFile(slaveBothList, "RemoteSlaveTest", 1000));
		root.addFile(new StaticRemoteFile(slave2List, "RemoveFile", 1000));
		root.addFile(new StaticRemoteFile(null, "DirTest", 0));
		LinkedRemoteFileInterface masterdir = root.getFile("DirTest");
		masterdir.addFile(
			new StaticRemoteFile(slaveBothList, "TestFileInDir", 1000));
	}

	private static void internalRemergeSlave1(
		LinkedRemoteFile masterroot,
		RemoteSlave slave1)
		throws IOException {
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "ConflictTest", 1000));
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "AddSlaveTest", 1000));
		slaveroot.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"RemoteSlaveTest",
				1000));
		masterroot.remerge(slaveroot, slave1);
	}

	private static void internalRemergeSlave2(
		LinkedRemoteFile root,
		RemoteSlave slave2)
		throws IOException {
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "ConflictTest", 1001));
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "AddSlaveTest", 1000));
		slaveroot.addFile(new StaticRemoteFile(null, "DirTest", 0));
		LinkedRemoteFileInterface slavedir = slaveroot.getFile("DirTest");
		slavedir.addFile(
			new StaticRemoteFile(
				Collections.EMPTY_LIST,
				"TestFileInDir",
				1000));
		root.remerge(slaveroot, slave2);
	}

	private LinkedRemoteFile _root;

	private RS _slave1;

	private RS _slave2;

	public LinkedRemoteFileTest(String fName) {
		super(fName);
	}
	private void internalSetUp() {
		_slave1 = new RS("slave1", Collections.EMPTY_LIST);
		_slave2 = new RS("slave2", Collections.EMPTY_LIST);
		FC cfg = new FC();
		_root = new LinkedRemoteFile(cfg);
	}

	public void setUp() {
		BasicConfigurator.configure();
	}

	public void testAddSlave() throws IOException {
		internalSetUp();
		List bothSlaves = Arrays.asList(new RemoteSlave[] { _slave1, _slave2 });

		//file2 = _root.addFile(file);
		LinkedRemoteFile slaveroot = new LinkedRemoteFile(null);
		slaveroot.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "AddSlaveTest", 1000));
		slaveroot.getFile("AddSlaveTest");
		_root.remerge(slaveroot, _slave1);
		_root.getFile("AddSlaveTest");
		_root.remerge(slaveroot, _slave2);
		LinkedRemoteFileInterface file2 = _root.getFile("AddSlaveTest");
		assertEquals(file2, _root.getFile(file2.getName()));
		assertEquals(bothSlaves, _root.getFile(file2.getName()).getSlaves());
		System.out.println(file2);
	}
	public void testEmptyRoot() throws FileNotFoundException {
		_root = new LinkedRemoteFile(null);
		assertEquals(0, _root.length());

		_root.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "Test1", 1000));

		assertEquals(1000, _root.length());

		_root.addFile(
			new StaticRemoteFile(Collections.EMPTY_LIST, "Test2", 10000));

		assertEquals(11000, _root.length());

		_root.getFile("Test1").delete();

		assertEquals(10000, _root.length());
	}
	public void testRemerge() throws IOException {
		internalSetUp();
		//		List slaveBothList = new ArrayList();
		//		slaveBothList.add(_slave1);
		//		slaveBothList.add(_slave2);
		List slaveBothList =
			Arrays.asList(new RemoteSlave[] { _slave1, _slave2 });

		// build like files.mlst does
		buildRoot(
			_root,
			slaveBothList,
			Collections.singletonList(_slave1),
			Collections.singletonList(_slave2));

		// remerge slave 1
		internalRemergeSlave1(_root, _slave1);
		assertEquals(
			Collections.singletonList(_slave1),
			_root.getFile("AddSlaveTest").getSlaves());
		// remerge slave 2
		internalRemergeSlave2(_root, _slave2);

		{
			assertNotNull(_root.getFile("ConflictTest"));
			assertNotNull(_root.getFile("ConflictTest.slave2.conflict"));
			assertEquals(
				slaveBothList,
				_root.getFile("AddSlaveTest").getSlaves());
			assertFalse(
				_root.getFile("RemoteSlaveTest").getSlaves().contains(_slave2));

			try {
				LinkedRemoteFileInterface file = _root.getFile("RemoveFile");
				throw new AssertionFailedError(
					file.toString() + " should be deleted");
			} catch (FileNotFoundException success) {
			}

			LinkedRemoteFileInterface masterdir = _root.getFile("DirTest");
			assertTrue(
				masterdir.getFile("TestFileInDir").getSlaves().contains(
					_slave2));
		}
	}
}
