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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import junit.framework.TestCase;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;

import org.drftpd.remotefile.AbstractLinkedRemoteFile;

/**
 * @author zubov
 * @version $Id: JobManagerTest.java,v 1.10 2004/07/09 17:08:38 zubov Exp $
 */
public class JobManagerTest extends TestCase {

	private Properties p;
	LinkedRemoteFilePath file2;
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
	public void loadJobManager() {
		if (_jm != null)
			return; // already loaded
		Properties p = new Properties();
		p.put("sleepSeconds", "1000");
		_jm = new JobManager(this, p);
		_jm.startJobs();
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
	}
	public void setUp() {
		p = new Properties();
		cm = new CM(p);
		cm.loadJobManager();
		jm = cm.getJobManager();
		file = new LinkedRemoteFilePath("/path/file1.txt");
		file.addSlave(rslave1);
		file2 = new LinkedRemoteFilePath("/path/file2.txt");
		file2.addSlave(rslave2);
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

		public void deleteOthers(Set destSlaves) {
			// TODO Auto-generated method stub
		}
	}

	/*
	 * Test for Job getNextJob(List)
	 */
	public void testGetNextJobList() {
		HashSet slaveSet = new HashSet();
		slaveSet.add(rslave1);
		slaveSet.add(rslave2);
		slaveSet.add(rslave3);
		Job job = new Job(file, slaveSet, 0,slaveSet.size());
		jm.addJobToWaitingQueue(job);
		Set usedSlaveList = new HashSet();
		Set skipJobs = new HashSet();
		assertSame(job, jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.add(job);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
		Job job2 = new Job(file2, slaveSet, 5, 2);
		jm.addJobToWaitingQueue(job2);
		assertSame(job2, jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.add(job2);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
		skipJobs.clear();
		usedSlaveList.add(rslave1);
		usedSlaveList.add(rslave2);
		usedSlaveList.add(rslave3);
		assertNull(jm.getNextJob(usedSlaveList, skipJobs));
	}
}
