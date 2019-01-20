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
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.DiskStatus;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyRemoteSlave;
import org.drftpd.vfs.DirectoryHandle;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id$
 */
public class MaxbandwidthFilterTest extends TestCase {
    RemoteSlave[] rslaves = {
            new RS("slave1", Collections.emptyList()),
            new RS("slave2", Collections.emptyList())
        };

    /**
     * Constructor for MaxbandwidthFilterTest.
     * @param arg0
     */
    public MaxbandwidthFilterTest(String arg0) {
        super(arg0);
    }

    public static void main(String[] args) {
        junit.textui.TestRunner.run(MaxbandwidthFilterTest.class);
    }

    public void testSimple() throws NoAvailableSlaveException {
        Properties p = new Properties();
        p.put("1.maxbandwidth", "800kb");

        Filter f = new MaxbandwidthFilter(1, p);
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

        f.process(sc, null, null, Transfer.TRANSFER_SENDING_DOWNLOAD, new DirectoryHandle("/"), null);
        Assert.assertEquals(sc.getBestSlave(), rslaves[1]);
    }

    static class RS extends DummyRemoteSlave {
        public RS(String name, Collection<Object> duh) {
            super(name);
        }

        public SlaveStatus getSlaveStatusAvailable() {
            if (getName().equals("slave2")) {
                return new SlaveStatus(new DiskStatus(0,0), 0, 0, 0, 0, 0, 0);
            }

            return new SlaveStatus(new DiskStatus(0,0), 0, 0, 9999999, 1, 9999999, 1);
        }
    }
}
