/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.slaveselection.filter;

import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.tests.DummyGlobalContext;
import org.drftpd.master.tests.DummySlaveManager;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author scitz0
 * @version $Id$
 */
public class MatchdirExFilterTest {
    RemoteSlave[] rslaves = {
            new DummyRemoteSlave("slave1"),
            new DummyRemoteSlave("slave2"),
            new DummyRemoteSlave("slave3")
    };

    @BeforeAll
    static void setUp() {
        AssignSlave.setGlobalContext(new DummyGlobalContext());
        DummyGlobalContext dgctx = (DummyGlobalContext) AssignSlave.getGlobalContext();
        dgctx.setSlaveManager(new DummySlaveManager());
    }

    @Test
    public void testSimple() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100, slave2-100");
        p.put("1.match", "/path1/.*");

        Filter f = new MatchdirExFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/"), null);
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(-100, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());
    }

    @Test
    public void testAll() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "ALL+100");
        p.put("1.match", "/path2/.*");

        Filter f = new MatchdirExFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(100, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(100, sc.getScoreForSlave(rslaves[2]).getScore());
    }

    @Test
    public void testRemove() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "slave2-remove");
        p.put("1.match", "/path1/.*");

        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        Filter f = new MatchdirExFilter(1, p);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);

        assertEquals(0, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());

        try {
            sc.getScoreForSlave(rslaves[1]);
            Assertions.fail("should not be called");
        } catch (ObjectNotFoundException success) {
            //success
        }
    }

    private static java.util.List<String> names(ScoreChart sc) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (ScoreChart.SlaveScore score : sc.getSlaveScores()) {
            names.add(score.getRSlave().getName());
        }
        return names;
    }

    @Test
    public void testAssumeRemoveSlaveAddedLater() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100 slave2-100");
        p.put("1.match", "/path1/.*");
        p.put("1.assume.remove", "true");

        // Filter is built before slave4 exists (as with SITE ADDSLAVE after startup)
        Filter f = new MatchdirExFilter(1, p);
        RemoteSlave[] all = Arrays.copyOf(rslaves, 4);
        all[3] = new DummyRemoteSlave("slave4");

        ScoreChart sc = new ScoreChart(Arrays.asList(all));
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        assertEquals(Arrays.asList("slave1", "slave2"), names(sc));
        assertEquals(100, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(-100, sc.getScoreForSlave(rslaves[1]).getScore());

        sc = new ScoreChart(Arrays.asList(all));
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        assertEquals(Arrays.asList("slave1", "slave2", "slave3", "slave4"), names(sc));
    }

    @Test
    public void testAssumeRemoveNegated() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100");
        p.put("1.match", "/path1/.*");
        p.put("1.assume.remove", "true");
        p.put("1.negate.expression", "true");

        Filter f = new MatchdirExFilter(1, p);
        RemoteSlave[] all = Arrays.copyOf(rslaves, 4);
        all[3] = new DummyRemoteSlave("slave4");

        ScoreChart sc = new ScoreChart(Arrays.asList(all));
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        assertEquals(Arrays.asList("slave1", "slave2", "slave3", "slave4"), names(sc));

        sc = new ScoreChart(Arrays.asList(all));
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path2/dir/file.txt"), null);
        assertEquals(Arrays.asList("slave1"), names(sc));
    }

    @Test
    public void testNoAssumeRemoveKeepsOthers() throws Exception {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100");
        p.put("1.match", "/path1/.*");

        Filter f = new MatchdirExFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/path1/dir/file.txt"), null);
        assertEquals(3, sc.getSlaveScores().size());
    }
}
