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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

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
        volatile String _offlineReason;

        @Override
        public boolean isOnline() {
            return _online;
        }

        /** Records the reason instead of tearing down a connection (there is none). */
        @Override
        public void setOffline(String reason) {
            _offlineReason = reason;
            _online = false;
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

    /** Runs the task on the pool and records the thread that runs it, so the test can see when it blocks. */
    private static <T> Future<T> submitTracked(ExecutorService pool, AtomicReference<Thread> runner,
                                               Callable<T> task) {
        return pool.submit(() -> {
            runner.set(Thread.currentThread());
            return task.call();
        });
    }

    /**
     * Waits (bounded) until the tracked thread is parked in a timed wait. The callers under test only park in
     * {@code _commandMonitor.wait(50)} or in the shared future's timed {@code get}, both after they have read the
     * connection generation, so a reconnect from now on is one they must notice.
     */
    private static void awaitTimedWaiting(AtomicReference<Thread> runner) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            Thread t = runner.get();
            if (t != null && t.getState() == Thread.State.TIMED_WAITING) {
                return;
            }
            if (System.nanoTime() > deadline) {
                fail("caller did not start waiting within 10 seconds, state " + (t == null ? "not started" : t.getState()));
            }
            Thread.sleep(1);
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

    /** Takes every index from the pool and checks that each of the 256 is there exactly once. */
    private Set<String> drainPool() throws SlaveUnavailableException {
        Set<String> indexes = new HashSet<>();
        for (int i = 0; i < 256; i++) {
            assertTrue(indexes.add(_slave.fetchIndex()), "index handed out twice");
        }
        return indexes;
    }

    @Test
    public void testReconnectWithinOneTickDoesNotAbandonIndexOfNewConnection() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<SystemInfoReply> caller = pool.submit(() -> _slave.fetchSystemInfo(500, _sender));
            waitUntilSent(1);
            String stale = _lastIndex;
            // the slave drops and comes back between two polls of the waiter: it never sees isOnline() == false
            _slave.resetIndexes();
            assertEquals(SystemInfoReply.Status.OFFLINE, caller.get(5, TimeUnit.SECONDS).getStatus());

            // the rebuilt pool is intact and the index is usable again on the new connection: a reply to a
            // command sent with it is delivered, not discarded as a late sysinfo reply
            assertTrue(drainPool().contains(stale));
            assertTrue(_slave.storeResponse(reply(stale)));
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testReconnectWithinOneTickReleasesSharedCallersAndNextRequestGoesOut() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            // timeouts far above any scheduling delay: only the reconnect may end these waits
            Future<SystemInfoReply> first = pool.submit(() -> _slave.fetchSystemInfo(30000, _sender));
            waitUntilSent(1);
            AtomicReference<Thread> secondThread = new AtomicReference<>();
            Future<SystemInfoReply> second = submitTracked(pool, secondThread,
                    () -> _slave.fetchSystemInfo(30000, _sender));
            // the second caller has joined the request in flight (it parks in the shared future's get)
            awaitTimedWaiting(secondThread);
            _slave.resetIndexes();
            assertEquals(SystemInfoReply.Status.OFFLINE, first.get(10, TimeUnit.SECONDS).getStatus());
            assertEquals(SystemInfoReply.Status.OFFLINE, second.get(10, TimeUnit.SECONDS).getStatus());

            // nothing is left pending: the next request is sent on the new connection and answered
            Future<?> reader = startReader(pool, 2);
            assertEquals(SystemInfoReply.Status.OK, _slave.fetchSystemInfo(5000, _sender).getStatus());
            reader.get(5, TimeUnit.SECONDS);
            assertEquals(2, _sent.get());
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    public void testFetchResponseReconnectWithinOneTick() throws Exception {
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            String stale = _slave.fetchIndex();
            AtomicReference<Thread> callerThread = new AtomicReference<>();
            // without the generation check the caller would wait out this timeout and then put the slave
            // offline; it is long enough that a slow runner cannot reach it before the reconnect below
            Future<?> caller = submitTracked(pool, callerThread, () -> _slave.fetchResponse(stale, 5000));
            // the caller is inside fetchResponse, parked in its 50 ms wait, and has read the generation
            awaitTimedWaiting(callerThread);
            _slave.resetIndexes();
            ExecutionException e = assertThrows(ExecutionException.class, () -> caller.get(10, TimeUnit.SECONDS));
            assertInstanceOf(SlaveUnavailableException.class, e.getCause());
            // the new connection is not dropped because the old one's command timed out
            assertNull(_slave._offlineReason);
            assertTrue(_slave.isOnline());
            // and the old index was not pushed into the rebuilt pool a second time
            assertTrue(drainPool().contains(stale));
        } finally {
            pool.shutdownNow();
        }
    }
}
