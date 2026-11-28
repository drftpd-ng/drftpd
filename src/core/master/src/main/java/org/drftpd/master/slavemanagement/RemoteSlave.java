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

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.network.AsyncCommand;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.protocol.ProtocolException;
import org.drftpd.common.slave.ConnectInfo;
import org.drftpd.common.slave.DiskStatus;
import org.drftpd.common.slave.TransferIndex;
import org.drftpd.common.slave.TransferStatus;
import org.drftpd.common.util.HostMaskCollection;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.SlaveEvent;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.io.SafeFileOutputStream;
import org.drftpd.master.network.RemoteTransfer;
import org.drftpd.master.stats.ExtendedTimedStats;
import org.drftpd.master.usermanager.Entity;
import org.drftpd.master.vfs.CommitManager;
import org.drftpd.master.vfs.Commitable;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.slave.network.*;
import org.drftpd.slave.protocol.QueuedOperation;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class RemoteSlave extends ExtendedTimedStats implements Runnable, Comparable<RemoteSlave>, Entity, Commitable {

    public static final Key<Boolean> SSL = new Key<>(RemoteSlave.class, "ssl");
    private static final Logger logger = LogManager.getLogger(RemoteSlave.class);
    public transient AtomicBoolean _remergePaused;
    protected transient int _errors;
    protected transient long _lastNetworkError;
    private transient boolean _isAvailable;
    private transient boolean _isRemerging;
    private transient boolean _remergeChecksums;
    private transient int _prevSocketTimeout;
    private transient volatile long _lastDownloadSending = 0L;
    private transient volatile long _lastUploadReceiving = 0L;
    private transient long _lastResponseReceived = System.currentTimeMillis();
    private transient long _lastCommandSent = System.currentTimeMillis();
    private transient long _lastRemergeCommandReceived = 0L;
    private final String _name;
    private transient DiskStatus _status;
    private HostMaskCollection _ipMasks;
    private Properties _keysAndValues;
    private final transient KeyedMap<Key<?>, Object> _transientKeyedMap;
    private ConcurrentLinkedDeque<QueuedOperation> _renameQueue;
    private transient LinkedBlockingDeque<String> _indexPool;
    private transient ConcurrentHashMap<String, AsyncResponse> _indexWithCommands;
    // Indexes whose caller gave up waiting without dropping the slave (see fetchSystemInfo).
    // Guarded by itself together with the insertion into _indexWithCommands.
    private final transient Set<String> _abandonedIndexes = new HashSet<>();
    // Indexes of pings the reader gave up on; recycled when the late reply arrives. Guarded by _abandonedIndexes.
    private final transient Set<String> _abandonedPingIndexes = new HashSet<>();
    // True from the moment a sysinfo request is issued until its reply has been consumed or discarded, so at
    // most one sysinfo is in flight per slave. Guarded by _abandonedIndexes.
    private transient boolean _sysInfoPending;
    // Completed with the outcome by the caller that issued the request, so concurrent callers share it. Non-null
    // only while that caller is still waiting; pending without a future means the request was abandoned.
    // Guarded by _abandonedIndexes.
    private transient CompletableFuture<SystemInfoReply> _sysInfoFuture;
    // Last successful sysinfo reply, reused for SYSINFO_CACHE_MILLIS. Guarded by _abandonedIndexes.
    private transient SystemInfoReply _sysInfoCache;
    private transient long _sysInfoCacheTime;
    // Incremented each time the index pool is rebuilt for a new connection (see resetIndexes). A thread waiting for
    // a reply compares it with the value it saw when it issued its request: if it changed, its index belongs to the
    // previous connection and must not be abandoned, recycled or matched against the rebuilt pool. Only written
    // while holding _abandonedIndexes, together with _socket, _sin and _sout (see connect and setOfflineReal).
    private transient volatile long _connectionGeneration;
    // Passed to setOfflineReal by the callers that mean whatever connection is current.
    private static final long CURRENT_CONNECTION = -1L;
    private transient volatile ObjectInputStream _sin;
    private transient volatile Socket _socket;
    private transient volatile ObjectOutputStream _sout;
    private transient ConcurrentHashMap<TransferIndex, RemoteTransfer> _transfers;
    private transient boolean _initRemergeCompleted;
    private final transient Object _commandMonitor;
    private static final long SYSINFO_CACHE_MILLIS = 3000L;
    private final transient LinkedBlockingQueue<RemergeMessage> _remergeQueue;
    private final transient LinkedBlockingQueue<FileHandle> _crcQueue;
    private transient RemergeThread _remergeThread;
    private transient CrcThread _crcThread;
    private transient volatile String _tlsProtocol;
    private transient volatile String _tlsCipherSuite;
    private transient volatile String _remoteAddress;

    public RemoteSlave(String name) {
        _name = name;
        _keysAndValues = new Properties();
        _transientKeyedMap = new KeyedMap<>();
        _ipMasks = new HostMaskCollection();
        _renameQueue = new ConcurrentLinkedDeque<>();
        _remergePaused = new AtomicBoolean();
        _remergeQueue = new LinkedBlockingQueue<>();
        _crcQueue = new LinkedBlockingQueue<>();
        _commandMonitor = new Object();
    }

    /**
     * TimeManager runs only resetMonth() on the 1st of a month and only resetYear() on 1 January, so each of them
     * has to cover the shorter periods too (like AbstractUser does), or those counters carry over.
     */
    @Override
    public void resetMonth(Date resetDate) {
        super.resetMonth(resetDate);
        super.resetDay(resetDate);
        super.resetHour(resetDate);
    }

    @Override
    public void resetYear(Date resetDate) {
        super.resetYear(resetDate);
        super.resetMonth(resetDate);
        super.resetDay(resetDate);
        super.resetHour(resetDate);
    }

    // Public API returning a Hashtable, kept as is for plugin compatibility.
    public static Hashtable<String, RemoteSlave> rslavesToHashtable(Collection<RemoteSlave> rslaves) {
        Hashtable<String, RemoteSlave> map = new Hashtable<>(rslaves.size());

        for (RemoteSlave rslave : rslaves) {
            map.put(rslave.getName(), rslave);
        }

        return map;
    }

    public static String getSlaveNameFromObjectInput(ObjectInputStream in)
            throws IOException {
        try {
            return (String) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void addMask(String mask) throws DuplicateElementException {
        _ipMasks.addMask(mask);
        commit();
    }

    /**
     * If X # of errors occur in Y amount of time, kick slave offline
     */
    public final void addNetworkError(SocketException e) {
        // set slave offline if too many network errors
        long errortimeout = Long
                .parseLong(getProperty("errortimeout", "60000")); // one
        // minute

        if (errortimeout <= 0) {
            errortimeout = 60000;
        }

        int maxerrors = Integer.parseInt(getProperty("maxerrors", "5"));

        if (maxerrors < 0) {
            maxerrors = 5;
        }

        _errors -= ((System.currentTimeMillis() - _lastNetworkError) / errortimeout);

        if (_errors < 0) {
            _errors = 0;
        }

        _errors++;
        _lastNetworkError = System.currentTimeMillis();

        if (_errors > maxerrors) {
            setOffline("Too many network errors - " + e.getMessage());
            logger.error("Too many network errors - {}", e);
        }
    }

    protected void addQueueDelete(String fileName) {
        addQueueRename(fileName, null);
    }

    protected void addQueueRename(String fileName, String destName) {
        if (isOnline()) {
            throw new IllegalStateException(
                    "Slave is online, you cannot queue an operation");
        }
        _renameQueue.add(new QueuedOperation(fileName, destName));
        commit();
    }

    public void setProperty(String name, String value) {
        _keysAndValues.setProperty(name, value);
        commit();
    }

    public String getProperty(String name, String def) {
        return _keysAndValues.getProperty(name, def);
    }

    public Properties getProperties() {
        return (Properties) _keysAndValues.clone();
    }

    /**
     * Needed in order for this class to be a Bean
     */
    public void setProperties(Properties keysAndValues) {
        _keysAndValues = keysAndValues;
    }

    public KeyedMap<Key<?>, Object> getTransientKeyedMap() {
        return _transientKeyedMap;
    }

    public void commit() {
        CommitManager.getCommitManager().writeImmediately(this);
    }

    public final int compareTo(RemoteSlave o) {
        return getName().compareTo(o.getName());
    }

    public final boolean equals(Object obj) {
        return obj instanceof RemoteSlave && ((RemoteSlave) obj).getName().equals(getName());
    }

    public GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }

    public final long getLastDownloadSending() {
        return _lastDownloadSending;
    }

    public final void setLastDownloadSending(long lastDownloadSending) {
        _lastDownloadSending = lastDownloadSending;
    }

    public final long getLastTransfer() {
        return Math.max(getLastDownloadSending(), getLastUploadReceiving());
    }

    public long getLastTransferForDirection(char dir) {
        if (dir == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            return getLastUploadReceiving();
        } else if (dir == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            return getLastDownloadSending();
        } else if (dir == Transfer.TRANSFER_UNKNOWN) {
            return getLastTransfer();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public final long getLastUploadReceiving() {
        return _lastUploadReceiving;
    }

    public final void setLastUploadReceiving(long lastUploadReceiving) {
        _lastUploadReceiving = lastUploadReceiving;
    }

    public HostMaskCollection getMasks() {
        return _ipMasks;
    }

    public void setMasks(HostMaskCollection masks) {
        _ipMasks = masks;
    }

    /**
     * Returns the name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Returns the RemoteSlave's saved SlaveStatus, can return a status before
     * remerge() is completed
     */
    public SlaveStatus getSlaveStatus() throws SlaveUnavailableException {
        if ((_status == null) || !isOnline()) {
            throw new SlaveUnavailableException();
        }
        int throughputUp = 0;
        int throughputDown = 0;
        int transfersUp = 0;
        int transfersDown = 0;
        long bytesReceived;
        long bytesSent;

        bytesReceived = getReceivedBytes();
        bytesSent = getSentBytes();

        for (RemoteTransfer transfer : _transfers.values()) {
            switch (transfer.getTransferDirection()) {
                case Transfer.TRANSFER_RECEIVING_UPLOAD:
                    throughputUp += transfer.getXferSpeed();
                    bytesReceived += transfer.getTransfered();
                    transfersUp += 1;
                    break;

                case Transfer.TRANSFER_SENDING_DOWNLOAD:
                    throughputDown += transfer.getXferSpeed();
                    transfersDown += 1;
                    bytesSent += transfer.getTransfered();
                    break;

                case Transfer.TRANSFER_UNKNOWN:
                    break;

                default:
                    throw new FatalException("unrecognized direction - "
                            + transfer.getTransferDirection() + " for " + transfer);
            }
        }

        return new SlaveStatus(_status, bytesSent, bytesReceived, throughputUp,
                transfersUp, throughputDown, transfersDown);
    }

    public long getSentBytes() {
        return Long.parseLong(getProperty("bytesSent", "0"));
    }

    public long getReceivedBytes() {
        return Long.parseLong(getProperty("bytesReceived", "0"));
    }

    /**
     * Returns the RemoteSlave's stored SlaveStatus, will not return a status
     * before remerge() is completed
     */
    public SlaveStatus getSlaveStatusAvailable()
            throws SlaveUnavailableException {
        if (isAvailable()) {
            return getSlaveStatus();
        }

        throw new SlaveUnavailableException("Slave is not online");
    }

    public final int hashCode() {
        return getName().hashCode();
    }

    /**
     * Called when the slave connects
     *
     * @throws ProtocolException
     */
    private void initializeSlaveAfterThreadIsRunning(long generation) throws IOException,
            SlaveUnavailableException, ProtocolException {
        commit();
        processQueue(generation);

        // checking ssl availability
        String checkSSLIndex = SlaveManager.getBasicIssuer().issueCheckSSL(this);
        try {
            getTransientKeyedMap().setObject(SSL,
                    ((AsyncResponseSSLCheck) fetchResponse(checkSSLIndex, getActualTimeout(), generation)).isSSLReady());
        } catch (RemoteIOException e) {
            throw new FatalException("Slave had an error processing the ssl check");
        }

        long skipAgeCutoff = 0L;

        String remergeMode = getPartialRemergeMode();
        _remergeChecksums = GlobalContext.getConfig().getMainProperties().
                getProperty("enableremergechecksums", "false").equalsIgnoreCase("true");
        boolean partialRemerge = false;
        boolean instantOnline = false;
        if (remergeMode == null) {
            logger.error("Slave partial remerge undefined in master.conf, defaulting to \"off\"");
        } else {
            if (remergeMode.equalsIgnoreCase("connect")) {
                try {
                    skipAgeCutoff = Long.parseLong(getProperty("lastConnect"));
                    partialRemerge = true;
                } catch (NumberFormatException e) {
                    logger.warn("Slave partial remerge mode set to \"off\" as lastConnect time is undefined, this may " +
                            " resolve itself automatically on next slave connection");
                }
            } else if (remergeMode.equalsIgnoreCase("disconnect")) {
                try {
                    skipAgeCutoff = Long.valueOf(getProperty("lastOnline"));
                    partialRemerge = true;
                } catch (NumberFormatException e) {
                    logger.warn("Slave partial remerge mode set to \"off\" as lastOnline time is undefined, this may " +
                            " resolve itself automatically on next slave connection");
                }
            } else if (remergeMode.equalsIgnoreCase("instant")) {
                instantOnline = true;
                setAvailable(true);
                logger.info("Slave added: '{}' status: {}", getName(), _status);
                GlobalContext.getEventService().publishAsync(new SlaveEvent("ADDSLAVE", this));
            }
        }
        String remergeIndex;
        if (partialRemerge) {
            remergeIndex = SlaveManager.getBasicIssuer().issueRemergeToSlave(this, "/", true, skipAgeCutoff, System.currentTimeMillis(), false);
        } else if (instantOnline) {
            remergeIndex = SlaveManager.getBasicIssuer().issueRemergeToSlave(this, "/", false, 0L, 0L, true);
        } else {
            remergeIndex = SlaveManager.getBasicIssuer().issueRemergeToSlave(this, "/", false, 0L, 0L, false);
        }

        try {
            fetchResponse(remergeIndex, 0, generation);
        } catch (RemoteIOException e) {
            throw new IOException(e.getMessage());
        }

        setCRCThreadFinished();
        putRemergeQueue(new RemergeMessage(this));

        if (_remergePaused.get()) {
            String message = ("Remerge was paused on slave after completion, issuing resume so not to break manual remerges");
            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, this));
            logger.debug("Remerge was paused on slave after completion, issuing resume so not to break manual remerges");
            SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
            _remergePaused.set(false);
        }
    }

    /**
     * @return true if the slave has synchronized its filelist since last
     * connect
     */
    public boolean isAvailable() {
        return _isAvailable;
    }

    public void setAvailable(boolean available) {
        _isAvailable = available;
    }

    public boolean isAvailablePing() {
        if (!isAvailable()) {
            return false;
        }

        long generation = getConnectionGeneration();
        try {
            String index = SlaveManager.getBasicIssuer().issuePingToSlave(this);
            fetchResponse(index, getActualTimeout(), generation);
        } catch (SlaveUnavailableException e) {
            setOffline(e, generation);
            return false;
        } catch (RemoteIOException e) {
            setOffline("The slave encountered an IOException while running ping...this is almost not possible",
                    generation);
            return false;
        }

        return isAvailable();
    }

    /**
     * @return true if the slave is online but a slave remerge is running
     */
    public boolean isRemerging() {
        return _isRemerging;
    }

    public void setRemerging(boolean remerging) {
        _isRemerging = remerging;
    }

    /**
     * @return true if CRC is to be added on remerge for files missing CRC in VFS
     */
    public boolean remergeChecksums() {
        return _remergeChecksums;
    }

    public void processQueue() throws IOException, SlaveUnavailableException {
        processQueue(getConnectionGeneration());
    }

    private void processQueue(long generation) throws IOException, SlaveUnavailableException {
        QueuedOperation item = null;
        while ((item = _renameQueue.poll()) != null) {
            String sourceFile = item.getSource();
            String destFile = item.getDestination();

            if (destFile == null) { // delete
                try {
                    fetchResponse(SlaveManager.getBasicIssuer().issueDeleteToSlave(this, sourceFile), 300000,
                            generation);
                } catch (RemoteIOException e) {
                    if (!(e.getCause() instanceof FileNotFoundException)) {
                        throw e.getCause();
                    }
                } finally {
                    commit();
                }
            } else { // rename
                String fileName = destFile.substring(destFile.lastIndexOf("/") + 1);
                String destDir = destFile.substring(0, destFile.lastIndexOf("/"));
                try {
                    fetchResponse(SlaveManager.getBasicIssuer().issueRenameToSlave(this, sourceFile, destDir, fileName),
                            getActualTimeout(), generation);
                } catch (RemoteIOException e) {
                    if (!(e.getCause() instanceof FileNotFoundException)) {
                        throw e.getCause();
                    }
                } finally {
                    commit();
                }
            }
        }
    }

    /**
     * @return true if the mask was removed successfully
     */
    public final boolean removeMask(String mask) {
        boolean ret = _ipMasks.removeMask(mask);

        if (ret) {
            commit();
        }

        return ret;
    }

    /**
     * @return the configured partial.remerge.mode, or null if the key is absent from master.conf
     * (callers treat null as "off", the value shipped in master.conf.dist).
     */
    private static String getPartialRemergeMode() {
        String mode = GlobalContext.getConfig().getMainProperties().getProperty("partial.remerge.mode");
        return mode == null ? null : mode.trim();
    }

    /**
     * Null-safe: an absent partial.remerge.mode means "off", not instant.
     */
    static boolean isInstantRemerge(String remergeMode) {
        return "instant".equalsIgnoreCase(remergeMode);
    }

    protected void makeAvailableAfterRemerge() {
        _initRemergeCompleted = true;
        setProperty("lastConnect", Long.toString(System.currentTimeMillis()));
        if (isInstantRemerge(getPartialRemergeMode())) {
            setRemerging(false);
            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", "Remerge queueprocess finished", this));
        } else {
            setAvailable(true);
            setRemerging(false);
            logger.info("Slave added: '{}' status: {}", getName(), _status);
            GlobalContext.getEventService().publishAsync(new SlaveEvent("ADDSLAVE", this));
        }
    }

    public final void setLastDirection(char direction, long l) {
        switch (direction) {
            case Transfer.TRANSFER_RECEIVING_UPLOAD -> {
                setLastUploadReceiving(l);
                return;
            }
            case Transfer.TRANSFER_SENDING_DOWNLOAD -> {
                setLastDownloadSending(l);
                return;
            }
            default -> throw new IllegalArgumentException();
        }
    }

    /**
     * Deletes files/directories and waits for the response Meant to be used if
     * you don't want to utilize asynchronization
     */
    public void simpleDelete(String path) {
        for (int attempt = 1; ; attempt++) {
            long generation = getConnectionGeneration();
            try {
                fetchResponse(issueDelete(path), 300000, generation);
                return;
            } catch (RemoteIOException e) {
                if (e.getCause() instanceof FileNotFoundException) {
                    return;
                }

                setOffline("IOException deleting file, check logs for specific error", generation);
                logger.error("IOException deleting file, file will be deleted when slave comes online", e);
            } catch (SlaveUnavailableException e) {
                // offline (the delete is queued below), or the slave reconnected while we waited
            }
            if (queueIfOffline(path, null)) {
                // Already offline and we ARE successful in deleting the file
                return;
            }
            if (attempt == 2) {
                logger.error("Could not delete {} on slave {}, which is online again", path, getName());
                return;
            }
            // the command was lost with the previous connection: once more on the current one
        }
    }

    /**
     * Queues a delete (destination null) or rename for the next connect if the slave is offline.
     *
     * @return false if the slave is online, so nothing was queued
     */
    private boolean queueIfOffline(String source, String destination) {
        if (isOnline()) {
            return false;
        }
        try {
            addQueueRename(source, destination);
            return true;
        } catch (IllegalStateException e) {
            // it came back online in the meantime
            return false;
        }
    }

    /** Sends a delete to the slave and returns its index. Seam for tests. */
    String issueDelete(String path) throws SlaveUnavailableException {
        return SlaveManager.getBasicIssuer().issueDeleteToSlave(this, path);
    }

    /** Sends a rename to the slave and returns its index. Seam for tests. */
    String issueRename(String from, String toDirPath, String toName) throws SlaveUnavailableException {
        return SlaveManager.getBasicIssuer().issueRenameToSlave(this, from, toDirPath, toName);
    }

    /**
     * Renames files/directories and waits for the response
     * NOTE: We allow the destination to exist in VFS and expect the slave to 'merge' it
     */
    public void simpleRename(String from, String toDirPath, String toName) {
        String simplePath;
        if (toDirPath.endsWith("/")) {
            simplePath = toDirPath + toName;
        } else {
            simplePath = toDirPath + "/" + toName;
        }
        for (int attempt = 1; ; attempt++) {
            long generation = getConnectionGeneration();
            try {
                fetchResponse(issueRename(from, toDirPath, toName), getActualTimeout(), generation);
                return;
            } catch (RemoteIOException e) {
                setOffline(e, generation);
            } catch (SlaveUnavailableException e) {
                // offline (the rename is queued below), or the slave reconnected while we waited
            }
            if (queueIfOffline(from, simplePath)) {
                return;
            }
            if (attempt == 2) {
                logger.error("Could not rename {} to {} on slave {}, which is online again", from, simplePath,
                        getName());
                return;
            }
            // the command was lost with the previous connection: once more on the current one
        }
    }

    public String toString() {
        return moreInfo();
    }

    /**
     * Rebuilds the pool of command indexes and forgets everything that was in flight.
     *
     * @return the generation of the new connection
     */
    long resetIndexes() {
        // All of it under the lock: whoever pushes an index back (a waiter, the reader recycling its ping index)
        // checks the generation under this lock, so it either runs before the increment, on the old pool that is
        // about to be cleared, or sees the new generation and leaves the rebuilt pool alone. A fetchIndex() that
        // overlaps the rebuild may get an index of either pool, but its caller read the generation before and
        // sees that it changed.
        synchronized (_abandonedIndexes) {
            _connectionGeneration++;
            if (_indexPool == null) {
                _indexPool = new LinkedBlockingDeque<>(256);
            } else {
                _indexPool.clear();
            }

            for (int i = 0; i < 256; i++) {
                String key = Integer.toHexString(i);

                if (key.length() < 2) {
                    key = "0" + key;
                }

                _indexPool.push(key);
            }

            if (_indexWithCommands == null) {
                _indexWithCommands = new ConcurrentHashMap<>();
            } else {
                _indexWithCommands.clear();
            }
            _abandonedIndexes.clear();
            _abandonedPingIndexes.clear();
            _sysInfoPending = false;
            _sysInfoCache = null;
            return _connectionGeneration;
        }
    }

    /**
     * The generation of the current connection, incremented on each connect. A caller that may react to the
     * failure of a command with {@link #setOffline(String, long)} reads it before issuing the command, so that a
     * failure on a previous connection does not drop the current one.
     */
    public long getConnectionGeneration() {
        return _connectionGeneration;
    }

    public synchronized void connect(Socket socket, ObjectInputStream in,
                                     ObjectOutputStream out) {
        // Extract TLS session info if this is an SSL socket
        String tlsProtocol;
        String tlsCipherSuite;
        if (socket instanceof SSLSocket sslSocket) {
            SSLSession session = sslSocket.getSession();
            tlsProtocol = session.getProtocol();
            tlsCipherSuite = session.getCipherSuite();
        } else {
            tlsProtocol = "N/A";
            tlsCipherSuite = "N/A";
        }
        String remoteAddress = socket.getInetAddress().getHostAddress();

        long generation;
        // The new streams and the new generation become visible together: a setOffline for the previous
        // connection either ran before (and tore down the previous streams) or sees the new generation and
        // leaves these alone.
        synchronized (_abandonedIndexes) {
            _socket = socket;
            _sout = out;
            _sin = in;
            _tlsProtocol = tlsProtocol;
            _tlsCipherSuite = tlsCipherSuite;
            _remoteAddress = remoteAddress;
            generation = resetIndexes();

            if (_transfers == null) {
                _transfers = new ConcurrentHashMap<>();
            } else {
                _transfers.clear();
            }
        }

        _errors = 0;
        _lastNetworkError = System.currentTimeMillis();
        _initRemergeCompleted = false;
        setRemerging(true);

        try {
            GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().handshakeWithSlave(this);
        } catch (ProtocolException e) {
            setOffline(e, generation);
        }

        class InitiateRemergeThread implements Runnable {
            public void run() {
                try {
                    initializeSlaveAfterThreadIsRunning(generation);
                } catch (Exception e) {
                    setOffline(e, generation);
                }
            }
        }

        new Thread(new InitiateRemergeThread(), "RemoteSlaveRemerge - " + getName()).start();
        start(in, socket, generation);
    }

    /** Starts the reader thread of the connection of this generation; it only ever reads that connection's stream. */
    private void start(ObjectInputStream in, Socket socket, long generation) {
        Thread t = new Thread(() -> run(in, socket, generation));
        t.setName("RemoteSlave - " + getName());
        t.start();
    }

    public long fetchChecksumFromIndex(String index) throws RemoteIOException,
            SlaveUnavailableException {
        return ((AsyncResponseChecksum) fetchResponse(index)).getChecksum();
    }

    public String fetchIndex() throws SlaveUnavailableException {
        String index;
        while (isOnline()) {
            try {
                index = _indexPool.poll(1000, TimeUnit.MILLISECONDS);
                if (index == null) {
                    logger.error("Too many commands sent, need to wait for the slave to process commands");
                } else {
                    return index;
                }
                if (getActualTimeout() < (System.currentTimeMillis() - _lastResponseReceived)) {
                    setOffline("Index pool exhausted and no response from slave in "
                            + (System.currentTimeMillis() - _lastResponseReceived)
                            + " milliseconds");
                    throw new SlaveUnavailableException();
                }
            } catch (InterruptedException e1) {
            }
        }

        throw new SlaveUnavailableException("Slave was offline or went offline while fetching an index");
    }

    public int fetchMaxPathFromIndex(String maxPathIndex) throws SlaveUnavailableException {
        try {
            return ((AsyncResponseMaxPath) fetchResponse(maxPathIndex)).getMaxPath();
        } catch (RemoteIOException e) {
            throw new FatalException("Slave had an error processing maxpath");
        }
    }

    public boolean fetchCheckSSLFromIndex(String sslIndex) throws SlaveUnavailableException {
        try {
            return ((AsyncResponseSSLCheck) fetchResponse(sslIndex)).isSSLReady();
        } catch (RemoteIOException e) {
            throw new FatalException("Slave had an error processing the ssl check");
        }
    }

    /**
     * Asks the slave for its hardware / system information.
     * <p>
     * Unlike {@link #fetchResponse(String, int)} this never puts the slave offline: an older slave that does not
     * know the command, a slow slave or an error on the slave side are all reported in the returned
     * {@link SystemInfoReply}.
     *
     * @param timeoutMillis maximum time to wait for the reply
     * @return the reply, never null
     */
    public SystemInfoReply fetchSystemInfo(long timeoutMillis) {
        return fetchSystemInfo(timeoutMillis, () -> SlaveManager.getBasicIssuer().issueSysInfoToSlave(this));
    }

    /** Sends the sysinfo command and returns the index it was sent with. Seam for tests. */
    @FunctionalInterface
    interface SysInfoSender {
        String send() throws SlaveUnavailableException;
    }

    SystemInfoReply fetchSystemInfo(long timeoutMillis, SysInfoSender sender) {
        if (!isOnline()) {
            return SystemInfoReply.of(SystemInfoReply.Status.OFFLINE, null);
        }
        CompletableFuture<SystemInfoReply> shared;
        CompletableFuture<SystemInfoReply> mine;
        synchronized (_abandonedIndexes) {
            if (_sysInfoCache != null && System.currentTimeMillis() - _sysInfoCacheTime < SYSINFO_CACHE_MILLIS) {
                return _sysInfoCache;
            }
            shared = _sysInfoFuture;
            if (shared == null && _sysInfoPending) {
                // a previous request timed out and the slave still owes the reply: do not pile up more
                return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, "still waiting for a previous request");
            }
            if (shared == null) {
                _sysInfoPending = true;
                mine = new CompletableFuture<>();
                _sysInfoFuture = mine;
            } else {
                mine = null;
            }
        }
        if (mine == null) {
            // somebody else already asked the slave: wait for the same reply
            try {
                return shared.get(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException e) {
                return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, null);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, null);
            }
        }
        SystemInfoReply reply = SystemInfoReply.of(SystemInfoReply.Status.ERROR, "request failed");
        try {
            reply = requestSystemInfo(timeoutMillis, sender);
            return reply;
        } finally {
            synchronized (_abandonedIndexes) {
                _sysInfoFuture = null;
            }
            mine.complete(reply);
        }
    }

    /** Issues the request and waits for its reply. The caller holds the pending flag and completes the future. */
    private SystemInfoReply requestSystemInfo(long timeoutMillis, SysInfoSender sender) {
        String index;
        // captured before the index is taken: if a reconnect happens at any point after this, the index may belong
        // to either pool, so it is left alone
        long generation = _connectionGeneration;
        try {
            index = sender.send();
        } catch (SlaveUnavailableException e) {
            clearSysInfoPending();
            return SystemInfoReply.of(SystemInfoReply.Status.OFFLINE, null);
        } catch (RuntimeException e) {
            clearSysInfoPending();
            throw e;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (isOnline() && _connectionGeneration == generation && !_indexWithCommands.containsKey(index)) {
            if (System.currentTimeMillis() >= deadline) {
                synchronized (_abandonedIndexes) {
                    if (_connectionGeneration == generation && !_indexWithCommands.containsKey(index)) {
                        // the reader thread will recycle the index (and clear the pending flag) if the reply
                        // ever arrives
                        _abandonedIndexes.add(index);
                        return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, null);
                    }
                }
                break;
            }
            try {
                synchronized (_commandMonitor) {
                    // Check again while holding the monitor: the reader thread stores the response and then
                    // calls notifyAll() on this monitor, so a reply that arrived after the check in the while
                    // condition would otherwise cost a full 50 ms wait
                    if (!_indexWithCommands.containsKey(index)) {
                        _commandMonitor.wait(50);
                    }
                }
            } catch (InterruptedException e) {
                // stop waiting: the next iteration abandons the index
                Thread.currentThread().interrupt();
                deadline = 0L;
            }
        }
        AsyncResponse ar = null;
        synchronized (_abandonedIndexes) {
            // a reconnect may have rebuilt the pool, which already holds this index again
            if (_connectionGeneration == generation) {
                ar = _indexWithCommands.remove(index);
                if (ar != null) {
                    _indexPool.push(index);
                }
            }
        }
        if (ar == null) {
            // went offline (or reconnected) while waiting: the index pool is rebuilt on reconnect
            clearSysInfoPending();
            return SystemInfoReply.of(SystemInfoReply.Status.OFFLINE, null);
        }
        SystemInfoReply reply = SystemInfoReply.fromResponse(ar);
        if (reply.getStatus() == SystemInfoReply.Status.ERROR && ar instanceof AsyncResponseException) {
            logger.warn("Slave {} failed to answer sysinfo", getName(), ((AsyncResponseException) ar).getThrowable());
        }
        synchronized (_abandonedIndexes) {
            _sysInfoPending = false;
            if (reply.getStatus() == SystemInfoReply.Status.OK) {
                _sysInfoCache = reply;
                _sysInfoCacheTime = System.currentTimeMillis();
            }
        }
        return reply;
    }

    private void clearSysInfoPending() {
        synchronized (_abandonedIndexes) {
            _sysInfoPending = false;
        }
    }

    /**
     * Hands a reply read from the slave to the thread waiting for it. A late reply for an index whose caller gave
     * up waiting is dropped and its index returned to the pool instead.
     *
     * @return true if the reply was stored for a waiting caller, false if it was a discarded late reply
     */
    boolean storeResponse(AsyncResponse ar) {
        return storeResponse(ar, _connectionGeneration);
    }

    /**
     * @param generation the connection generation the reply was read on; a reply from a previous connection is
     *                   dropped, its index is not part of the current pool
     */
    boolean storeResponse(AsyncResponse ar, long generation) {
        synchronized (_abandonedIndexes) {
            if (_connectionGeneration != generation) {
                logger.debug("Discarding reply {} read on a previous connection", ar.getIndex());
                return false;
            }
            if (_abandonedIndexes.remove(ar.getIndex())) {
                // late reply for a request whose caller gave up waiting: recycle the index
                logger.debug("Discarding late reply for abandoned index {}", ar.getIndex());
                _indexPool.push(ar.getIndex());
                _sysInfoPending = false;
                return false;
            }
            if (_abandonedPingIndexes.remove(ar.getIndex())) {
                logger.debug("Discarding late reply for lost ping {}", ar.getIndex());
                _indexPool.push(ar.getIndex());
                return false;
            }
            _indexWithCommands.put(ar.getIndex(), ar);
            return true;
        }
    }

    /** Called by the reader thread after storing a response: wakes the threads waiting for a reply. */
    void wakeCommandWaiters() {
        synchronized (_commandMonitor) {
            _commandMonitor.notifyAll();
        }
    }

    public AsyncResponse fetchResponse(String index)
            throws SlaveUnavailableException, RemoteIOException {
        return fetchResponse(index, getActualTimeout());
    }

    /**
     * returns an AsyncResponse for that index and throws any exceptions thrown
     * on the Slave side
     * <p>
     * Takes the generation on entry, so a reconnect between issuing the command and this call goes unnoticed: use
     * {@link #fetchResponse(String, int, long)} with the generation read before the command was issued.
     */
    public AsyncResponse fetchResponse(String index, int wait) throws SlaveUnavailableException, RemoteIOException {
        return fetchResponse(index, wait, _connectionGeneration);
    }

    /**
     * Like {@link #fetchResponse(String, int)}, for a command issued on the connection of the given generation
     * ({@link #getConnectionGeneration()}, read before the command is issued). If the slave reconnected since, the
     * index may belong to either pool: it is left alone and {@link SlaveUnavailableException} is thrown, and a
     * timeout does not drop the new connection.
     */
    public AsyncResponse fetchResponse(String index, int wait, long generation)
            throws SlaveUnavailableException, RemoteIOException {
        long total = System.currentTimeMillis();
        long lastUpdate = 0L;
        long reportIdle = 60000L;

        while (isOnline() && _connectionGeneration == generation && !_indexWithCommands.containsKey(index)) {
            // will wait a maximum of 50 milliseconds before waking up
            // Any longer and we risk slow exchange of commands between master and slave
            try {
                synchronized (_commandMonitor) {
                    // Check again while holding the monitor: the reader thread stores the response and then
                    // calls notifyAll() on this monitor, so a response that arrived after the check in the
                    // while condition would otherwise be missed and cost a full 50 ms wait
                    if (!_indexWithCommands.containsKey(index)) {
                        _commandMonitor.wait(50);
                    }
                }
            } catch (InterruptedException e) {
                // Ignore interupt
            }

            if ((wait != 0) && ((System.currentTimeMillis() - total) >= wait)) {
                setOffline("Slave has taken too long while waiting for reply " + index, generation);
            }

            // Handle remerge status reporting if we are remerging
            if (isRemerging()) {
                // Only do something if we have not received a remerge command from slave in 15 seconds
                if ((System.currentTimeMillis() - _lastRemergeCommandReceived) >= reportIdle) {
                    if (lastUpdate >= 0L && (System.currentTimeMillis() - lastUpdate) <= reportIdle) {
                        // skip for at least 15 seconds, not spamming the log
                        continue;
                    }

                    // We have not received remerge data from slave, report current master status for this slave
                    logger.warn("We are still remerging, but have not received remerge command from Slave in {} " +
                            "seconds", (reportIdle / 1000));
                    logger.warn("remergeQueue: {}, remergePaused: {}, Last remerge command received: {}",
                            _remergeQueue.size(), _remergePaused.get(), _lastRemergeCommandReceived);

                    // Set last remerge report
                    lastUpdate = System.currentTimeMillis();

                    // Incremental back-off untill we are out of this
                    reportIdle *= 2;
                } else {
                    // reset reportIdle if we continue
                    reportIdle = 60000L;
                }
            }
        }

        if (!isOnline()) {
            throw new SlaveUnavailableException("Slave went offline while processing command");
        }

        AsyncResponse rar;
        synchronized (_abandonedIndexes) {
            if (_connectionGeneration != generation) {
                // reconnected while waiting: the rebuilt pool already holds this index, do not push it twice
                throw new SlaveUnavailableException("Slave went offline while processing command");
            }
            rar = _indexWithCommands.remove(index);
            if (rar == null) {
                // gone between the wait and here: taken by another waiter on the same index (which already put it
                // back), or cleared by setOffline. Pushing it again would put it in the pool twice.
                throw new SlaveUnavailableException("Slave went offline while processing command");
            }
            _indexPool.push(index);
        }

        if (rar instanceof AsyncResponseException) {
            Throwable t = ((AsyncResponseException) rar).getThrowable();

            if (t instanceof IOException) {
                throw new RemoteIOException((IOException) t);
            }

            logger.error("Exception on slave that is unable to be handled by the master", t);
            setOffline("Exception on slave that is unable to be handled by the master", generation);
            throw new SlaveUnavailableException("Exception on slave that is unable to be handled by the master");
        }
        return rar;
    }

    public synchronized String getPASVIP() throws SlaveUnavailableException {
        if (!isOnline())
            throw new SlaveUnavailableException();

        return getProperty("pasv_addr", _socket.getInetAddress().getHostAddress());
    }

    public int getPort() {
        return _socket.getPort();
    }

    public boolean isOnline() {
        return ((_socket != null) && _socket.isConnected());
    }

    /**
     * Returns the TLS protocol used for the connection to this slave.
     *
     * @return The TLS protocol (e.g., "TLSv1.3") or "N/A" if not available
     */
    public String getTlsProtocol() {
        return _tlsProtocol != null ? _tlsProtocol : "N/A";
    }

    /**
     * Returns the TLS cipher suite used for the connection to this slave.
     *
     * @return The cipher suite or "N/A" if not available
     */
    public String getTlsCipherSuite() {
        return _tlsCipherSuite != null ? _tlsCipherSuite : "N/A";
    }

    /**
     * Returns the remote IP address of this slave.
     *
     * @return The remote IP address or "N/A" if not available
     */
    public String getRemoteAddress() {
        return _remoteAddress != null ? _remoteAddress : "N/A";
    }

    public long getCheckSumForPath(String path) throws IOException,
            SlaveUnavailableException {
        long generation = getConnectionGeneration();
        try {
            return ((AsyncResponseChecksum) fetchResponse(SlaveManager.getBasicIssuer().issueChecksumToSlave(this, path),
                    getActualTimeout(), generation)).getChecksum();
        } catch (RemoteIOException e) {
            throw e.getCause();
        }
    }

    public String moreInfo() {
        try {
            return getName() + ":address=[" + getPASVIP() + "]port=["
                    + getPort() + "]";
        } catch (SlaveUnavailableException e) {
            return getName() + ":offline";
        }
    }

    /** Runs the reader for the current connection. connect() starts the reader with its own stream instead. */
    public void run() {
        ObjectInputStream in;
        Socket socket;
        long generation;
        synchronized (_abandonedIndexes) {
            in = _sin;
            socket = _socket;
            generation = _connectionGeneration;
        }
        run(in, socket, generation);
    }

    /**
     * The reader thread of one connection.
     *
     * @param in         the stream of that connection; never the one of a later connection
     * @param socket     the socket of that connection
     * @param generation the generation of that connection
     */
    private void run(ObjectInputStream in, Socket socket, long generation) {
        logger.debug("Starting RemoteSlave for {}", getName());

        try {
            String pingIndex = null;
            while (isOnline() && _connectionGeneration == generation) {
                AsyncResponse ar = null;

                try {
                    ar = readAsyncResponse(in, generation);
                    _lastResponseReceived = System.currentTimeMillis();
                } catch (SlaveUnavailableException e3) {
                    // no reason for slave thread to be running if the slave is not online
                    logger.warn("Slave unavailable catched while we were running, exiting");
                    return;
                } catch (SocketTimeoutException e) {
                    // handled below
                }

                if ((getActualTimeout() > (System.currentTimeMillis() - _lastResponseReceived))
                        && ((getActualTimeout() / 2 < (System.currentTimeMillis() - _lastResponseReceived))
                        || (getActualTimeout() / 2 < (System.currentTimeMillis() - _lastCommandSent)))) {
                    if (pingIndex != null) {
                        if (!abandonPingIndex(pingIndex, generation)) {
                            // the slave reconnected: the pool was rebuilt with this index, and this reader is done
                            return;
                        }
                        logger.error("Ping lost, no response from slave, sending new ping to slave");
                    }
                    if (_connectionGeneration != generation) {
                        // do not ping the new connection from the reader of the previous one
                        return;
                    }
                    pingIndex = SlaveManager.getBasicIssuer().issuePingToSlave(this);
                } else if (getActualTimeout() < (System.currentTimeMillis() - _lastResponseReceived)) {
                    setOffline("Slave seems to have gone offline, have not received a response in "
                            + (System.currentTimeMillis() - _lastResponseReceived)
                            + " milliseconds", generation);
                    throw new SlaveUnavailableException();
                }

                if (isOnline() && _connectionGeneration == generation && !_initRemergeCompleted) {
                    if (_remergePaused.get()) {
                        // Do we need to resume?
                        if (_remergeQueue.size() <= Integer.parseInt(GlobalContext.getConfig().getMainProperties().getProperty("remerge.resume.threshold", "50"))) {
                            socket.setSoTimeout(_prevSocketTimeout); // Restore old time out
                            SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
                            _remergePaused.set(false);
                            logger.debug("Issued remerge resume to slave, current remerge queue is {}", _remergeQueue.size());
                        }
                    } else {
                        // Do we need to pause?
                        if (_remergeQueue.size() > Integer.parseInt(GlobalContext.getConfig().getMainProperties().getProperty("remerge.pause.threshold", "250"))) {
                            SlaveManager.getBasicIssuer().issueRemergePauseToSlave(this);
                            _prevSocketTimeout = socket.getSoTimeout();
                            // Set lower timeout so it reacts faster when queueSize goes back down
                            socket.setSoTimeout(100);
                            _remergePaused.set(true);
                            logger.debug("Issued remerge pause to slave, current remerge queue is {}", _remergeQueue.size());
                        }
                    }
                }

                if (ar == null) {
                    continue;
                }

                if (!(ar instanceof AsyncResponseRemerge) && !(ar instanceof AsyncResponseTransferStatus)) {
                    logger.debug("Received: {}", ar);
                }

                if (ar instanceof AsyncResponseTransfer) {
                    AsyncResponseTransfer art = (AsyncResponseTransfer) ar;
                    addTransfer((art.getConnectInfo().getTransferIndex()), new RemoteTransfer(art.getConnectInfo(), this));
                }

                switch (ar.getIndex()) {
                    case "Remerge" -> putRemergeQueue(new RemergeMessage((AsyncResponseRemerge) ar, this));
                    case "DiskStatus" -> _status = ((AsyncResponseDiskStatus) ar).getDiskStatus();
                    case "TransferStatus" -> {
                        TransferStatus ats = ((AsyncResponseTransferStatus) ar).getTransferStatus();
                        RemoteTransfer rt;
                        try {
                            rt = getTransfer(ats.getTransferIndex());
                        } catch (SlaveUnavailableException e1) {
                            // no reason for slave thread to be running if the
                            // slave is not online
                            return;
                        }
                        rt.updateTransferStatus(ats);
                        if (ats.isFinished()) {
                            removeTransfer(ats.getTransferIndex());
                        }
                    }
                    default -> {
                        if (!storeResponse(ar, generation)) {
                            continue;
                        }
                        if (pingIndex != null && pingIndex.equals(ar.getIndex())) {
                            try {
                                fetchResponse(pingIndex, getActualTimeout(), generation);
                            } catch (SlaveUnavailableException e) {
                                if (_connectionGeneration != generation) {
                                    logger.debug("Slave {} reconnected, reader of the previous connection exits",
                                            getName());
                                    return;
                                }
                                throw e;
                            }
                            pingIndex = null;
                        } else if (ar.getIndex().equals("SiteBotMessage")) {
                            String message = ((AsyncResponseSiteBotMessage) ar).getMessage();
                            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, this));
                        } else {
                            wakeCommandWaiters();
                        }
                    }
                }
            }
        } catch (Throwable e) {
            logger.error("Slave thread threw an error, dropping slave offline", e);
            setOffline("error: " + e.getMessage(), generation);
        }
    }

    /**
     * Gives up on a ping whose reply did not come: its index goes back into the pool when the late reply arrives
     * (see storeResponse), not before, so that reply cannot be taken for the reply of a later command that reuses
     * the index. Nothing is done if the slave reconnected since the ping was sent: the rebuilt pool already holds
     * the index.
     *
     * @return false if the connection of this generation is gone
     */
    boolean abandonPingIndex(String index, long generation) {
        synchronized (_abandonedIndexes) {
            if (_connectionGeneration != generation) {
                return false;
            }
            _abandonedPingIndexes.add(index);
            return true;
        }
    }

    private int getActualTimeout() {
        return Integer.parseInt(getProperty("timeout", Integer.toString(SlaveManager.actualTimeout)));
    }

    private void removeTransfer(TransferIndex transferIndex) {
        RemoteTransfer transfer = _transfers.remove(transferIndex);

        if (transfer == null) {
            if (!isOnline()) {
                return;
            }
            throw new IllegalStateException("there is a bug in code");
        }
        if (transfer.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            updateDownloadedBytes(transfer.getTransfered());
        } else if (transfer.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            updateUploadedBytes(transfer.getTransfered());
        } // else, we don't care
        commit();
    }

    public void setOffline(String reason) {
        logger.debug("setOffline() {}", reason);
        setOfflineReal(reason, CURRENT_CONNECTION);
    }

    /**
     * Puts the slave offline because of a failure on the connection of the given generation
     * ({@link #getConnectionGeneration()}, read before the failed command was issued). Does nothing if the slave
     * has reconnected since: the failure belongs to a connection that is already gone.
     */
    public void setOffline(String reason, long generation) {
        if (isPreviousConnection(generation, reason)) {
            return;
        }
        logger.debug("setOffline() {}", reason);
        setOfflineReal(reason, generation);
    }

    public void setOffline(Throwable t) {
        logger.info("setOffline()", t);
        setOfflineReal(offlineReason(t), CURRENT_CONNECTION);
    }

    /** {@link #setOffline(String, long)} for a failure given as a Throwable. */
    public void setOffline(Throwable t, long generation) {
        if (isPreviousConnection(generation, offlineReason(t))) {
            return;
        }
        logger.info("setOffline()", t);
        setOfflineReal(offlineReason(t), generation);
    }

    private static String offlineReason(Throwable t) {
        return t.getMessage() == null ? "No Message" : t.getMessage();
    }

    private boolean isPreviousConnection(long generation, String reason) {
        if (generation != CURRENT_CONNECTION && generation != _connectionGeneration) {
            logger.debug("Not setting {} offline for a failure on a previous connection: {}", getName(), reason);
            return true;
        }
        return false;
    }

    /**
     * @param generation the connection to tear down, or CURRENT_CONNECTION for whatever connection is current
     */
    private void setOfflineReal(String reason, long generation) {
        if (isPreviousConnection(generation, reason)) {
            return;
        }
        // If remerging and remerge is paused, wake it (while the connection is still open)
        if (isRemerging() && _remergePaused.get()) {
            try {
                SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
            } catch (SlaveUnavailableException e) {
                // Socket already closed to slave, ignore.
            }
        }
        ObjectInputStream sin;
        ObjectOutputStream sout;
        Socket socket;
        boolean wasAvailable;
        // The generation is checked and the connection taken under the lock connect() installs a new connection
        // under, so both see the same connection. setOffline can run on several threads at the same time (closing
        // the socket makes the reader thread fail and call setOffline too): only the first caller gets the streams,
        // the others find them null and close nothing. No I/O and no events under the lock.
        synchronized (_abandonedIndexes) {
            if (isPreviousConnection(generation, reason)) {
                return;
            }
            if (isRemerging()) {
                _remergePaused.set(false);
                setRemerging(false);
            }
            // If the slave is still processing the remerge queue clear all
            // outstanding entries
            _lastRemergeCommandReceived = 0L;
            _remergeQueue.clear();
            _crcQueue.clear();
            sin = _sin;
            _sin = null;
            sout = _sout;
            _sout = null;
            socket = _socket;
            _socket = null;
            // Connection details are only meaningful while connected, do not keep stale values
            _tlsProtocol = null;
            _tlsCipherSuite = null;
            _remoteAddress = null;
            if (_indexWithCommands != null)
                _indexWithCommands.clear();
            if (_transfers != null)
                _transfers.clear();
            _status = null;
            wasAvailable = _isAvailable;
            setAvailable(false);
        }
        if (sin != null) {
            try {
                sin.close();
            } catch (IOException e) {
            }
        }
        if (sout != null) {
            try {
                sout.flush();
                sout.close();
            } catch (IOException e) {
            }
        }
        if (socket != null) {
            setProperty("lastOnline", Long.toString(System.currentTimeMillis()));
            try {
                socket.close();
            } catch (IOException e) {
            }
        }

        if (wasAvailable) {
            GlobalContext.getEventService().publishAsync(
                    new SlaveEvent("DELSLAVE", reason, this));
        } else {
            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", reason, this));
        }
    }

    /**
     * fetches the next AsyncResponse from the stream of the connection of the given generation; if an
     * IOException is encountered, that connection is setOffline() and SlaveUnavailableException is thrown
     *
     * @throws SlaveUnavailableException
     * @throws SocketTimeoutException
     */
    AsyncResponse readAsyncResponse(ObjectInputStream in, long generation) throws SlaveUnavailableException,
            SocketTimeoutException {
        Object obj;
        if (in == null || !isOnline() || _connectionGeneration != generation) {
            throw new SlaveUnavailableException("Slave is unavailable");
        }
        while (true) {
            try {
                obj = in.readObject();
            } catch (ClassNotFoundException e) {
                logger.error("ClassNotFound reading AsyncResponse", e);
                setOffline("ClassNotFound reading AsyncResponse", generation);
                throw new SlaveUnavailableException(
                        "Slave is unavailable - Class Not Found");
            } catch (SocketTimeoutException e) {
                // don't want this to be caught by IOException below
                throw e;
            } catch (IOException e) {
                logger.error("IOException reading AsyncResponse", e);
                setOffline("IOException reading AsyncResponse", generation);
                throw new SlaveUnavailableException(
                        "Slave is unavailable - IOException");
            }
            if (obj != null) {
                if (obj instanceof AsyncResponse) {
                    return (AsyncResponse) obj;
                }
                logger.error("Throwing away an unexpected class - {} - {}", obj.getClass().getName(), obj);
            }
        }
    }

    public ConnectInfo fetchTransferResponseFromIndex(String index)
            throws RemoteIOException, SlaveUnavailableException {
        AsyncResponseTransfer art = (AsyncResponseTransfer) fetchResponse(index);

        return art.getConnectInfo();
    }

    /** {@link #fetchTransferResponseFromIndex(String)} for a command issued on the connection of that generation. */
    public ConnectInfo fetchTransferResponseFromIndex(String index, long generation)
            throws RemoteIOException, SlaveUnavailableException {
        AsyncResponseTransfer art = (AsyncResponseTransfer) fetchResponse(index, getActualTimeout(), generation);

        return art.getConnectInfo();
    }

    /**
     * Will not set a slave offline, it is the job of the calling thread to
     * decide to do this
     */
    public synchronized void sendCommand(AsyncCommandArgument rac)
            throws SlaveUnavailableException {
        if (rac == null) {
            throw new NullPointerException();
        }

        ObjectOutputStream out = _sout;
        if (!isOnline()) {
            throw new SlaveUnavailableException();
        }

        try {
            logger.debug("Sending {}", rac);
            out.writeObject(rac);
            out.flush();
            out.reset();
        } catch (IOException e) {
            logger.error("error in sendCommand()", e);
            throw new SlaveUnavailableException("error sending command (exception already handled)", e);
        }
        _lastCommandSent = System.currentTimeMillis();
    }

    public boolean checkConnect(Socket socket) throws PatternSyntaxException {
        return getMasks().check(socket);
    }

    /**
     * @return whether the TLS client certificate presented on this connection matches the slave's pinned
     * fingerprint (always true when no fingerprint is pinned)
     */
    public boolean checkCertificate(Socket socket) {
        return SlaveCertificatePin.check(getName(), getProperty(SlaveCertificatePin.PROPERTY), socket);
    }

    /**
     * Checks the certificate of the current connection against the pinned fingerprint, e.g. right after
     * the fingerprint was changed while the slave is online, and disconnects the slave if it does not match.
     *
     * @return the result of the check, or null if the slave is offline
     */
    public SlaveCertificatePin.Result verifyConnectedCertificate() {
        Socket socket = _socket;
        if (socket == null || !socket.isConnected()) {
            return null;
        }
        SlaveCertificatePin.Result result = SlaveCertificatePin.evaluate(
                getProperty(SlaveCertificatePin.PROPERTY), SlaveCertificatePin.peerCertificate(socket));
        if (result != SlaveCertificatePin.Result.ACCEPTED) {
            logger.warn("Disconnecting slave {}: its current certificate does not match the newly pinned fingerprint ({})",
                    getName(), result);
            setOffline("Certificate fingerprint does not match the pinned fingerprint");
        }
        return result;
    }

    public String getProperty(String key) {
        synchronized (_keysAndValues) {
            return _keysAndValues.getProperty(key);
        }
    }

    public void addTransfer(TransferIndex transferIndex,
                            RemoteTransfer transfer) {
        if (!isOnline()) {
            return;
        }

        _transfers.put(transferIndex, transfer);
    }

    public RemoteTransfer getTransfer(TransferIndex transferIndex)
            throws SlaveUnavailableException {
        if (!isOnline()) {
            throw new SlaveUnavailableException("Slave is not online");
        }

        RemoteTransfer ret = _transfers.get(transferIndex);
        if (ret == null) {
            if (isOnline()) {
                throw new FatalException(
                        "there is a bug somewhere in code, tried to fetch a transfer index that doesn't exist - "
                                + transferIndex);
            }
            throw new SlaveUnavailableException("Slave is not online");
        }
        return ret;
    }

    public Collection<RemoteTransfer> getTransfers()
            throws SlaveUnavailableException {
        if (!isOnline()) {
            throw new SlaveUnavailableException("Slave is not online");
        }
        return Collections.unmodifiableCollection(_transfers.values());
    }

    public boolean isMemberOf(String string) {
        StringTokenizer st = new StringTokenizer(getProperty("keywords", ""), " ");

        while (st.hasMoreElements()) {
            if (st.nextToken().equals(string)) {
                return true;
            }
        }

        return false;
    }

    public ConcurrentLinkedDeque<QueuedOperation> getRenameQueue() {
        return _renameQueue;
    }

    public void setRenameQueue(ConcurrentLinkedDeque<QueuedOperation> renameQueue) {
        _renameQueue = Objects.requireNonNullElseGet(renameQueue, ConcurrentLinkedDeque::new);
    }

    public LinkedBlockingQueue<RemergeMessage> getRemergeQueue() {
        return _remergeQueue;
    }

    public LinkedBlockingQueue<FileHandle> getCRCQueue() {
        return _crcQueue;
    }

    public void setCRCThreadFinished() {
        if (_crcThread != null && _crcThread.isAlive()) {
            _crcThread.setFinished();
        }
    }

    public void shutdown() {
        try {
            sendCommand(new AsyncCommand("shutdown", "shutdown"));
            setOfflineReal("shutdown gracefully", CURRENT_CONNECTION);
        } catch (SlaveUnavailableException e) {
        }
    }

    public long getLastTimeOnline() {
        if (isOnline()) {
            return System.currentTimeMillis();
        }
        String value = getProperty("lastOnline");
        // if (value == null) Slave has never been online
        return Long.parseLong(value == null ? "0" : value);
    }

    public String removeProperty(String key) throws KeyNotFoundException {
        synchronized (_keysAndValues) {
            if (getProperty(key) == null)
                throw new KeyNotFoundException();
            String value = (String) _keysAndValues.remove(key);
            commit();
            return value;
        }
    }

    public String descriptiveName() {
        return getName();
    }

    public void writeToDisk() {
        Map<String, Object> params = new HashMap<>();
        params.put(JsonWriter.PRETTY_PRINT, true);
        try (OutputStream out = new SafeFileOutputStream(
                getGlobalContext().getSlaveManager().getSlaveFile(this.getName()));
             JsonWriter writer = new JsonWriter(out, params)) {
            writer.write(this);
            logger.debug("Wrote slavefile for {}", this.getName());
        } catch (IOException | JsonIoException e) {
            throw new RuntimeException("Error writing slavefile for "
                    + this.getName() + ": " + e.getMessage(), e);
        }
    }

    public ObjectOutputStream getOutputStream() {
        return _sout;
    }

    public ObjectInputStream getInputStream() {
        return _sin;
    }

    public void putRemergeQueue(RemergeMessage message) {
        if (!message.isCompleted()) {
            logger.debug("REMERGE: putting message into queue. (path: {})", message.getDirectory());
        }
        try {
            _remergeQueue.put(message);
            _lastRemergeCommandReceived = System.currentTimeMillis();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (_remergeThread == null || !_remergeThread.isAlive()) {
            _remergeThread = new RemergeThread(getName());
            _remergeThread.start();
        }
    }

    public void putCRCQueue(FileHandle file) {
        logger.debug("CRC: putting file into queue {}", file.getPath());
        try {
            _crcQueue.put(file);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        if (_crcThread == null || !_crcThread.isAlive()) {
            _crcThread = new CrcThread(getName());
            _crcThread.start();
        }
    }

    private class RemergeThread extends Thread {

        public RemergeThread(String slaveName) {
            super("RemergeThread - " + slaveName);
        }

        public void run() {
            while (true) {
                RemergeMessage msg;
                try {
                    logger.info("REMERGE SIZE: {}", _remergeQueue.size());
                    msg = _remergeQueue.take();
                } catch (InterruptedException e) {
                    logger.debug("REMERGE QUE: fault in node from queue with exception {}", e.getMessage());
                    continue;
                }

                if (msg.isCompleted()) {
                    logger.info("REMERGE: queue finished");
                    // Wait for crc queue to finish
                    while (!_crcQueue.isEmpty()) {
                        try {
                            Thread.sleep(500);
                        } catch (InterruptedException e) {
                            logger.debug("REMERGE QUE: thread interrupted waiting for crc queue to drain with exception {}", e.getMessage());
                        }
                    }
                    if (!_initRemergeCompleted) {
                        // First remerge after slave connect
                        msg.getRslave().makeAvailableAfterRemerge();
                    }
                    break;
                }

                DirectoryHandle dir = new DirectoryHandle(msg.getDirectory());

                try {
                    dir.remerge(msg.getFiles(), msg.getRslave(), msg.getLastModified());
                } catch (IOException e) {
                    logger.error("IOException during remerge", e);
                    msg.getRslave().setOffline("IOException during remerge");
                    break;
                }
            }
        }
    }

    private class CrcThread extends Thread {

        private boolean _finished = false;

        CrcThread(String slaveName) {
            super("crcThread - " + slaveName);
        }

        public void run() {
            while (true) {
                FileHandle file;
                try {
                    logger.info("REMERGE CRC SIZE: {}", _crcQueue.size());
                    file = _crcQueue.poll(1000, TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    logger.debug("REMERGE CRC QUE: fault in node from queue with exception {}", e.getMessage());
                    continue;
                }
                if (_finished && _crcQueue.isEmpty() && file == null) {
                    logger.info("REMERGE CRC: queue finished");
                    break;
                }
                if (file == null) {
                    continue;
                }
                long checksum;
                try {
                    checksum = getCheckSumForPath(file.getPath());
                } catch (IOException e) {
                    logger.error("IOException on remerge getting CRC from slave [{}, {}]", getName(), file.getPath());
                    continue;
                } catch (SlaveUnavailableException e) {
                    logger.warn("Slave went offline while processing remerge crc queue.");
                    break;
                }
                try {
                    file.setCheckSum(checksum);
                } catch (FileNotFoundException e) {
                    logger.debug("File deleted while getting crc from slave {}", file.getPath());
                }
            }
        }

        void setFinished() {
            _finished = true;
        }
    }
}
