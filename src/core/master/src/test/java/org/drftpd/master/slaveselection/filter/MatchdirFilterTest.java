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
 * @author mog
 * @version $Id$
 */
public class MatchdirFilterTest {
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
    public void testSimple() throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.assign", "slave1+100, slave2-100");
        p.put("1.match", "/path1/*");

        Filter f = new MatchdirFilter(1, p);
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
    public void testAll() throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.assign", "ALL+100");
        p.put("1.match", "/path2/*");

        Filter f = new MatchdirFilter(1, p);
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
    public void testRemove() throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.assign", "slave2-remove");
        p.put("1.match", "/path1/*");

        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        Filter f = new MatchdirFilter(1, p);
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
}
