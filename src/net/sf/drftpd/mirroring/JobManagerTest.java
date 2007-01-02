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
import net.sf.drftpd.master.SlaveFileException;

import org.drftpd.master.ConnectionManager;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.CaseInsensitiveHashtable;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;
import org.drftpd.tests.DummySlaveSelectionManager;
import org.drftpd.vfs.FileHandle;


/**
 * @author zubov
 * @version $Id$
 */
public class JobManagerTest extends TestCase {
    private Properties p;
    JobtestFileHandle file2;
    JobtestFileHandle file;

    /**
     * Constructor for JobManagerTest.
     * @param arg0
     */
    ConnectionManager _cm;
    JobManager _jm;
    private ArrayList<RemoteSlave> _slaveList;

    public JobManagerTest(String arg0) throws IOException {
        super(arg0);
    }

    public void setUp() throws RemoteException, SlaveFileException {
        DummyGlobalContext dgc = new DummyGlobalContext();
        //dgc.setSlaveManager(new DummySlaveManager(dgc));

        DummyRemoteSlave rslave1 = new DummyRemoteSlave("slave1", dgc);
        DummyRemoteSlave rslave2 = new DummyRemoteSlave("slave2", dgc);
        DummyRemoteSlave rslave3 = new DummyRemoteSlave("slave3", dgc);
        _slaveList = new ArrayList<RemoteSlave>();
        _slaveList.add(rslave1);
        _slaveList.add(rslave2);
        _slaveList.add(rslave3);
        p = new Properties();
        _cm = new CM(p);

        // TODO I broke the test! -zubov
        //_cm.setGlobalContext(dgc);
        //dgc.setConnectionManager(_cm);

        DummySlaveManager dsm = null;

        dsm = new DummySlaveManager(dgc);

        dgc.setSlaveManager(dsm);

        DummySlaveSelectionManager dssm = new DummySlaveSelectionManager();
        dgc.setSlaveSelectionManager(dssm);
        _jm = _cm.getGlobalContext().getJobManager();
        file = new JobtestFileHandle("/path/file1.txt");
        file.addSlave(rslave1);
        file2 = new JobtestFileHandle("/path/file2.txt");
        file2.addSlave(rslave2);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(JobManagerTest.class);
    }

    /*
     * Test for Job getNextJob(List)
     */
    public void testGetNextJobList() {
        HashSet slaveSet = new HashSet(_slaveList);
        Job job = new Job(file, slaveSet, 0, slaveSet.size());
        _jm.addJobToQueue(job);

        Set usedSlaveList = new HashSet();
        Set skipJobs = new HashSet();
        assertSame(job, _jm.getNextJob(usedSlaveList, skipJobs));
        skipJobs.add(job);
        assertNull(_jm.getNextJob(usedSlaveList, skipJobs));

        Job job2 = new Job(file2, slaveSet, 5, 2);
        _jm.addJobToQueue(job2);
        assertSame(job2, _jm.getNextJob(usedSlaveList, skipJobs));
        skipJobs.add(job2);
        assertNull(_jm.getNextJob(usedSlaveList, skipJobs));
        skipJobs.clear();
        usedSlaveList.addAll(_slaveList);
        assertNull(_jm.getNextJob(usedSlaveList, skipJobs));
    }

    class CM extends ConnectionManager {
        CM(Properties p) {
            p.put("master.bindname", "slavemanager");
            p.put("master.bindport", "1099");
            p.put("master.port", "2121");
            p.put("master.localslave", "false");
            p.put("master.usermanager",
                "net.sf.drftpd.master.usermanager.jsx.JSXUserManager");
            p.put("slaveselection",
                "org.drftpd.slaveselection.def.SlaveSelectionManager");
            p.put("sectionmanager", "org.drftpd.sections.conf.SectionManager");
            p.put("use.ident", "true");
        }
    }

    public static class JobtestFileHandle extends FileHandle {
        private boolean isDeleted = false;
        private HashSet<RemoteSlave> slaves = new HashSet<RemoteSlave>();

        public JobtestFileHandle(String string) {
        	super(string);
		}

		public void addSlave(DummyRemoteSlave slave) {
            slaves.add(slave);
        }

        public void delete() {
            isDeleted = true;
        }

        public Collection<RemoteSlave> getAvailableSlaves() throws NoAvailableSlaveException {
            if (getSlaves().isEmpty()) {
                throw new NoAvailableSlaveException();
            }

            return getSlaves();
        }

        public Set<RemoteSlave> getSlaves() {
            return slaves;
        }

        public boolean isAvailable() {
            return true;
        }

        public boolean isDeleted() {
            return isDeleted;
        }

        public boolean removeSlave(DummyRemoteSlave slave) {
            return slaves.remove(slave);
        }

        public String toString() {
            String string = "[file=" + getPath() + "][availableSlaves[";

            for (Iterator iter = this.getSlaves().iterator(); iter.hasNext();) {
                DummyRemoteSlave rslave = (DummyRemoteSlave) iter.next();
                string = string + rslave + ",";
            }

            return string + "]]";
        }

        public void deleteOthers(Set destSlaves) {
            // TODO Auto-generated method stub
        }

        public void remerge(CaseInsensitiveHashtable lightRemoteFiles,
            RemoteSlave rslave) throws IOException {
            // TODO Auto-generated method stub
        }

		public boolean isValid() {
			return true;
		}
    }
}
