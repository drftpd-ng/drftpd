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
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author zubov
 * @version $Id$
 */
public class CycleFilterTest {

    @Test
    public void testProcess() throws NoAvailableSlaveException, ObjectNotFoundException {
        RemoteSlave[] rslaves = {
                new DummyRemoteSlave("slave1"),
                new DummyRemoteSlave("slave2"),
                new DummyRemoteSlave("slave3")
        };
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        Filter f = new CycleFilter(0, null);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);
        assertEquals(1, sc.getScoreForSlave(rslaves[0]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[1]).getScore());
        assertEquals(0, sc.getScoreForSlave(rslaves[2]).getScore());
    }
}
