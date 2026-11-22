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
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.slave.network.AsyncResponseSystemInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

        volatile boolean _online = true;

        @Override
        public boolean isOnline() {
            return _online;
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

    /** Plays the reader thread: waits for the request, stores the reply and wakes the waiters. */
    private Future<?> startReader(ExecutorService pool, int requests) {
        return pool.submit(() -> {
            waitUntilSent(requests);
            assertTrue(_slave.storeResponse(reply(_lastIndex)));
            _slave.wakeCommandWaiters();
            return null;
        });
    }

    @Test
    public void testReplyBeforeTimeoutIsDelivered() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            // Future.get() rethrows anything the reader thread's assertions threw
            Future<?> reader = startReader(pool, 1);
            SystemInfoReply r = _slave.fetchSystemInfo(5000, _sender);
            reader.get(5, TimeUnit.SECONDS);
            assertEquals(SystemInfoReply.Status.OK, r.getStatus());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testReplyWakesUpWaiterViaNotify() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SystemInfoReply> caller = pool.submit(() -> _slave.fetchSystemInfo(30000, _sender));
            waitUntilSent(1);
            Thread.sleep(100); // let the caller settle into its wait
            long start = System.nanoTime();
            assertTrue(_slave.storeResponse(reply(_lastIndex)));
            _slave.wakeCommandWaiters();
            SystemInfoReply r = caller.get(5, TimeUnit.SECONDS);
            long elapsedMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
            assertEquals(SystemInfoReply.Status.OK, r.getStatus());
            // far below the 30 s timeout (the waiter also re-polls every 50 ms, so this is generous)
            assertTrue(elapsedMillis < 2000, "waiter returned after " + elapsedMillis + " ms");
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testSendFailureClearsStateAndNextRequestGoesOut() throws Exception {
        RemoteSlave.SysInfoSender failing = () -> {
            throw new SlaveUnavailableException("gone");
        };
        assertEquals(SystemInfoReply.Status.OFFLINE, _slave.fetchSystemInfo(1000, failing).getStatus());

        // not stuck as "pending": the next request is sent and can be answered
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<?> reader = startReader(pool, 1);
            assertEquals(SystemInfoReply.Status.OK, _slave.fetchSystemInfo(5000, _sender).getStatus());
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertEquals(1, _sent.get());
    }

    @Test
    public void testRuntimeExceptionFromSenderCompletesSharedRequest() {
        RemoteSlave.SysInfoSender broken = () -> {
            throw new IllegalStateException("boom");
        };
        assertThrows(IllegalStateException.class, () -> _slave.fetchSystemInfo(1000, broken));
        // the future was completed and cleared: a later caller is not stuck behind it
        assertEquals(SystemInfoReply.Status.TIMEOUT, _slave.fetchSystemInfo(50, _sender).getStatus());
        assertEquals(1, _sent.get());
    }

    @Test
    public void testSlaveGoingOfflineWhileWaiting() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SystemInfoReply> caller = pool.submit(() -> _slave.fetchSystemInfo(30000, _sender));
            waitUntilSent(1);
            _slave._online = false;
            assertEquals(SystemInfoReply.Status.OFFLINE, caller.get(5, TimeUnit.SECONDS).getStatus());

            // reconnect: the state is clean, a new request is sent and not answered "still waiting"
            _slave._online = true;
            _slave.resetIndexes();
            Future<?> reader = startReader(pool, 2);
            assertEquals(SystemInfoReply.Status.OK, _slave.fetchSystemInfo(5000, _sender).getStatus());
            reader.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
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
    public void testConcurrentCallersShareOneRequest() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SystemInfoReply> first = pool.submit(() -> _slave.fetchSystemInfo(5000, _sender));
            waitUntilSent(1);
            Future<SystemInfoReply> second = pool.submit(() -> _slave.fetchSystemInfo(5000, _sender));
            Thread.sleep(100); // let the second caller join the request in flight
            assertTrue(_slave.storeResponse(reply(_lastIndex)));
            _slave.wakeCommandWaiters();
            SystemInfoReply r1 = first.get(5, TimeUnit.SECONDS);
            SystemInfoReply r2 = second.get(5, TimeUnit.SECONDS);
            assertEquals(SystemInfoReply.Status.OK, r1.getStatus());
            assertEquals(SystemInfoReply.Status.OK, r2.getStatus());
            assertSame(r1, r2);
            assertEquals(1, _sent.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testSharedCallerGetsTimeoutWhenRequestTimesOut() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SystemInfoReply> first = pool.submit(() -> _slave.fetchSystemInfo(300, _sender));
            waitUntilSent(1);
            Future<SystemInfoReply> second = pool.submit(() -> _slave.fetchSystemInfo(5000, _sender));
            assertEquals(SystemInfoReply.Status.TIMEOUT, first.get(5, TimeUnit.SECONDS).getStatus());
            assertEquals(SystemInfoReply.Status.TIMEOUT, second.get(5, TimeUnit.SECONDS).getStatus());
            assertEquals(1, _sent.get());
        } finally {
            pool.shutdownNow();
        }
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
