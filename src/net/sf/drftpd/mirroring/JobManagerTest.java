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
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import org.drftpd.remotefile.AbstractLinkedRemoteFile;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import junit.framework.TestCase;

/**
 * @author zubov
 * @version $Id: JobManagerTest.java,v 1.4 2004/04/01 05:29:41 zubov Exp $
 */
public class JobManagerTest extends TestCase {

	private Properties p;
	LinkedRemoteFilePath root;
	LinkedRemoteFilePath file;
	static RemoteSlave rslave1 = new RemoteSlave("slave1", new ArrayList());
	static RemoteSlave rslave2 = new RemoteSlave("slave2", new ArrayList());
	static RemoteSlave rslave3 = new RemoteSlave("slave3", new ArrayList());
	static ArrayList slaveList = new ArrayList();
	static {
		slaveList.add(rslave1);
		slaveList.add(rslave2);
		slaveList.add(rslave3);
	}
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
			try {
				sm = new SM();
			} catch (RemoteException e) {
			}
		}
		SM sm;
		public SlaveManagerImpl getSlaveManager() {
			return sm;
		}

	}

	class SM extends SlaveManagerImpl {
		protected SM() throws RemoteException {
		}

		public Collection getAvailableSlaves()
			throws NoAvailableSlaveException {
			return slaveList;
		}

		public Collection getSlaves() {
			return slaveList;
		}

	}
	public JobManagerTest(String arg0) throws IOException {
		super(arg0);
		p = new Properties();
		cm = new CM(p);
		cm.loadJobManager();
		jm = cm.getJobManager();
		file = new LinkedRemoteFilePath("/path/file1.txt");
		file.addSlave(rslave1);
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

		public void addSlave(RemoteSlave slave) {
			slaves.add(slave);
		}

		public void delete() {
			isDeleted = true;
		}

		public Collection getAvailableSlaves()
			throws NoAvailableSlaveException {
			if (getSlaves().isEmpty())
				throw new NoAvailableSlaveException();
			return getSlaves();
		}

		public Collection getSlaves() {
			return slaves;
		}

		public boolean isAvailable() {
			return true;
		}

		public boolean isDeleted() {
			return isDeleted;
		}

		public boolean removeSlave(RemoteSlave slave) {
			return slaves.remove(slave);
		}

		public String toString() {
			String string = "[file=" + getPath() + "][availableSlaves[";
			for (Iterator iter = this.getSlaves().iterator();
				iter.hasNext();
				) {
				RemoteSlave rslave = (RemoteSlave) iter.next();
				string = string + rslave + ",";
			}
			return string + "]]";
		}

		public String getName() {
			return _path;
		}

	}

	public void testAddJob() {
		int sizebefore = jm.getAllJobs().size();
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file, slaveList, null, null, 0);
		jm.addJob(job);
		assertEquals(sizebefore, jm.getAllJobs().size() - 1);
		jm.addJob(job);
		assertEquals(jm.getAllJobs().size(), jm.getAllJobs().size());
	}

	/*
	 * Test for Job getNextJob(List)
	 */
	public void testGetNextJobList() {
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file, slaveList, null, null, 0);
		jm.addJob(job);
		ArrayList usedSlaveList = new ArrayList();
		ArrayList skipJobs = new ArrayList();
		assertSame(job, jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.add(job);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.clear();
		slaveList.clear();
		slaveList.add(rslave2);
		jm.removeJob(job);
		job = new Job(file, slaveList, null, null, 5);
		jm.addJob(job);
		assertSame(job, jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.add(job);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.clear();
		usedSlaveList.add(rslave1);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
	}

	public void testRemoveJob() {
		ArrayList slaveList = new ArrayList();
		slaveList.add(null);
		slaveList.add(null);
		Job job = new Job(file, slaveList, null, null, 0);
		int sizebefore = jm.getAllJobs().size();
		jm.addJob(job);
		assertEquals(sizebefore + 1, jm.getAllJobs().size());
		jm.removeJob(job);
		assertEquals(sizebefore, jm.getAllJobs().size());
	}
}
