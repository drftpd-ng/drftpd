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
import java.util.Collections;
import java.util.Properties;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.SlaveFileException;

import org.apache.log4j.BasicConfigurator;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.StaticRemoteFile;
import org.drftpd.sections.def.SectionManager;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyConnectionManager;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;


/**
 * @author mog
 * @version $Id: SlavetopFilterTest.java 847 2004-12-02 03:32:41Z mog $
 */
public class SlavetopFilterTest extends TestCase {
    private LinkedRemoteFile _dir1;
    private LinkedRemoteFile _root;
	private RemoteSlave[] _rslaves;
	private ScoreChart _sc;
	private DummyFilterChain _fc;
	private LinkedRemoteFile _dir2;

    public SlavetopFilterTest(String name) {
        super(name);
    }

    public static TestSuite suite() {
        return new TestSuite(SlavetopFilterTest.class);
    }

    protected void setUp() throws Exception {
        BasicConfigurator.configure();
        _rslaves = new RemoteSlave[] {
                new DummyRemoteSlave("slave1", null),
                new DummyRemoteSlave("slave2", null),
                new DummyRemoteSlave("slave3", null)
            };

        _sc = new ScoreChart(Arrays.asList(_rslaves));

        _root = new LinkedRemoteFile(null);
        _dir1 = _root.createDirectory("dir1");

        _dir2 = _dir1.createDirectory("dir2");

        _dir2.addFile(new StaticRemoteFile("file1",
                Collections.singletonList(_rslaves[0])));
        _dir2.addFile(new StaticRemoteFile("file2",
                Collections.singletonList(_rslaves[2])));
        _dir2.addFile(new StaticRemoteFile("file3",
                Collections.singletonList(_rslaves[0])));
        _dir2.addFile(new StaticRemoteFile("file4",
                Collections.singletonList(_rslaves[1])));
        _dir2.addFile(new StaticRemoteFile("file5",
                Collections.singletonList(_rslaves[2])));

        // these 3 shouldn't get included by SlavetopFilter, as they are directly in the section.
        _dir1.addFile(new StaticRemoteFile("file6",
                Collections.singletonList(_rslaves[1])));

        _dir1.addFile(new StaticRemoteFile("file7",
                Collections.singletonList(_rslaves[1])));

        _dir1.addFile(new StaticRemoteFile("file8",
                Collections.singletonList(_rslaves[1])));

        _fc = new DummyFilterChain();
        DummyConnectionManager cm = new DummyConnectionManager();

        DummyGlobalContext gctx = new DummyGlobalContext();
        _fc.setGlobalContext(gctx);
        gctx.setSectionManager(new SectionManager(cm));
        gctx.setConnectionManager(cm);
        gctx.setRoot(_root);
        cm.setGlobalContext(gctx);

        DummySlaveManager sm = new DummySlaveManager();
        sm.setGlobalContext(gctx);
        _fc.setSlaveManager(sm);
    }

    public void testAssign()
        throws NoAvailableSlaveException, FileExistsException, 
            ObjectNotFoundException, RemoteException, SlaveFileException {
        Properties p = new Properties();
        p.put("1.topslaves", "2");
        p.put("1.assign", "100");

        SlavetopFilter f = new SlavetopFilter(_fc, 1, p);
        f.process(_sc, null, null, Transfer.TRANSFER_UNKNOWN, _dir2, null);
        assertEquals(100, _sc.getSlaveScore(_rslaves[0]).getScore());
        assertEquals(0, _sc.getSlaveScore(_rslaves[1]).getScore());
        assertEquals(100, _sc.getSlaveScore(_rslaves[2]).getScore());
    }
    
    public void testRemove() throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.topslaves", "2");
        p.put("1.assign", "0");

        SlavetopFilter f = new SlavetopFilter(_fc, 1, p);
        f.process(_sc, null, null, Transfer.TRANSFER_UNKNOWN, _dir2, null);
        assertEquals(0, _sc.getSlaveScore(_rslaves[0]).getScore());
        try {
        	_sc.getSlaveScore(_rslaves[1]).getScore();
        	fail();
        }catch(ObjectNotFoundException e) {
        }
        assertEquals(0, _sc.getSlaveScore(_rslaves[2]).getScore());
    }

    public class DummyFilterChain extends FilterChain {
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
