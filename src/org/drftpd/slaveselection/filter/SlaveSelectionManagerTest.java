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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.BasicConfigurator;

import java.util.Arrays;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: SlaveSelectionManagerTest.java,v 1.6 2004/08/03 20:14:10 zubov Exp $
 */
public class SlaveSelectionManagerTest extends TestCase {
    public SlaveSelectionManagerTest(String fName) {
        super(fName);
    }

    public static TestSuite suite() {
        return new TestSuite(SlaveSelectionManagerTest.class);
    }

    public void testEmptyFail() {
        Properties p = new Properties();

        try {
            new FilterChain(null, p);
            fail();
        } catch (IllegalArgumentException pass) {
        }
    }

    public void testBandwidth() {
        Properties p = new Properties();
        p.put("1.filter", "bandwidth");

        //p.put("1.expr", "*");
        p.put("1.multiplier", "1");

        FilterChain ssm = new FilterChain(null, p);
        RemoteSlave[] rslaves = {
                new RemoteSlave("slave1", null), new RemoteSlave("slave2", null)
            };

        try {
            ssm.getBestSlave(new ScoreChart(Arrays.asList(rslaves)), null,
                null, Transfer.TRANSFER_SENDING_DOWNLOAD,
                new MatchdirFilterTest.LinkedRemoteFilePath("/blabla/file.txt"));
            fail(); // no slaves are online
        } catch (NoAvailableSlaveException pass) {
        }
    }

    public void setUp() {
        BasicConfigurator.configure();
    }
}
