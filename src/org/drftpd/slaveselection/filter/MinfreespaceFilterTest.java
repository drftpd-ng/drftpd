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

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.Bytes;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

/**
 * @author mog
 * @version $Id: MinfreespaceFilterTest.java,v 1.2 2004/02/26 21:11:08 mog Exp $
 */
public class MinfreespaceFilterTest extends TestCase {
	public static class RemoteSlaveTesting extends RemoteSlave {
		private SlaveStatus _status;

		public RemoteSlaveTesting(String name, Collection masks, SlaveStatus status) {
			super(name, masks);
			_status = status;
		}
		
		public SlaveStatus getStatus()
			throws RemoteException, NoAvailableSlaveException {
			return _status;
		}
	}

	public MinfreespaceFilterTest(String fName) {
		super(fName);
	}
	
	public static TestSuite suite() {
		return new TestSuite(MinfreespaceFilterTest.class);
	}
	
	public void testSimple() throws ObjectNotFoundException, NoAvailableSlaveException {
		Properties p = new Properties();
		p.put("1.remove", "1");
		p.put("1.minfreespace", "100GB");

		SlaveStatus s = new SlaveStatus(Bytes.parseBytes("50GB"), Bytes.parseBytes("1000GB"), 0, 0, 0, 0, 0,0);
		RemoteSlave rslaves[] =
			{ new RemoteSlaveTesting("slave1", Collections.EMPTY_LIST, s)};
		ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));


		Filter f = new MinfreespaceFilter(null, 1, p);
		f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null);

		assertEquals(-1, sc.getSlaveScore(rslaves[0]).getScore());
	}
}
