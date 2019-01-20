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

import junit.framework.TestCase;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.DiskStatus;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyRemoteSlave;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class BandwidthFilterTest extends TestCase {
	public void testBandwidth() throws NoAvailableSlaveException, ObjectNotFoundException {
		Properties p = new Properties();
		p.put("1.multiplier", "3");
		
		Filter f = new BandwidthFilter(1, p);
		
		SlaveStatus status = new SlaveStatus(new DiskStatus(0, 0), 0, 0, 100, 0, 100, 0);
		RemoteSlave[] list = { new RS("slave1", status) };
		ScoreChart sc = new ScoreChart(Arrays.asList(list));
		
		f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);
		
		Assert.assertEquals(-300, sc.getScoreForSlave(list[0]).getScore());
	}
	
	static class RS extends DummyRemoteSlave {
		private SlaveStatus _status;

		public RS(String name, SlaveStatus status) {
			super(name);
			_status = status;
		}
		
        public SlaveStatus getSlaveStatusAvailable() {
            return _status;
        }
	}
}
