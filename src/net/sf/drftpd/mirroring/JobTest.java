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
package net.sf.drftpd.mirroring;

import java.util.ArrayList;

import net.sf.drftpd.master.RemoteSlave;

import junit.framework.TestCase;

/*
 * @author zubov
 * @version $Id: JobTest.java,v 1.3 2004/05/18 18:16:17 zubov Exp $
 */
public class JobTest extends TestCase {

	/**
	 * Constructor for JobTest.
	 * @param arg0
	 */
	public JobTest(String arg0) {
		super(arg0);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(JobTest.class);
	}
	
	public void testRemoveDestinationSlave() {
		ArrayList slaveSet = new ArrayList();
		slaveSet.add(null);
		slaveSet.add(null);
		RemoteSlave rslave = new RemoteSlave("name",null);
		slaveSet.add(rslave);
		Job job = new Job(null,slaveSet,null,null,0);
		assertTrue(job.removeDestinationSlave(null));
		assertTrue(job.removeDestinationSlave(rslave));
		assertEquals(1,slaveSet.size());
	}

}
