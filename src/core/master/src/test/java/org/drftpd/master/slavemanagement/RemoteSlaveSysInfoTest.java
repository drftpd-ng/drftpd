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
package org.drftpd.master.slavemanagement;

import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.slave.SystemInfo;
import org.drftpd.slave.network.AsyncResponseSystemInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the sysinfo bookkeeping of {@link RemoteSlave} (abandoned indexes, single flight, late replies)
 * with a fake sender and by feeding replies through {@link RemoteSlave#storeResponse} like the reader thread does.
 */
public class RemoteSlaveSysInfoTest {

    /** A slave that is "online" without a socket. */
    private static class FakeSlave extends RemoteSlave {
        FakeSlave() {
            super("fake");
        }

        @Override
        public boolean isOnline() {
            return true;
        }
    }

    private FakeSlave _slave;
    private AtomicInteger _sent;
    private volatile String _lastIndex;
    private RemoteSlave.SysInfoSender _sender;

    @BeforeEach
    public void setUp() {
        _slave = new FakeSlave();
        _slave.resetIndexes();
        _sent = new AtomicInteger();
        // takes the next index like BasicIssuer does, without any network
        _sender = () -> {
            String index = _slave.fetchIndex();
            _lastIndex = index;
            _sent.incrementAndGet();
            return index;
        };
    }

    private static AsyncResponse reply(String index) {
        return new AsyncResponseSystemInfo(index, new SystemInfo());
    }

    private void waitUntilSent(int count) throws InterruptedException {
        while (_sent.get() < count) {
            Thread.sleep(5);
        }
    }

    @Test
    public void testReplyBeforeTimeoutIsDelivered() throws Exception {
        Thread reader = new Thread(() -> {
            try {
                waitUntilSent(1);
            } catch (InterruptedException ignored) {
            }
            assertTrue(_slave.storeResponse(reply(_lastIndex)));
        });
        reader.start();
        SystemInfoReply r = _slave.fetchSystemInfo(5000, _sender);
        reader.join();
        assertEquals(SystemInfoReply.Status.OK, r.getStatus());
    }

    @Test
    public void testTimeoutAbandonsIndexAndLateReplyReturnsIt() throws Exception {
        SystemInfoReply r = _slave.fetchSystemInfo(100, _sender);
        assertEquals(SystemInfoReply.Status.TIMEOUT, r.getStatus());
        assertEquals(1, _sent.get());
        String abandoned = _lastIndex;

        // another command must not get the abandoned index: take everything else from the pool
        for (int i = 0; i < 255; i++) {
            assertNotEquals(abandoned, _slave.fetchIndex());
        }

        // the late reply is discarded (not delivered to anybody) and the index is recycled
        assertFalse(_slave.storeResponse(reply(abandoned)));
        assertEquals(abandoned, _slave.fetchIndex());
    }

    @Test
    public void testSingleFlightSkipsWhileAbandoned() {
        assertEquals(SystemInfoReply.Status.TIMEOUT, _slave.fetchSystemInfo(50, _sender).getStatus());
        assertEquals(1, _sent.get());
        String abandoned = _lastIndex;

        // still abandoned: nothing is sent, repeated requests do not eat indexes
        for (int i = 0; i < 300; i++) {
            SystemInfoReply r = _slave.fetchSystemInfo(50, _sender);
            assertEquals(SystemInfoReply.Status.TIMEOUT, r.getStatus());
            assertNotNull(r.getMessage());
        }
        assertEquals(1, _sent.get());

        // the late reply releases the slave for the next request
        assertFalse(_slave.storeResponse(reply(abandoned)));
        assertEquals(SystemInfoReply.Status.TIMEOUT, _slave.fetchSystemInfo(50, _sender).getStatus());
        assertEquals(2, _sent.get());
    }

    @Test
    public void testConcurrentRequestIsSkipped() throws Exception {
        Thread first = new Thread(() -> _slave.fetchSystemInfo(5000, _sender));
        first.start();
        waitUntilSent(1);
        SystemInfoReply second = _slave.fetchSystemInfo(5000, _sender);
        assertEquals(SystemInfoReply.Status.TIMEOUT, second.getStatus());
        assertEquals(1, _sent.get());
        assertTrue(_slave.storeResponse(reply(_lastIndex)));
        first.join();
    }

    @Test
    public void testOkReplyIsCachedBriefly() throws Exception {
        Thread reader = new Thread(() -> {
            try {
                waitUntilSent(1);
            } catch (InterruptedException ignored) {
            }
            _slave.storeResponse(reply(_lastIndex));
        });
        reader.start();
        assertEquals(SystemInfoReply.Status.OK, _slave.fetchSystemInfo(5000, _sender).getStatus());
        reader.join();
        assertEquals(SystemInfoReply.Status.OK, _slave.fetchSystemInfo(5000, _sender).getStatus());
        assertEquals(1, _sent.get());
    }

    @Test
    public void testReconnectForgetsAbandonedState() {
        assertEquals(SystemInfoReply.Status.TIMEOUT, _slave.fetchSystemInfo(50, _sender).getStatus());
        _slave.resetIndexes();
        assertEquals(SystemInfoReply.Status.TIMEOUT, _slave.fetchSystemInfo(50, _sender).getStatus());
        assertEquals(2, _sent.get());
    }
}
