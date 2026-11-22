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
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.network.PassiveConnection;
import org.drftpd.common.slave.ConnectInfo;
import org.drftpd.common.slave.LightRemoteInode;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.slave.TransferStatus;
import org.drftpd.slave.network.*;
import org.drftpd.slave.vfs.Root;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private static final AtomicBoolean remergePaused = new AtomicBoolean(false);
    private static final AtomicBoolean _remerging = new AtomicBoolean(false);
    private static final Object remergeWaitObj = new Object();

    public BasicHandler(SlaveProtocolCentral central) {
        super(central);
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
        if (!_remerging.compareAndSet(false, true)) {
            logger.warn("Received remerge request while we are remerging");
            return new AsyncResponseException(ac.getIndex(), new Exception("Already remerging"));
        }
        try {
            // Slave Protocol central calls this with a dedicated thread which we give the lowest possible priority
            Thread.currentThread().setPriority(Thread.MIN_PRIORITY);

            // Get the arguments for this command
            String[] argsArray = ac.getArgsArray();
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
                remergeDecision = "Instant online: disabled, Partial remerge: enabled. skipping all files last modified before " + cutoffDate.toString() + ".";
            } else if (instantOnline) {
                remergeDecision = "Instant online: enabled, Partial remerge: disabled. Remerging in background.";
            } else {
                remergeDecision = "Instant online: disabled, Partial remerge: disabled. Remerging in foreground.";
            }
            logger.info(remergeDecision);
            sendResponse(new AsyncResponseSiteBotMessage(remergeDecision));

            logger.debug("Remerging start");

            var roots = getSlaveObject().getRoots().getRootList();

            SortedSet<Map.Entry<String, Root.DirectoryContent>> sortedInodeSet = new TreeSet<Map.Entry<String, Root.DirectoryContent>>(new Comparator<Map.Entry<String, Root.DirectoryContent>>() {
                @Override
                public int compare(Map.Entry<String, Root.DirectoryContent> o1, Map.Entry<String, Root.DirectoryContent> o2) {
                    var leftKey = o1.getKey();
                    long leftKeySeparatorCount = leftKey.codePoints().filter(ch -> ch == File.separatorChar).count();
                    var leftValue = o1.getValue();

                    var rightKey = o2.getKey();
                    long rightKeySeparatorCount = rightKey.codePoints().filter(ch -> ch == File.separatorChar).count();
                    var rightValue = o2.getValue();

                    if (leftKeySeparatorCount < rightKeySeparatorCount) {
                        return 1;
                    }
                    else if (leftKeySeparatorCount > rightKeySeparatorCount) {
                        return -1;
                    }
                    else if (leftValue.dirAttributes.isDirectory() && !rightValue.dirAttributes.isDirectory()) {
                        return 1;
                    }
                    else if (!leftValue.dirAttributes.isDirectory() && rightValue.dirAttributes.isDirectory()) {
                        return -1;
                    } else {
                        return leftKey.compareToIgnoreCase(rightKey);
                    }
                }
            });
        
            TreeMap<String, Root.DirectoryContent> inodeTree = new TreeMap<>();
            for (Root root : roots) {
                logger.debug("Getting file list for root {}", root);
                root.getAllInodes(inodeTree, () -> !getSlaveObject().isOnline());
            }

            var remergeItems = new LinkedList<AsyncResponseRemerge>();
            
            inodeTree.forEach((dir, dirContent) -> {
                var node = new AbstractMap.SimpleEntry<String, Root.DirectoryContent>(dir, dirContent);
                sortedInodeSet.add(node);
            });

            final long cutoff = skipAgeCutoff;
            // master expects results depth first, sortedInodeSet's comparator handles it
            sortedInodeSet.forEach((node) -> {
                String dir = node.getKey();
                if (dir == "") {
                    dir = "/";
                }
                Root.DirectoryContent dirContent = node.getValue();

                // Create a sorted list of inodes
                // -directories before files
                // -ordered alphabetically, case insensitive
                List<LightRemoteInode> sortedInodes = new ArrayList<LightRemoteInode>();
                dirContent.inodes.forEach((name, attr) -> {
                    var inode = new LightRemoteInode(
                        name,
                        "drftpd",
                        "drftpd",
                        attr.isDirectory(),
                        attr.lastModifiedTime().toMillis(),
                        attr.size()
                    );
    
                    sortedInodes.add(inode);
                });

                sortedInodes.sort(new Comparator<LightRemoteInode>() {
                    public int compare(LightRemoteInode o1, LightRemoteInode o2) {
                        if (o1.isDirectory() && !o2.isDirectory()) {
                            return -1;
                        }
                        else if (!o1.isDirectory() && o2.isDirectory()) {
                            return 1;
                        } else {
                            return String.CASE_INSENSITIVE_ORDER.compare(o1.getName(), o2.getName());
                        }
                    }
                });

                var lastModified = dirContent.dirAttributes.lastModifiedTime().toMillis();
                if (partialRemerge && lastModified <= cutoff) {
                    logger.trace("Partial remerge skipping {}, lastModified {} <= cutoff {}", dir, lastModified, cutoff);
                }
                else
                {
                    var arr = new AsyncResponseRemerge(dir, sortedInodes, lastModified);
                    remergeItems.add(arr);
                }
            });

            for (var remergeItem : remergeItems) {
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

                if (!getSlaveObject().isOnline()) {
                    // Slave has shut down, no need to continue with remerge
                    return null;
                }

                logger.trace("Sending {} to the master", remergeItem.getPath());
                sendResponse(remergeItem);
            }

            logger.debug("Remerging done");
            return new AsyncResponse(ac.getIndex());
        } catch (Throwable e) {
            logger.error("Exception during merging", e);
            sendResponse(new AsyncResponseSiteBotMessage("Exception during merging"));

            return new AsyncResponseException(ac.getIndex(), e);
        } finally {
            _remerging.set(false);
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
