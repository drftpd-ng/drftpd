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

import org.drftpd.common.slave.DiskStatus;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveStatus;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author mog
 * @version $Id$
 */
public class BandwidthFilterTest {

    @Test
    public void testBandwidth() throws NoAvailableSlaveException, ObjectNotFoundException {
        Properties p = new Properties();
        p.put("1.multiplier", "3");

        Filter f = new BandwidthFilter(1, p);

        SlaveStatus status = new SlaveStatus(new DiskStatus(0, 0), 0, 0, 100, 0, 100, 0);
        RemoteSlave[] list = {new RS("slave1", status)};
        ScoreChart sc = new ScoreChart(Arrays.asList(list));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);

        assertEquals(-300, sc.getScoreForSlave(list[0]).getScore());
    }

    static class RS extends DummyRemoteSlave {
        private final SlaveStatus _status;

        public RS(String name, SlaveStatus status) {
            super(name);
            _status = status;
        }

        public SlaveStatus getSlaveStatusAvailable() {
            return _status;
        }
    }
}
