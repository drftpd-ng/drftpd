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
package net.sf.drftpd.master;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import de.hampelratte.id3.ID3v1Tag;

/**
 * @author mog
 * @version $Id: RemoteSlaveTest.java,v 1.6 2004/07/29 17:39:04 zubov Exp $
 */
public class RemoteSlaveTest extends TestCase {
	public static TestSuite suite() {
		return new TestSuite(RemoteSlaveTest.class);
	}

	public RemoteSlaveTest(String fName) {
		super(fName);
	}

	public void testEquals() {
		RemoteSlave rslave1 = new RemoteSlave("test1",null);
		RemoteSlave rslave2 = new RemoteSlave("test1",null);
		assertTrue(rslave1.equals(rslave1));
		assertTrue(rslave1.equals(rslave2));
	}
	public class SlaveImpl implements Slave {

		private HashSet _filelist;
		public SlaveImpl(HashSet filelist) {
			_filelist = filelist;
		}

		public long checkSum(String path) throws RemoteException, IOException {
			throw new NoSuchMethodError();
		}

		public Transfer listen(boolean encrypted)
			throws RemoteException, IOException {
			throw new NoSuchMethodError();
		}

		public Transfer connect(InetSocketAddress addr, boolean encrypted)
			throws RemoteException {
			throw new NoSuchMethodError();
		}

		public SlaveStatus getSlaveStatus() throws RemoteException {
			throw new NoSuchMethodError();
		}

		public void ping() throws RemoteException {
		}

		public SFVFile getSFVFile(String path)
			throws RemoteException, IOException {
			throw new NoSuchMethodError();
		}

		public ID3v1Tag getID3v1Tag(String path)
			throws RemoteException, IOException {
			throw new NoSuchMethodError();
		}

		public void rename(String from, String toDirPath, String toName)
			throws RemoteException, IOException {
			_filelist.remove(from);
			_filelist.add(new String(toDirPath + "/" + toName));
		}

		public void delete(String path) throws RemoteException, IOException {
			_filelist.remove(path);
		}

		public LinkedRemoteFile getSlaveRoot() throws IOException {
			throw new NoSuchMethodError();
		}
	}

	public void testSetSlave() throws IOException {
		RemoteSlave rslave = new RemoteSlave("test",null);
		rslave.deleteFile("/deleteme");
		rslave.rename("/renameme", "/indir", "tofile");
		List list = new ArrayList();
		list.add(rslave);
		HashSet filelist = new HashSet();
		filelist.add("/deleteme");
		filelist.add("/renameme");
		filelist.add("/indir");
		Slave slave = new SlaveImpl(filelist);
		rslave.setSlave(slave, null, null, 256);
		assertFalse(filelist.contains("/deleteme"));
		assertFalse(filelist.contains("/renameme"));
		assertTrue(filelist.contains("/indir"));
		assertTrue(filelist.contains("/indir/tofile"));
	}
}
