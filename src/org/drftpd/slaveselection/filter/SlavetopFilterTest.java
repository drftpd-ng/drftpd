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
import junit.framework.TestSuite;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.apache.log4j.BasicConfigurator;

import org.drftpd.GlobalContext;

import org.drftpd.master.RemoteTransfer;
import org.drftpd.sections.def.SectionManager;
import org.drftpd.slave.Transfer;


import org.drftpd.tests.DummyConnectionManager;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;

import java.rmi.RemoteException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: SlavetopFilterTest.java,v 1.10 2004/11/09 19:00:00 mog Exp $
 */
public class SlavetopFilterTest extends TestCase {
    private LinkedRemoteFile dir1;
    private LinkedRemoteFile root;

    public SlavetopFilterTest(String name) {
        super(name);
    }

    public static TestSuite suite() {
        return new TestSuite(SlavetopFilterTest.class);
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
    }

    public void testSimple()
        throws NoAvailableSlaveException, FileExistsException, 
            ObjectNotFoundException, RemoteException, SlaveFileException {
        Properties p = new Properties();
        p.put("1.topslaves", "2");
        p.put("1.assign", "100");

        RemoteSlave[] rslaves = {
                new DummyRemoteSlave("slave1", null),
                new DummyRemoteSlave("slave2", null),
                new DummyRemoteSlave("slave3", null)
            };

        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        root = new LinkedRemoteFile(null);
        dir1 = root.createDirectory("dir1");

        LinkedRemoteFile dir2 = dir1.createDirectory("dir2");

        dir2.addFile(new StaticRemoteFile("file1",
                Collections.singletonList(rslaves[0])));
        dir2.addFile(new StaticRemoteFile("file2",
                Collections.singletonList(rslaves[2])));
        dir2.addFile(new StaticRemoteFile("file3",
                Collections.singletonList(rslaves[0])));
        dir2.addFile(new StaticRemoteFile("file4",
                Collections.singletonList(rslaves[1])));
        dir2.addFile(new StaticRemoteFile("file5",
                Collections.singletonList(rslaves[2])));

        // these 3 shouldn't get included by SlavetopFilter, as they are directly in the section.
        dir1.addFile(new StaticRemoteFile("file6",
                Collections.singletonList(rslaves[1])));

        dir1.addFile(new StaticRemoteFile("file7",
                Collections.singletonList(rslaves[1])));

        dir1.addFile(new StaticRemoteFile("file8",
                Collections.singletonList(rslaves[1])));

        FC fc = new FC();
        DummyConnectionManager cm = new DummyConnectionManager();

        DummyGlobalContext gctx = new DummyGlobalContext();
        fc.setGlobalContext(gctx);
        gctx.setSectionManager(new SectionManager(cm));
        gctx.setConnectionManager(cm);
        gctx.setRoot(root);
        cm.setGlobalContext(gctx);

        DummySlaveManager sm = new DummySlaveManager();
        sm.setGlobalContext(gctx);
        fc.setSlaveManager(sm);

        Filter f = new SlavetopFilter(fc, 1, p);
        f.process(sc, null, null, Transfer.TRANSFER_UNKNOWN, dir2);
        assertEquals(100, sc.getSlaveScore(rslaves[0]).getScore());
        assertEquals(0, sc.getSlaveScore(rslaves[1]).getScore());
        assertEquals(100, sc.getSlaveScore(rslaves[2]).getScore());
    }

    public class FC extends FilterChain {
        private DummySlaveManager _slavem;
        private DummyGlobalContext _dgctx;

        public SlaveManager getSlaveManager() {
            return _slavem;
        }

        public void setSlaveManager(DummySlaveManager sm) {
            _slavem = sm;
        }

        public void setGlobalContext(DummyGlobalContext dgctx) {
            _dgctx = dgctx;
        }

        public GlobalContext getGlobalContext() {
            return _dgctx;
        }
    }
}
