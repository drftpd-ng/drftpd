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

package org.drftpd.master.vfs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;

import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This classes handle all XML commits.
 * The main purpose of having this is to avoid serializing the same object tons of times,
 * even if its data was not changed.
 *
 * @author zubov
 * @version $Id$
 */
public class CommitManager {

    private static final Logger logger = LogManager.getLogger(CommitManager.class);

    private static CommitManager _instance;

    private final ConcurrentLinkedQueue<CommitableWrapper> _commitQueue;
    private boolean _isStarted;
    private final AtomicInteger _queueSize;
    private volatile boolean _drainQueue;
    private Thread _commitThread;

    /**
     * Private constructor in order to make this class a Singleton.
     */
    private CommitManager() {
        _commitQueue = new ConcurrentLinkedQueue<>();
        _queueSize = new AtomicInteger();
    }

    /**
     * @return the unique CommitManager instance, creating the instance if it does not exist yet.
     */
    public static CommitManager getCommitManager() {
        if (_instance == null) {
            _instance = new CommitManager();
        }
        return _instance;
    }

    /**
     * Starts the {@link CommitHandler}.
     *
     * @throws IllegalStateException if the thread has already started.
     */
    public void start() {
        if (_isStarted) {
            throw new IllegalStateException("The CommitManager is already started");
        }

        _isStarted = true;
        _commitThread = new Thread(new CommitHandler());
        _commitThread.start();
    }

    /**
     * Get the state of the internal thread
     */
    public Thread.State getThreadState() {
        return _commitThread.getState();
    }

    /**
     * Adds a {@link Commitable} object to the commit queue.
     * If the object is already present on the queue, this call is just ignored.
     *
     * @param object The Commitable object
     */
    public void add(Commitable object) {
        if (contains(object)) {
            return;
            // object already queued to write
        }
        _commitQueue.offer(new CommitableWrapper(object));
        _queueSize.incrementAndGet();
    }

    /**
     * Writes the requested {@link Commitable} object immediately and bypasses the queue
     *
     * @param object The Commitable object
     */
    public void writeImmediately(Commitable object) {
        ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
        writeCommitable(object);
        Thread.currentThread().setContextClassLoader(prevCL);
    }

    /**
     * Remove a {@link Commitable} object from the queue
     *
     * @param object The Commitable object
     * @return true if the object was removed from the CommitQueue, false otherwise.
     */
    public boolean remove(Commitable object) {
        if (object == null) return false;
        for (Iterator<CommitableWrapper> iter = _commitQueue.iterator(); iter.hasNext(); ) {
            CommitableWrapper cw = iter.next();
            if (cw != null && cw.equals(object)) {
                iter.remove();
                _queueSize.decrementAndGet();
                return true;
            }
        }
        return false;
    }

    /**
     * @param object
     * @return true if the object is present on the CommitQueue, false otherwise.
     */
    public boolean contains(Commitable object) {
        if (object == null) return false;

        for (CommitableWrapper cw : _commitQueue) {
            if (cw != null && cw.equals(object)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the number of outstanding objects to commit.
     */
    public int getQueueSize() {
        return _queueSize.get();
    }

    /**
     * Forces the immediate write of a (@link Commitable) if present in the commit queue.
     *
     * @param object
     */
    public void flushImmediate(Commitable object) {
        if (contains(object)) {
            ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
            writeCommitable(object);
            Thread.currentThread().setContextClassLoader(prevCL);
        }
    }

    /**
     * Instructs the commitmanager to write all queued items regardless of age, this cannot
     * be undone and is used only when the process enters shutdown mode.
     */
    public void enableQueueDrain() {
        _drainQueue = true;
        // Wakeup the commit thread incase it is sleeping
        if (_commitThread != null) {
            _commitThread.interrupt();
        }
    }

    private long getCommitDelay() {
        Properties cfg = GlobalContext.getConfig().getMainProperties();
        try {
            return Long.parseLong(PropertyHelper.getProperty(cfg, "disk.commit.delay", "10000"));
        } catch (NumberFormatException e) {
        }
        return 10000;
    }

    private void processAllLoop() {
        while (true) {
            long delay = getCommitDelay();
            long time = System.currentTimeMillis() - delay;
            for (Iterator<CommitableWrapper> iter = _commitQueue.iterator(); iter.hasNext(); ) {
                CommitableWrapper cw = iter.next();
                if (cw.getTime() < time || _drainQueue) {
                    if (writeCommitable(cw.getCommitable())) {
                        iter.remove();
                        _queueSize.decrementAndGet();
                    }
                }
            }

            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
            }
        }
    }

    private boolean writeCommitable(Commitable item) {
        try {
            item.writeToDisk();
            return true;
        } catch (Exception e) {
            logger.error("Error writing object to disk - {}", item.descriptiveName(), e);
        }
        return false;
    }

    /**
     * Creates a wrapping object for the Commitable object and current time.
     */
    private static class CommitableWrapper {
        private final Commitable _object;
        private final long _time;

        private CommitableWrapper(Commitable object) {
            _object = object;
            _time = System.currentTimeMillis();
        }

        public Commitable getCommitable() {
            return _object;
        }

        public long getTime() {
            return _time;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) return true;
            if (obj == null) return false;
            return (obj instanceof Commitable) && _object.equals(obj);
        }

        @Override
        public int hashCode() {
            return _object.hashCode();
        }
    }

    private class CommitHandler implements Runnable {

        private CommitHandler() {
        }

        public void run() {
            Thread.currentThread().setName("CommitHandler");
            processAllLoop();
        }
    }
}
