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
import org.drftpd.Time;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.Transfer;
import org.drftpd.tests.DummyRemoteSlave;
import org.junit.Assert;

import java.util.Arrays;
import java.util.Properties;


/**
 * @author mog
 * @version $Id$
 */
public class MintimeonlineFilterTest extends TestCase {
    public MintimeonlineFilterTest(String name) {
        super(name);
    }

    public void testSimple() throws NoAvailableSlaveException {
        Properties p = new Properties();
        p.put("1.multiplier", "1");
        p.put("1.mintime", "2m");

        long time = System.currentTimeMillis();
        RemoteSlave[] rslaves = { new RS("slave1", time) };
        ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));
        MintimeonlineFilter f = new MintimeonlineFilter(1, p);
        f.process(sc, null, null, Transfer.TRANSFER_UNKNOWN, null, time);
        Assert.assertEquals(-Time.parseTime("1m"), sc.getBestSlaveScore().getScore());
    }

    public static class RS extends DummyRemoteSlave {
        private long _time;

        public RS(String name, long time) {
            super(name);
            _time = time;
        }

        public long getLastTransferForDirection(char dir) {
            return _time - Time.parseTime("1m");
        }
    }
}
