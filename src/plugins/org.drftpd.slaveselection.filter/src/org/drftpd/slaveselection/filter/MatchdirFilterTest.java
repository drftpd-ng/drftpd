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
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyGlobalContext;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.tests.DummySlaveManager;
import org.drftpd.vfs.DirectoryHandle;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class MatchdirFilterTest extends TestCase {
    RemoteSlave[] rslaves = {
            new DummyRemoteSlave("slave1"),
            new DummyRemoteSlave("slave2"),
            new DummyRemoteSlave("slave3")
        };

    public MatchdirFilterTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(MatchdirFilterTest.class);
    }
    
    public void setUp() {
        AssignSlave.setGlobalContext(new DummyGlobalContext());
        DummyGlobalContext dgctx = (DummyGlobalContext) AssignSlave.getGlobalContext();
        dgctx.setSlaveManager(new DummySlaveManager());    
    }

   public void testSimple()
        throws ObjectNotFoundException, NoAvailableSlaveException {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100, slave2-100");
        p.put("1.match", "/path1/*");
        
        Filter f = new MatchdirFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/"), null);
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        Assert.assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(-100, sc.getScoreForSlave(rslaves[1]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());
    }

   public void testAll()
        throws ObjectNotFoundException, NoAvailableSlaveException {
        Properties p = new Properties();
        p.put("1.assign", "ALL+100");
        p.put("1.match", "/path2/*");

        Filter f = new MatchdirFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        Assert.assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(100, sc.getScoreForSlave(rslaves[1]).getScore());
        Assert.assertEquals(100, sc.getScoreForSlave(rslaves[2]).getScore());
    }

    public void testRemove()
        throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.assign", "slave2-remove");
        p.put("1.match", "/path1/*");

        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        Filter f = new MatchdirFilter(1, p);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);

        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        Assert.assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        try {
            sc.getScoreForSlave(rslaves[1]);
            Assert.fail();
        } catch (ObjectNotFoundException success) {
            //success
        }
    }
}
