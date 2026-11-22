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
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ScoreChartTest {

    private static final char UP = Transfer.TRANSFER_RECEIVING_UPLOAD;

    @Test
    public void testEmptyChartThrows() {
        ScoreChart sc = new ScoreChart(Collections.emptyList());
        assertThrows(NoAvailableSlaveException.class, () -> sc.getBestSlave(UP));
        assertThrows(NoAvailableSlaveException.class, sc::getBestSlave);
    }

    @Test
    public void testHighestScoreWins() throws Exception {
        RemoteSlave s1 = new DummyRemoteSlave("slave1");
        RemoteSlave s2 = new DummyRemoteSlave("slave2");
        s2.setLastUploadReceiving(System.currentTimeMillis());
        ScoreChart sc = new ScoreChart(Arrays.asList(s1, s2));
        sc.addScoreToSlave(s2, 1);
        assertSame(s2, sc.getBestSlave(UP));
    }

    @Test
    public void testTiePicksLeastRecentlyUsed() throws Exception {
        RemoteSlave s1 = new DummyRemoteSlave("slave1");
        RemoteSlave s2 = new DummyRemoteSlave("slave2");
        RemoteSlave s3 = new DummyRemoteSlave("slave3");
        s1.setLastUploadReceiving(3000);
        s2.setLastUploadReceiving(1000);
        s3.setLastUploadReceiving(500); // least recently used, but lower score
        // download time must not matter for an upload selection
        s2.setLastDownloadSending(9000);
        ScoreChart sc = new ScoreChart(Arrays.asList(s1, s2, s3));
        sc.addScoreToSlave(s1, 100);
        sc.addScoreToSlave(s2, 100);
        assertSame(s2, sc.getBestSlave(UP));
        assertSame(s1, sc.getBestSlave(Transfer.TRANSFER_SENDING_DOWNLOAD));
    }

    private static FilterChain emptyChain() {
        FilterChain fc = new FilterChain() {};
        fc.reload(new Properties());
        return fc;
    }

    @Test
    public void testNextTiedSelectionPicksOtherSlave() throws Exception {
        RemoteSlave s1 = new DummyRemoteSlave("slave1");
        RemoteSlave s2 = new DummyRemoteSlave("slave2");
        FilterChain fc = emptyChain();
        List<RemoteSlave> slaves = Arrays.asList(s1, s2);

        RemoteSlave first = fc.getBestSlave(new ScoreChart(slaves), null, UP, null, null);
        RemoteSlave second = fc.getBestSlave(new ScoreChart(slaves), null, UP, null, null);
        RemoteSlave third = fc.getBestSlave(new ScoreChart(slaves), null, UP, null, null);
        assertNotSame(first, second);
        assertSame(first, third);
        assertTrue(first.getLastUploadReceiving() > 0);
        assertEquals(0, first.getLastDownloadSending());
        assertEquals(0, second.getLastDownloadSending());
    }

    @Test
    public void testConcurrentSelectionsAlternate() throws Exception {
        RemoteSlave s1 = new DummyRemoteSlave("slave1");
        RemoteSlave s2 = new DummyRemoteSlave("slave2");
        List<RemoteSlave> slaves = Arrays.asList(s1, s2);
        List<RemoteSlave> order = Collections.synchronizedList(new ArrayList<>());
        FilterChain fc = emptyChain();

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<RemoteSlave>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                // records the pick from inside FilterChain.getBestSlave, i.e. in selection order
                ScoreChart sc = new ScoreChart(slaves) {
                    @Override
                    public RemoteSlave getBestSlave(char direction) throws NoAvailableSlaveException {
                        RemoteSlave r = super.getBestSlave(direction);
                        order.add(r);
                        return r;
                    }
                };
                return fc.getBestSlave(sc, null, UP, null, null);
            }));
        }
        start.countDown();
        int count1 = 0;
        for (Future<RemoteSlave> f : futures) {
            if (f.get(10, TimeUnit.SECONDS) == s1) {
                count1++;
            }
        }
        pool.shutdown();

        assertEquals(threads / 2, count1);
        assertEquals(threads, order.size());
        for (int i = 1; i < order.size(); i++) {
            assertNotSame(order.get(i - 1), order.get(i), "pick " + i + " repeated the previous slave");
        }
    }
}
