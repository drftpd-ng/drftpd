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
package org.drftpd.slaveselection.filter;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.drftpd.remotefile.AbstractLinkedRemoteFile;

/*
 * @author zubov
 * @version $Id
 */
public class MaxbandwidthFilterTest extends TestCase {
	public static class LinkedRemoteFilePath extends AbstractLinkedRemoteFile {
		private String _path;
		public LinkedRemoteFilePath(String path) {
			_path = path;
		}
		public String getPath() {
			return _path;
		}
		public void deleteOthers(Set destSlaves) {
		}
	}
	/**
	 * Constructor for MaxbandwidthFilterTest.
	 * @param arg0
	 */
	public MaxbandwidthFilterTest(String arg0) {
		super(arg0);
	}

	public class FC extends FilterChain {
		public SlaveManagerImpl getSlaveManager() {
			try {
				return new SM();
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public class SM extends SlaveManagerImpl {
		public SM() throws RemoteException {
			super();
		}
		public RemoteSlave getSlave(String s) throws ObjectNotFoundException {
			if(s == null) throw new RuntimeException();
			if(rslaves[0] == null) throw new RuntimeException();
			if (s.equals(rslaves[0].getName()))
				return rslaves[0];
			if (s.equals(rslaves[1].getName()))
				return rslaves[1];
			throw new ObjectNotFoundException();
		}

	}
	
	public class RS extends RemoteSlave {
		public RS(String name,Collection duh) {
			super(name,null);
		}

		public SlaveStatus getStatusAvailable() {
			if (getName().equals("slave2")) {
				return new SlaveStatus(0,0,0,0,0,0,0,0);
			}
			return new SlaveStatus(0,0,0,0,9999999,1,9999999,1);
		}
	}

	RemoteSlave rslaves[] =
		{
			new RS("slave1", Collections.EMPTY_LIST),
			new RS("slave2", Collections.EMPTY_LIST)};

	public static void main(String[] args) {
		junit.textui.TestRunner.run(MaxbandwidthFilterTest.class);
	}

	public void testSimple() throws ObjectNotFoundException, NoAvailableSlaveException {
		Properties p = new Properties();
		p.put("1.maxbandwidth", "800kb");
		Filter f = new MaxbandwidthFilter(new FC(), 1, p);
		ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

		f.process(sc, null, null,Transfer.TRANSFER_SENDING_DOWNLOAD, new LinkedRemoteFilePath("/"));
		assertEquals(sc.getBestSlave(),rslaves[1]);
	}
}
