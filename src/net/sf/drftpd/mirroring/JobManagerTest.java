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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

import org.drftpd.remotefile.AbstractLinkedRemoteFile;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import junit.framework.TestCase;

/*
 * @author zubov
 * @version $Id
 */
public class JobManagerTest extends TestCase {

	private Properties p;
	LinkedRemoteFilePath root;
	LinkedRemoteFilePath file;
	/**
	 * Constructor for JobManagerTest.
	 * @param arg0
	 */
	ConnectionManager cm;
	JobManager jm;
	class CM extends ConnectionManager {
		CM(Properties p) {
			p.put("master.bindname", "slavemanager");
			p.put("master.bindport", "1099");
			p.put("master.port", "2121");
			p.put("master.localslave", "false");
			p.put(
				"master.usermanager",
				"net.sf.drftpd.master.usermanager.jsx.JSXUserManager");
			p.put(
				"slaveselection",
				"org.drftpd.slaveselection.def.SlaveSelectionManager");
			p.put("sectionmanager", "org.drftpd.sections.conf.SectionManager");
			p.put("use.ident", "true");
		}
	}
	public JobManagerTest(String arg0) throws IOException {
		super(arg0);
		p = new Properties();
		cm = new CM(p);
		cm.loadJobManager();
		jm = cm.getJobManager();
		FtpConfig cfg = new FtpConfig(p,"drftpd.conf",cm);
		file = new LinkedRemoteFilePath("/path/file1.txt");
		file.addSlave(new RemoteSlave("slave1",new ArrayList()));
		root = new LinkedRemoteFilePath("/path/file2.txt");

	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(JobManagerTest.class);
	}
	
	public static class LinkedRemoteFilePath extends AbstractLinkedRemoteFile {
		private String _path;
		private boolean isDeleted = false;
		private ArrayList slaves = new ArrayList();
		public LinkedRemoteFilePath(String path) {
			_path = path;
		}
		public String getPath() {
			return _path;
		}
		
		/**
		 *
		 */

		public void addSlave(RemoteSlave slave) {
			slaves.add(slave);
		}

		/**
		 *
		 */

		public void delete() {
			isDeleted = true;
		}

		/**
		 *
		 */

		public Collection getAvailableSlaves()
			throws NoAvailableSlaveException {
			if (getSlaves().isEmpty()) throw new NoAvailableSlaveException();
			return getSlaves();
		}

		/**
		 *
		 */

		public Collection getSlaves() {
			return slaves;
		}

		/**
		 *
		 */

		public boolean isAvailable() {
			return true;
		}

		/**
		 *
		 */

		public boolean isDeleted() {
			return isDeleted;
		}

		/**
		 *
		 */

		public boolean removeSlave(RemoteSlave slave) {
			return slaves.remove(slave);
		}

	}

	public void testAddJob() {
		int sizebefore = jm.getAllJobs().size();
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file,slaveList,null,null,0);
		jm.addJob(job);
		assertEquals(sizebefore, jm.getAllJobs().size() - 1);
		jm.addJob(job);
		assertEquals(jm.getAllJobs().size(),jm.getAllJobs().size());
	}

	/*
	 * Test for Job getNextJob(List)
	 */
	public void testGetNextJobList() {
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file,slaveList,null,null,0);
		jm.addJob(job);
		ArrayList usedSlaveList = new ArrayList();
		assertSame(job,jm.getNextJob(usedSlaveList));
	}

	public void testRemoveJob() {
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file,slaveList,null,null,0);
		int sizebefore = jm.getAllJobs().size();
		jm.addJob(job);
		assertEquals(sizebefore+1,jm.getAllJobs().size());
		jm.removeJob(job);
		assertEquals(sizebefore,jm.getAllJobs().size());
	}
}
