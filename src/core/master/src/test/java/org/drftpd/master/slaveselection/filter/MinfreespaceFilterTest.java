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
import org.drftpd.common.util.Bytes;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.DummyRemoteSlave;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveStatus;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;


/**
 * @author mog
 * @version $Id$
 */
public class MinfreespaceFilterTest {

    @Test
    public void testSimple() throws Exception {
        Properties p = new Properties();
        p.put("1.multiplier", "1");
        p.put("1.minfreespace", "100MB");

        SlaveStatus s = new SlaveStatus(new DiskStatus(Bytes.parseBytes("50MB"),
                Bytes.parseBytes("100GB")), 0, 0, 0, 0, 0, 0);
        RemoteSlave[] rslaves = {
                new RemoteSlaveTesting("slave1", Collections.emptyList(), s)
        };
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        Filter f = new MinfreespaceFilter(1, p);
        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, null, null);

        assertEquals(Bytes.parseBytes("-50MB"), sc.getScoreForSlave(rslaves[0]).getScore());
    }

    public static class RemoteSlaveTesting extends DummyRemoteSlave {
        private final SlaveStatus _status;

        public RemoteSlaveTesting(String name, Collection<Object> masks,
                                  SlaveStatus status) {
            super(name);
            _status = status;
        }

        public SlaveStatus getSlaveStatusAvailable() {
            return _status;
        }
    }
}
