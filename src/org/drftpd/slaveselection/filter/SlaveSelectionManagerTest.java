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

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.BasicConfigurator;

/**
 * @author mog
 * @version $Id: SlaveSelectionManagerTest.java,v 1.1 2004/02/26 13:56:53 mog Exp $
 */
public class SlaveSelectionManagerTest extends TestCase {

	public SlaveSelectionManagerTest(String fName) {
		super(fName);
	}

	public static TestSuite suite() {
		return new TestSuite(SlaveSelectionManagerTest.class);
	}

	public void testEmptyFail() {
		Properties p = new Properties();
		try {
			new SlaveSelectionManager(null, p);
			fail();
		} catch (IllegalArgumentException pass) {
		}
	}

	public void testBandwidth() {
		Properties p = new Properties();
		p.put("1.filter", "bandwidth");
		//p.put("1.expr", "*");
		p.put("1.multiplier", "1");
		SlaveSelectionManager ssm = new SlaveSelectionManager(null, p);
		RemoteSlave rslaves[] =
			{
				new RemoteSlave("slave1", Collections.EMPTY_LIST),
				new RemoteSlave("slave2", Collections.EMPTY_LIST)};

		try {
			ssm.getASlave(
				Arrays.asList(rslaves),
				Transfer.TRANSFER_SENDING_DOWNLOAD,
				null,
				new MatchdirFilterTest.LinkedRemoteFilePath(
					"/blabla/file.txt"));
			fail(); // no slaves are online
		} catch (NoAvailableSlaveException pass) {
		}
	}
	public void setUp() {
		BasicConfigurator.configure();
	}
}
