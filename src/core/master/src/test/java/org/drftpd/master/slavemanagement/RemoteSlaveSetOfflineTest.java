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

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.Socket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * setOffline can be called by several threads at once, e.g. a command thread that gives up on the slave closes the
 * socket and the reader thread, failing on that socket, calls setOffline as well.
 */
public class RemoteSlaveSetOfflineTest {

    /** Counts how often the connection is closed. */
    private static class CountingSocket extends Socket {
        final AtomicInteger _closed = new AtomicInteger();

        @Override
        public synchronized void close() {
            _closed.incrementAndGet();
        }
    }

    /**
     * Holds the first caller in setProperty("lastOnline"), i.e. between the null check of the socket and its use,
     * until a second caller has gone all the way through setOffline. Does not write the slave to disk.
     */
    private static class RacingSlave extends RemoteSlave {
        final CountDownLatch _firstInside = new CountDownLatch(1);
        final CountDownLatch _secondDone = new CountDownLatch(1);
        final AtomicInteger _lastOnlineWrites = new AtomicInteger();

        RacingSlave() {
            super("racing");
        }

        @Override
        public void setProperty(String name, String value) {
            if ("lastOnline".equals(name) && _lastOnlineWrites.incrementAndGet() == 1) {
                _firstInside.countDown();
                try {
                    assertTrue(_secondDone.await(5, TimeUnit.SECONDS));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private static void setSocket(RemoteSlave slave, Socket socket) throws ReflectiveOperationException {
        Field f = RemoteSlave.class.getDeclaredField("_socket");
        f.setAccessible(true);
        f.set(slave, socket);
    }

    @Test
    public void testConcurrentSetOfflineDoesNotThrow() throws Exception {
        RacingSlave slave = new RacingSlave();
        CountingSocket socket = new CountingSocket();
        setSocket(slave, socket);
        ExecutorService pool = Executors.newCachedThreadPool();
        try {
            Future<?> first = pool.submit(() -> slave.setOffline("command timed out"));
            assertTrue(slave._firstInside.await(5, TimeUnit.SECONDS));
            Future<?> second = pool.submit(() -> slave.setOffline("IOException reading AsyncResponse"));
            second.get(5, TimeUnit.SECONDS);
            slave._secondDone.countDown();
            // used to fail with: Cannot invoke "java.net.Socket.close()" because "this._socket" is null
            first.get(5, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }
        assertFalse(slave.isOnline());
        assertTrue(socket._closed.get() >= 1);

        // and once offline, going offline again is a no-op for the connection
        int closed = socket._closed.get();
        slave.setOffline("again");
        assertEquals(closed, socket._closed.get());
        assertEquals(2, slave._lastOnlineWrites.get());
    }
}
