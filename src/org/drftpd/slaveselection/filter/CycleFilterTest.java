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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.Transfer;

import junit.framework.TestCase;

/*
 * @author zubov
 * @version $Id
 */
public class CycleFilterTest extends TestCase {

	/**
	 * Constructor for CycleFilterTest.
	 * @param arg0
	 */
	public CycleFilterTest(String arg0) {
		super(arg0);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(CycleFilterTest.class);
	}

	public void testProcess() throws NoAvailableSlaveException, ObjectNotFoundException {
		RemoteSlave rslaves[] =
			{
				new RemoteSlave("slave1", Collections.EMPTY_LIST),
				new RemoteSlave("slave2", Collections.EMPTY_LIST),
				new RemoteSlave("slave3", Collections.EMPTY_LIST)};
		ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
		Filter f = new CycleFilter(null,0,null);
		f.process(sc, null, null,Transfer.TRANSFER_SENDING_DOWNLOAD, null);
		assertEquals(1,sc.getSlaveScore(rslaves[0]).getScore());
		assertEquals(0,sc.getSlaveScore(rslaves[1]).getScore());
		assertEquals(0,sc.getSlaveScore(rslaves[2]).getScore());
	}
}
