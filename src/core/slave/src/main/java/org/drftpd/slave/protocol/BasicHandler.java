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
package org.drftpd.slave.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.exceptions.TransferDeniedException;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.common.io.PhysicalFile;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.network.PassiveConnection;
import org.drftpd.common.slave.ConnectInfo;
import org.drftpd.common.slave.LightRemoteInode;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.slave.TransferStatus;
import org.drftpd.slave.Slave;
import org.drftpd.slave.network.*;
import org.drftpd.slave.vfs.RootCollection;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.sql.Array;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Basic operations handling.
 *
 * @author fr0w
 * @author zubov
 * @author mog
 * @version $Id$
 */
public class BasicHandler extends AbstractHandler {
    private static final Logger logger = LogManager.getLogger(BasicHandler.class);

    // The following variables are static as they are used to signal between
    // remerging and the pause/resume functions, due to the way the handler
    // map works these are run against separate object instances.
    private static final AtomicBoolean remergePaused = new AtomicBoolean();
    private static final AtomicBoolean _remerging = new AtomicBoolean();
    private final ThreadPoolExecutor _pool;
    private static final Object remergeWaitObj = new Object();
    private static final Object mergeDepthWaitObj = new Object();
    private final ArrayList<String> mergeDepth = new ArrayList<>();
    private final ArrayList<RemergeItem> remergeResponses = new ArrayList<>();

    public BasicHandler(SlaveProtocolCentral central) {
        super(central);

        // Initialize us as not remerging
        _remerging.set(false);

        // Get the amount of concurrent threads our threadpool can run at
        // We start with 1 as a default and only increase if we are running threaded remerge mode
        int numThreads = 1;
        if (getSlaveObject().threadedRemerge()) {
            logger.fatal("threaded remerge enabled");
            if (getSlaveObject().threadedThreads() > 0) {
                numThreads = getSlaveObject().threadedThreads();
            } else {
                numThreads = Runtime.getRuntime().availableProcessors();
                if (numThreads > 2) {
                    numThreads -= 1;
                }
            }
        }
        logger.debug("Initializing the pool for remerge with {} threads", numThreads);
        _pool = new ThreadPoolExecutor(numThreads, numThreads, 300, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), new RemergeThreadFactory(),
                new ThreadPoolExecutor.CallerRunsPolicy());
        _pool.allowCoreThreadTimeOut(true);
    }

    @Override
    public String getProtocolName() {
        return "BasicProtocol";
    }

    // TODO check this.
    // ABORT
    public AsyncResponse handleAbort(AsyncCommandArgument ac) {
        TransferIndex ti = new TransferIndex(Integer.parseInt(ac.getArgsArray()[0]));

        Map<TransferIndex, Transfer> transfers = getSlaveObject().getTransferMap();

        if (!transfers.containsKey(ti)) {
            return null;
        }

        Transfer t = transfers.get(ti);
        t.abort(ac.getArgsArray()[1]);
        return new AsyncResponse(ac.getIndex());
    }

    // CONNECT
    public AsyncResponse handleConnect(AsyncCommandArgument ac) {
        String[] data = ac.getArgsArray()[0].split(":");
        boolean encrypted = ac.getArgsArray()[1].equals("true");
        boolean useSSLClientHandshake = ac.getArgsArray()[2].equals("true");
        InetAddress address;

        try {
            address = InetAddress.getByName(data[0]);
        } catch (UnknownHostException e1) {
            return new AsyncResponseException(ac.getIndex(), e1);
        }

        int port = Integer.parseInt(data[1]);
        Transfer t = new Transfer(new ActiveConnection(encrypted ? getSlaveObject().getSSLContext() : null,
                new InetSocketAddress(address, port), useSSLClientHandshake, getSlaveObject().getBindIP()),
                getSlaveObject(), new TransferIndex());

        getSlaveObject().addTransfer(t);

        return new AsyncResponseTransfer(ac.getIndex(), new ConnectInfo(port, t.getTransferIndex(), t.getTransferStatus()));
    }

    // DELETE
    public AsyncResponse handleDelete(AsyncCommandArgument ac) {
        try {
            getSlaveObject().delete(ac.getArgs());
            sendResponse(new AsyncResponseDiskStatus(getSlaveObject().getDiskStatus()));
            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    // LISTEN
    public AsyncResponse handleListen(AsyncCommandArgument ac) {
        String[] data = ac.getArgs().split(":");
        boolean encrypted = data[0].equals("true");
        boolean useSSLClientMode = data[1].equals("true");
        PassiveConnection c;

        try {
            c = new PassiveConnection(encrypted ? getSlaveObject().getSSLContext() : null,
                    getSlaveObject().getPortRange(), useSSLClientMode, getSlaveObject().getBindIP());

        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }

        Transfer t = new Transfer(c, getSlaveObject(), new TransferIndex());
        getSlaveObject().addTransfer(t);

        return new AsyncResponseTransfer(ac.getIndex(), new ConnectInfo(c.getLocalPort(), t.getTransferIndex(), t.getTransferStatus()));
    }

    // MAXPATH
    public AsyncResponse handleMaxpath(AsyncCommandArgument ac) {
        int maxPathLength = getSlaveObject().getMaxPathLength();
        return new AsyncResponseMaxPath(ac.getIndex(), maxPathLength);
    }

    // PING
    public AsyncResponse handlePing(AsyncCommandArgument ac) {
        return new AsyncResponse(ac.getIndex());
    }

    // RECEIVE
    public AsyncResponse handleReceive(AsyncCommandArgument ac) {
        char type = ac.getArgsArray()[0].charAt(0);
        long position = Long.parseLong(ac.getArgsArray()[1]);
        TransferIndex transferIndex = new TransferIndex(Integer.parseInt(ac.getArgsArray()[2]));
        String inetAddress = ac.getArgsArray()[3];
        String path = ac.getArgsArray()[4];
        String fileName = path.substring(path.lastIndexOf("/") + 1);
        String dirName = path.substring(0, path.lastIndexOf("/"));
        long minSpeed = Long.parseLong(ac.getArgsArray()[5]);
        long maxSpeed = Long.parseLong(ac.getArgsArray()[6]);
        Transfer t = getSlaveObject().getTransfer(transferIndex);
        t.setMinSpeed(minSpeed);
        t.setMaxSpeed(maxSpeed);
        getSlaveObject().sendResponse(new AsyncResponse(ac.getIndex())); // return calling thread on master
        try {
            return new AsyncResponseTransferStatus(t.receiveFile(dirName, type, fileName, position, inetAddress));
        } catch (IOException | TransferDeniedException e) {
            return new AsyncResponseTransferStatus(new TransferStatus(transferIndex, e));
        }
    }

    // REMERGE PAUSE
    public AsyncResponse handleRemergePause(AsyncCommandArgument ac) {
        remergePaused.set(true);
        return new AsyncResponse(ac.getIndex());
    }

    // REMERGE RESUME
    public AsyncResponse handleRemergeResume(AsyncCommandArgument ac) {
        remergePaused.set(false);
        synchronized (remergeWaitObj) {
            remergeWaitObj.notifyAll();
        }
        return new AsyncResponse(ac.getIndex());
    }

    /* REMERGE
     * args array:
     * 0: path (string)
     * 1: partialRemerge (boolean)
     * 2: skipAgeCutoff (long)
     * 3: masterTime (long)
     * 4: instantOnline (boolean)
     */
    public AsyncResponse handleRemerge(AsyncCommandArgument ac) {
        if (_remerging.get()) {
            logger.warn("Received remerge request while we are remerging");
            return new AsyncResponseException(ac.getIndex(), new Exception("Already remerging"));
        }
        try {
            // Slave Protocol central calls this with a dedicated thread which we give the lowest possible priority
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            // Get the arguments for this command
            String[] argsArray = ac.getArgsArray();
            String basePath = argsArray[0];
            boolean instantOnline = Boolean.parseBoolean(argsArray[4]);
            boolean partialRemerge = Boolean.parseBoolean(argsArray[1]) && !getSlaveObject().ignorePartialRemerge() && !instantOnline;
            long skipAgeCutoff = 0L; // We only care for the value the master gave us if we are partial remerging (see below)
            long masterTime = Long.parseLong(argsArray[3]);

            // Based on the input decide the remerge situation on our end and report back
            String remergeDecision = "Unexpected situation encountered in handleRemerge, please report";
            if (partialRemerge) {
                skipAgeCutoff = Long.parseLong(argsArray[2]);

                if (skipAgeCutoff != Long.MIN_VALUE) {
                    skipAgeCutoff += System.currentTimeMillis() - masterTime;
                }
                Date cutoffDate = new Date(skipAgeCutoff);
                remergeDecision = "Instant online: disabled, Partial remerge: enabled. skipping all files last modified before " + cutoffDate.toString() + ".  Remerging with " + _pool.getMaximumPoolSize() + " threads";
            } else if (instantOnline) {
                remergeDecision = "Instant online: enabled, Partial remerge: disabled. Remerging in background with " + _pool.getMaximumPoolSize() + " threads";
            } else {
                remergeDecision = "Instant online: disabled, Partial remerge: disabled. Remerging in foreground with " + _pool.getMaximumPoolSize() + " threads";
            }
            logger.info(remergeDecision);
            sendResponse(new AsyncResponseSiteBotMessage(remergeDecision));

            _remerging.set(true);
            // Start with a empty list!
            mergeDepth.clear();
            logger.debug("Remerging started");
            _pool.execute(new HandleRemergeThread(getSlaveObject().getRoots(), basePath, partialRemerge, skipAgeCutoff));

            while (_pool.getActiveCount() > 0 || remergeResponses.size() > 0) {
                // First check if we are still online, bail if not
                if (!getSlaveObject().isOnline()) {
                    // Slave has shut down, no need to continue with remerge
                    return null;
                }

                // Check if we can send stuff to the master
                synchronized(remergeResponses) {
                    Iterator<RemergeItem> rrIterator = remergeResponses.iterator();
                    while(rrIterator.hasNext()) {
                        RemergeItem ri = rrIterator.next();
                        int foundDirectories = 0;
                        synchronized (mergeDepthWaitObj) {
                            for (String path : ri.getDepthDirectories()) {
                                for (String dir : mergeDepth) {
                                    if (dir.startsWith(path)) {
                                        foundDirectories+=1;
                                    }
                                }
                            }
                        }
                        if (foundDirectories >= ri.getDepthDirectories().size()) {
                            logger.debug("Sending {} to the master", ri.getAsyncResponseRemerge().getPath());
                            rrIterator.remove();
                            sendResponse(ri.getAsyncResponseRemerge());
                            updateDepth(ri.getAsyncResponseRemerge().getPath() + "/");
                        }
                    }
                }

                try {
                    logger.debug("Queue: {}, Active threads: {}, Pending responses: {}, sleeping 1 second",
                            _pool.getQueue().size(), _pool.getActiveCount(), remergeResponses.size());
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Either we have been woken properly in which case we will exit the
                    // loop or we have not in which case we will wait again.
                }
            }

            logger.debug("Remerging done");
            _remerging.set(false);
            // Make sure we do not hog memory and clear the list
            mergeDepth.clear();
            return new AsyncResponse(ac.getIndex());
        } catch (Throwable e) {
            logger.error("Exception during merging", e);
            sendResponse(new AsyncResponseSiteBotMessage("Exception during merging"));

            _remerging.set(false);
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    // RENAME
    public AsyncResponse handleRename(AsyncCommandArgument ac) {
        String from = ac.getArgsArray()[0];
        String toDir = ac.getArgsArray()[1];
        String toFile = ac.getArgsArray()[2];

        try {
            getSlaveObject().rename(from, toDir, toFile);
            return new AsyncResponse(ac.getIndex());
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    // SEND
    public AsyncResponse handleSend(AsyncCommandArgument ac) {
        char type = ac.getArgsArray()[0].charAt(0);
        long position = Long.parseLong(ac.getArgsArray()[1]);
        TransferIndex transferIndex = new TransferIndex(Integer.parseInt(ac.getArgsArray()[2]));
        String inetAddress = ac.getArgsArray()[3];
        String path = ac.getArgsArray()[4];
        long minSpeed = Long.parseLong(ac.getArgsArray()[5]);
        long maxSpeed = Long.parseLong(ac.getArgsArray()[6]);
        Transfer t = getSlaveObject().getTransfer(transferIndex);
        t.setMinSpeed(minSpeed);
        t.setMaxSpeed(maxSpeed);
        sendResponse(new AsyncResponse(ac.getIndex()));

        // calling thread on master
        try {
            return new AsyncResponseTransferStatus(t.sendFile(path, type, position, inetAddress));
        } catch (IOException | TransferDeniedException e) {
            return new AsyncResponseTransferStatus(new TransferStatus(t
                    .getTransferIndex(), e));
        }
    }

    // CHECKSUM
    public AsyncResponse handleChecksum(AsyncCommandArgument ac) {
        try {
            return new AsyncResponseChecksum(ac.getIndex(), getSlaveObject().checkSum(ac.getArgs()));
        } catch (IOException e) {
            return new AsyncResponseException(ac.getIndex(), e);
        }
    }

    // SHUTDOWN
    public AsyncResponse handleShutdown(AsyncCommandArgument ac) {
        logger.info("The master has requested that I shutdown");
        getSlaveObject().shutdown();
        System.exit(0);
        return null;
    }

    // CHECK SSL
    public AsyncResponse handleCheckSSL(AsyncCommandArgument ac) {
        return new AsyncResponseSSLCheck(ac.getIndex(), getSlaveObject().getSSLContext() != null);
    }

    private void updateDepth(String path) {
        // Hack to ensure we do not use double //...
        if (path.equalsIgnoreCase("//")) {
            path = "/";
        }
        logger.debug("updateDepth - Checking [{}]", path);
        boolean add = true;
        synchronized (mergeDepthWaitObj) {
            for (String dir : mergeDepth) {
                if (path.startsWith(dir)) {
                    dir = path;
                    add = false;
                    break;
                }

                if (dir.startsWith(path)) {
                    add = false;
                    break;
                }
            }

            if (add) {
                logger.debug("updateDepth - Adding [{}]", path);
                mergeDepth.add(path);
            }
        }
    }

    private class HandleRemergeThread implements Runnable {
        private RootCollection _rootCollection = null;
        private String _path = null;
        private boolean _partialRemerge = false;
        private long _skipAgeCutoff = 0L;

        public HandleRemergeThread(RootCollection rootCollection, String path, boolean partialRemerge, long skipAgeCutoff) {
            this._rootCollection = rootCollection;
            this._path = path;
            this._partialRemerge = partialRemerge;
            this._skipAgeCutoff = skipAgeCutoff;
        }

        public void run() {
            Thread currThread = Thread.currentThread();

            // Remerge depth???

            // Sanity check
            if (!getSlaveObject().isOnline()) {
                // Slave has shut down, no need to continue with remerge
                return;
            }

            // Give the thread a logical name
            currThread.setName("Remerge Thread["+ currThread.getId() + "] - Processing " + _path);
            while (remergePaused.get() && getSlaveObject().isOnline()) {
                logger.debug("Remerging paused, sleeping");
                synchronized (remergeWaitObj) {
                    try {
                        remergeWaitObj.wait(1000);
                    } catch (InterruptedException e) {
                        // Either we have been woken properly in which case we will exit the
                        // loop or we have not in which case we will wait again.
                    }
                }
            }

            // Get a list of contents
            Set<String> inodes = _rootCollection.getLocalInodes(_path, getSlaveObject().concurrentRootIteration());
            List<LightRemoteInode> fileList = new ArrayList<LightRemoteInode>();
            ArrayList<String> dirList = new ArrayList<>();

            boolean inodesModified = false;
            long pathLastModified = _rootCollection.getLastModifiedForPath(_path);
            // Need to check the last modified of the parent itself to detect where
            // files have been deleted but none changed or added
            if (_partialRemerge && pathLastModified > _skipAgeCutoff) {
                inodesModified = true;
            }
            for (String inode : inodes) {
                String fullPath = _path + "/" + inode;
                if (_path.endsWith("/")) {
                    fullPath = _path + inode;
                }
                PhysicalFile file;
                try {
                    file = _rootCollection.getFile(fullPath);
                } catch (FileNotFoundException e) {
                    // something is screwy, we just found the file, it has to exist
                    // race condition i guess, stop deleting files outside drftpd!
                    logger.error("Error getting file {} even though we just listed it, check permissions", fullPath, e);
                    sendResponse(new AsyncResponseSiteBotMessage("Error getting file " + fullPath + " check permissions"));
                    continue;
                }
                try {
                    if (file.isSymbolicLink()) {
                        // ignore it, but log an error
                        logger.warn("You have a symbolic link {} -- these are ignored by drftpd", fullPath);
                        sendResponse(new AsyncResponseSiteBotMessage("You have a symbolic link " + fullPath + " -- these are ignored by drftpd"));
                        continue;
                    }
                } catch (IOException e) {
                    logger.warn("You have a symbolic link that couldn't be read at {} -- these are ignored by drftpd", fullPath);
                    sendResponse(new AsyncResponseSiteBotMessage("You have a symbolic link thacouldn't be read at " + fullPath + " -- these are ignored by drftpd"));
                    continue;
                }
                if (_partialRemerge && file.lastModified() > _skipAgeCutoff) {
                    inodesModified = true;
                }
                if (file.isDirectory()) {
                    dirList.add(fullPath + "/");
                    _pool.execute(new HandleRemergeThread(_rootCollection, fullPath, _partialRemerge, _skipAgeCutoff));
                }
                fileList.add(new LightRemoteInode(file));
            }
            if (!_partialRemerge || inodesModified) {
                synchronized(remergeResponses) {
                    remergeResponses.add(new RemergeItem(dirList, new AsyncResponseRemerge(_path, fileList, pathLastModified)));
                    // remergeResponses.add(new AsyncResponseRemerge(_path, fileList, pathLastModified, dirList));
                }
            } else {
                updateDepth(_path + "/");
                logger.debug("Skipping send of {} as no files changed since last merge", _path);
            }
            currThread.setName(RemergeThreadFactory.getIdleThreadName(currThread.getId()));
        }
    }

    static class RemergeThreadFactory implements ThreadFactory {
        public static String getIdleThreadName(long threadId) {
            return "Remerge Thread[" + threadId + "] - Waiting for path to process";
        }

        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setPriority(Thread.MIN_PRIORITY);
            t.setName(getIdleThreadName(t.getId()));
            return t;
        }
    }

    static class RemergeItem {
        private final List<String> _dd;
        private final AsyncResponseRemerge _arr;

        public RemergeItem(List<String> dd, AsyncResponseRemerge arr) {
            _dd = dd;
            _arr = arr;
        }

        public List<String> getDepthDirectories() {
            return _dd;
        }

        public AsyncResponseRemerge getAsyncResponseRemerge() {
            return _arr;
        }
    }
}
