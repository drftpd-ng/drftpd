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
package net.sf.drftpd.master;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.StreamException;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.HostMaskCollection;
import net.sf.drftpd.ID3Tag;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.TransferStatus;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Logger;

import org.apache.oro.text.regex.MalformedPatternException;

import org.drftpd.GlobalContext;

import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.RemoteTransfer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseID3Tag;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSFVFile;
import org.drftpd.slave.async.AsyncResponseSlaveStatus;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;

import java.util.Collection;
import java.util.EmptyStackException;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;
import java.util.Stack;
import java.util.StringTokenizer;


/**
 * @author mog
 * @author zubov
 * @version $Id: RemoteSlave.java,v 1.62 2004/11/06 06:04:02 zubov Exp $
 */
public class RemoteSlave implements Runnable, Comparable, Serializable {
    private static final long serialVersionUID = -6973935289361817125L;
    private static final Logger logger = Logger.getLogger(RemoteSlave.class);
    private static XStream _xst = new XStream();
    private transient boolean _available;
    protected transient int _errors;
    private transient GlobalContext _gctx;
    private transient long _lastDownloadSending = 0;
    protected transient long _lastNetworkError;
    private transient long _lastUploadReceiving = 0;
    private transient int _maxPath;
    private transient String _name;
    private transient SlaveStatus _status;
    private HostMaskCollection _ipMasks;
    private Properties _keysAndValues;
    private HashMap _renameQueue;
    private Stack _indexPool;
    private transient HashMap _indexWithCommands;
    private transient ObjectInputStream _sin;
    private transient Socket _socket;
    private transient ObjectOutputStream _sout;
    private transient HashMap _transfers;

    /**
     * Used by everything including tests
     */
    public RemoteSlave(String name, GlobalContext gctx) {
        init(name, gctx);
        _keysAndValues = new Properties();
        _ipMasks = new HostMaskCollection();
        _renameQueue = new HashMap();
        commit();
    }

    public final static Hashtable rslavesToHashtable(Collection rslaves) {
        Hashtable map = new Hashtable(rslaves.size());

        for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();
            map.put(rslave.getName(), rslave);
        }

        return map;
    }

    public boolean addMask(String mask) {
        _ipMasks.addMask(mask);
        commit();

        return true;
    }

    /**
     * If X # of errors occur in Y amount of time, kick slave offline
     */
    public final void addNetworkError(SocketException e) {
        // set slave offline if too many network errors
        long errortimeout = Long.parseLong(getProperty("errortimeout", "60000")); // one minute

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
            logger.error("Too many network errors - " + e);
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

        if (_renameQueue.containsKey(fileName)) {
            throw new IllegalArgumentException(fileName +
                " is already in the queue for " + getName());
        }

        _renameQueue.put(fileName, destName);
    }

    public void setProperty(String name, String value) {
        _keysAndValues.setProperty(name, value);
        commit();
    }

    public String getProperty(String name, String def) {
        return _keysAndValues.getProperty(name, def);
    }

    public Properties getProperties() {
        return _keysAndValues;
    }

    public void commit() {
        try {
            SafeFileWriter out = new SafeFileWriter((getGlobalContext()
                                                         .getSlaveManager()
                                                         .getSlaveFile(this.getName())));

            try {
                //_xst = new XStream();
                out.write(_xst.toXML(this));
            } finally {
                out.close();
            }

            Logger.getLogger(RemoteSlave.class).debug("wrote " + getName());
        } catch (IOException ex) {
            throw new RuntimeException("Error writing slavefile for " +
                this.getName() + ": " + ex.getMessage(), ex);
        }
    }

    public final int compareTo(Object o) {
        if (!(o instanceof RemoteSlave)) {
            throw new IllegalArgumentException();
        }

        return getName().compareTo(((RemoteSlave) o).getName());
    }

    public final boolean equals(Object obj) {
        return ((RemoteSlave) obj).getName().equals(getName());
    }

    public GlobalContext getGlobalContext() {
        return _gctx;
    }

    public final long getLastDownloadSending() {
        return _lastDownloadSending;
    }

    public final long getLastTransfer() {
        return Math.max(getLastDownloadSending(), getLastUploadReceiving());
    }

    public long getLastTransferForDirection(char dir) {
        if (dir == RemoteTransfer.TRANSFER_RECEIVING_UPLOAD) {
            return getLastUploadReceiving();
        } else if (dir == RemoteTransfer.TRANSFER_SENDING_DOWNLOAD) {
            return getLastDownloadSending();
        } else if (dir == RemoteTransfer.TRANSFER_THROUGHPUT) {
            return getLastTransfer();
        } else {
            throw new IllegalArgumentException();
        }
    }

    public final long getLastUploadReceiving() {
        return _lastUploadReceiving;
    }

    public HostMaskCollection getMasks() {
        return _ipMasks;
    }

    /**
     * Returns the name.
     */
    public final String getName() {
        return _name;
    }

    /**
     * Returns the RemoteSlave's saved SlaveStatus, can return a status before
     * remerge() is completed
     */
    public final synchronized SlaveStatus getStatus()
        throws SlaveUnavailableException {
        try {
            _status = fetchStatusFromIndex(issueStatusToSlave());
        } catch (IOException e) {
            setOffline(e);
        }

        return _status;
    }

    /**
     * Returns the RemoteSlave's stored SlaveStatus, will not return a status
     * before remerge() is completed
     */
    public synchronized SlaveStatus getStatusAvailable()
        throws SlaveUnavailableException {
        if (isAvailable()) {
            return getStatus();
        }

        throw new SlaveUnavailableException("Slave is not online");
    }

    public final int hashCode() {
        return getName().hashCode();
    }

    /**
     * Used to initialize the RemoteSlave, used in the constructor and after
     * serialization
     */
    protected final void init(String name, GlobalContext gctx) {
        _name = name;
        _gctx = gctx;
    }

    /**
     * Called when the slave connects
     */
    private void initializeSlaveAfterThreadIsRunning()
        throws IOException, SlaveUnavailableException {
        processQueue();

        String statusIndex = issueStatusToSlave();
        String maxPathIndex = issueMaxPathToSlave();
        _status = fetchStatusFromIndex(statusIndex);
        logger.debug("status was received");
        _maxPath = fetchMaxPathFromIndex(maxPathIndex);
        logger.debug("maxpath was received");
        getGlobalContext().getRoot().setSlaveForMerging(this);

        String remergeIndex = issueRemergeToSlave("/");
        fetchRemergeResponseFromIndex(remergeIndex);
        getGlobalContext().getRoot().cleanSlaveFromMerging(this);
        setAvailable(true);
        logger.info("Slave added: '" + getName() + "' status: " + _status);
        getGlobalContext().getConnectionManager().dispatchFtpEvent(new SlaveEvent(
                "ADDSLAVE", this));
    }

    /**
     * @return true if the slave has synchronized its filelist since last
     *         connect
     */
    public final boolean isAvailable() {
        return _available;
    }

    public final boolean isAvailablePing() {
        if (!isAvailable()) {
            return false;
        }

        try {
            String index = issuePingToSlave();
            fetchResponse(index);
        } catch (SlaveUnavailableException e) {
            setOffline(e);

            return false;
        } catch (IOException e) {
            setOffline(e);

            return false;
        }

        return isAvailable();
    }

    public void processQueue() throws IOException, SlaveUnavailableException {
        for (Iterator iter = _renameQueue.keySet().iterator(); iter.hasNext();) {
            String sourceFile = (String) iter.next();
            String destFile = (String) _renameQueue.get(sourceFile);

            if (destFile == null) {
                fetchResponse(issueDeleteToSlave(sourceFile));
            } else {
                String fileName = destFile.substring(destFile.lastIndexOf("/") +
                        1);
                String destDir = destFile.substring(0, destFile.lastIndexOf("/"));
                fetchResponse(issueRenameToSlave(sourceFile, destDir, fileName));
            }

            iter.remove();
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

    public final void setAvailable(boolean available) {
        _available = available;
    }

    public final void setLastDirection(char direction, long l) {
        switch (direction) {
        case RemoteTransfer.TRANSFER_RECEIVING_UPLOAD:
            setLastUploadReceiving(l);

            return;

        case RemoteTransfer.TRANSFER_SENDING_DOWNLOAD:
            setLastDownloadSending(l);

            return;

        default:
            throw new IllegalArgumentException();
        }
    }

    public final void setLastDownloadSending(long lastDownloadSending) {
        _lastDownloadSending = lastDownloadSending;
    }

    public final void setLastUploadReceiving(long lastUploadReceiving) {
        _lastUploadReceiving = lastUploadReceiving;
    }

    /**
     * Deletes files/directories and waits for the response
     */
    public void simpleDelete(String path) {
        try {
            String index = issueDeleteToSlave(path);
            fetchResponse(index);
        } catch (FileNotFoundException e) {
            return;
        } catch (IOException e) {
            setOffline(
                "IOException deleting file, check logs for specific error");
            addQueueDelete(path);
            logger.error("IOException deleting file, check logs for specific error",
                e);
        } catch (SlaveUnavailableException e) {
            addQueueDelete(path);
        }
    }

    /**
     * Renames files/directories and waits for the response
     */
    public void simpleRename(String from, String toDirPath, String toName) {
        try {
            String index = issueRenameToSlave(from, toDirPath, toName);
            fetchResponse(index);
        } catch (IOException e) {
            setOffline(e.getMessage());
            addQueueRename(from, toDirPath + "/" + toName);
        } catch (SlaveUnavailableException e) {
            addQueueRename(from, toDirPath + "/" + toName);
        }
    }

    public String toString() {
        return moreInfo();
    }

    public static String getSlaveNameFromObjectInput(ObjectInputStream in)
        throws IOException {
        try {
            return (String) in.readObject();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    public void connect(Socket socket, ObjectInputStream in,
        ObjectOutputStream out) throws IOException {
        _socket = socket;
        _sout = out;
        _sin = in;
        _indexPool = new Stack();

        for (int i = 0; i < 256; i++) {
            String key = Integer.toHexString(i);

            if (key.length() < 2) {
                key = "0" + key;
            }

            _indexPool.push(key);
        }

        _indexWithCommands = new HashMap();
        _transfers = new HashMap();
        _errors = 0;
        _lastNetworkError = System.currentTimeMillis();
        start();

        try {
            initializeSlaveAfterThreadIsRunning();
        } catch (IOException e) {
            setOffline(e);
        } catch (SlaveUnavailableException e) {
            setOffline(e);
        }
    }

    private void start() {
        Thread t = new Thread(this);
        t.setName("RemoteSlave - " + getName());
        t.start();
    }

    public void disconnect() {
        if (_socket != null) {
            try {
                _socket.close();
            } catch (IOException e) {
            }
        }

        _socket = null;
        _sin = null;
        _sout = null;
        _indexPool = null;
        _indexWithCommands = null;
        _transfers = null;
    }

    public long fetchChecksumFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        return ((AsyncResponseChecksum) fetchResponse(index)).getChecksum();
    }

    public ID3Tag fetchID3TagFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        return ((AsyncResponseID3Tag) fetchResponse(index)).getTag();
    }

    private String fetchIndex() throws SlaveUnavailableException {
        while (isOnline()) {
            synchronized (_indexPool) {
                try {
                    String index = (String) _indexPool.pop();

                    return index;
                } catch (EmptyStackException e) {
                    logger.debug(
                        "Too many commands sent, need to wait for the slave to process commands");
                }

                try {
                    _indexPool.wait();
                } catch (InterruptedException e1) {
                }
            }
        }

        throw new SlaveUnavailableException("Went offline fetching an index");
    }

    public int fetchMaxPathFromIndex(String maxPathIndex)
        throws IOException, SlaveUnavailableException {
        return ((AsyncResponseMaxPath) fetchResponse(maxPathIndex)).getMaxPath();
    }

    
    public AsyncResponse fetchResponse(String index) throws IOException, SlaveUnavailableException {
        return fetchResponse(index,10000);
    }
    /**
     * returns an AsyncResponse for that index and throws any exceptions
     */
    public AsyncResponse fetchResponse(String index, int wait)
        throws IOException, SlaveUnavailableException {
        AsyncResponse rar = null;
        long total = System.currentTimeMillis();

        synchronized (_indexWithCommands) {
            while (isOnline()) {
                if (wait != 0 && (System.currentTimeMillis() - total) >= wait) {
                    returnIndex(index);
                    throw new SlaveUnavailableException(
                        "Slave has taken too long while processing command");
                }

                if (_indexWithCommands.containsKey(index)) {
                    break;
                }

                try {
                    _indexWithCommands.wait(1000);
                } catch (InterruptedException e) {
                }
            }

            rar = (AsyncResponse) _indexWithCommands.remove(index);
        }

        returnIndex(index);

        if (rar == null) {
            throw new SlaveUnavailableException(
                "Slave went offline while processing command");
        }

        if (rar instanceof AsyncResponseException) {
            Throwable t = ((AsyncResponseException) rar).getThrowable();

            if (t instanceof IOException) {
                throw (IOException) t;
            }

            throw new RuntimeException(t);
        }

        return rar;
    }

    public SFVFile fetchSFVFileFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        return ((AsyncResponseSFVFile) fetchResponse(index)).getSFV();
    }

    public SlaveStatus fetchStatusFromIndex(String statusIndex)
        throws IOException, SlaveUnavailableException {
        return ((AsyncResponseSlaveStatus) fetchResponse(statusIndex)).getSlaveStatus();
    }

    public InetAddress getInetAddress() {
        return _socket.getInetAddress();
    }

    public int getPort() {
        return _socket.getPort();
    }

    public boolean isOnline() {
        return ((_socket != null) && _socket.isConnected());
    }

    public String issueChecksumToSlave(String string)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "checksum", string));

        return index;
    }

    public String issueConnectToSlave(InetSocketAddress address,
        boolean encryptedDataChannel) throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "connect",
                address.getAddress().getHostAddress() + ":" +
                address.getPort() + "," + encryptedDataChannel));

        return index;
    }

    /**
     * @return String index, needs to be used to fetch the response
     */
    public String issueDeleteToSlave(String sourceFile)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "delete", sourceFile));

        return index;
    }

    public String issueID3TagToSlave(String path)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "id3tag", path));

        return index;
    }

    public String issueListenToSlave(boolean encryptedDataChannel)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "listen",
                "" + encryptedDataChannel));

        return index;
    }

    public String issueMaxPathToSlave() throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommand(index, "maxpath"));

        return index;
    }

    public String issuePingToSlave() throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommand(index, "ping"));

        return index;
    }

    public String issueReceiveToSlave(String name, char c, long position,
        TransferIndex tindex) throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "receive",
                c + "," + position + "," + tindex + "," + name));

        return index;
    }

    public String issueRenameToSlave(String from, String toDirPath,
        String toName) throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "rename",
                from + "," + toDirPath + "," + toName));

        return index;
    }

    public String issueSFVFileToSlave(String path)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        AsyncCommand ac = new AsyncCommandArgument(index, "sfvfile", path);
        sendCommand(ac);

        return index;
    }

    public String issueStatusToSlave() throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommand(index, "status"));

        return index;
    }

    public String moreInfo() {
        return getName() + ":address=[" + getInetAddress() + "]port=[" +
        Integer.toString(getPort()) + "]";
    }

    private void returnIndex(String index) {
        synchronized (_indexPool) {
            _indexPool.push(index);
            _indexPool.notifyAll();
        }
    }

    public void run() {
        logger.debug("Starting RemoteSlave for " + getName());

        try {
            while (isOnline()) {
                AsyncResponse ar = null;

                try {
                    ar = readAsyncResponse();

                    if (ar == null) {
                        continue;
                    }
                } catch (IOException e3) {
                    logger.error("", e3);
                    setOffline("Error in slave communication");

                    return;
                }

                logger.debug("Received: " + ar);

                if (ar instanceof AsyncResponseTransfer) {
                    AsyncResponseTransfer art = (AsyncResponseTransfer) ar;
                    addTransfer((art.getConnectInfo().getTransferIndex()),
                        new RemoteTransfer(art.getConnectInfo(), this));
                }

                if (ar.getIndex().equals("Remerge")) {
                    AsyncResponseRemerge asr = (AsyncResponseRemerge) ar;
                    LinkedRemoteFileInterface lrf;

                    try {
                        lrf = getGlobalContext().getRoot().lookupFile(asr.getDirectory());
                    } catch (FileNotFoundException e1) {
                        lrf = getGlobalContext().getRoot().createDirectories(asr.getDirectory());
                    }

                    try {
                        lrf.remerge(asr.getFiles(), this);
                    } catch (IOException e2) {
                        logger.error("IOException during remerge", e2);
                        setOffline("IOException during remerge");
                    }
                } else if (ar.getIndex().equals("SlaveStatus")) {
                    _status = ((AsyncResponseSlaveStatus) ar).getSlaveStatus();
                } else if (ar.getIndex().equals("TransferStatus")) {
                    TransferStatus ats = ((AsyncResponseTransferStatus) ar).getTransferStatus();
                    RemoteTransfer rt = getTransfer(ats.getTransferIndex());
                    rt.updateTransferStatus(ats);

                    if (ats.isFinished()) {
                        _transfers.remove(ats.getTransferIndex());
                    }
                } else {
                    logger.debug("Into _indexWithCommands: " + ar);

                    synchronized (_indexWithCommands) {
                        _indexWithCommands.put(ar.getIndex(), ar);
                        _indexWithCommands.notifyAll();
                    }

                    logger.debug("threads notified");
                }
            }
        } catch (StreamException e) {
            setOffline("Slave disconnected");
            logger.error("", e);
        }
    }

    public final synchronized void setOffline(String reason) {
        disconnect();
        _status = null;
        _maxPath = 0;
        _status = null;
        _available = false;
        _socket = null;
        _sout = null;
        _sin = null;

        if (!_available) {
            return;
        }

        getGlobalContext().dispatchFtpEvent(new SlaveEvent("DELSLAVE", reason,
                this));
        setAvailable(false);
        getGlobalContext().getRoot().resetSlaveForMerging(this);
    }

    public final void setOffline(Throwable t) {
        if (t.getMessage() == null) {
            setOffline("No Message");
        } else {
            setOffline(t.getMessage());
        }
    }

    /**
     * fetches the next AsyncResponse, if IOException is encountered, the slave
     * is setOffline() and the Exception is thrown
     */
    private AsyncResponse readAsyncResponse() throws IOException {
        try {
            AsyncResponse ar = (AsyncResponse) _sin.readObject();

            if (ar instanceof AsyncResponseException) {
                AsyncResponseException asr = (AsyncResponseException) ar;
                logger.debug("", asr.getThrowable());

                if (asr.getThrowable() instanceof IOException) {
                    throw (IOException) asr.getThrowable();
                }

                throw new FatalException(asr.getThrowable());
            }

            return ar;
        } catch (ClassNotFoundException e) {
            throw new FatalException(e);
        }
    }

    public void issueAbortToSlave(TransferIndex transferIndex)
        throws SlaveUnavailableException {
        sendCommand(new AsyncCommandArgument("abort", "abort",
                transferIndex.toString()));
    }

    public ConnectInfo fetchTransferResponseFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        AsyncResponseTransfer art = (AsyncResponseTransfer) fetchResponse(index);

        return art.getConnectInfo();
    }

    private synchronized void sendCommand(AsyncCommand rac) {
        if (rac == null) {
            return;
        }

        try {
            _sout.writeObject(rac);
            _sout.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String issueTransferStatusToSlave(TransferIndex transferIndex)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "transferstatus",
                transferIndex.toString()));

        return index;
    }

    public String issueSendToSlave(String name, char c, long position,
        TransferIndex tindex) throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "send",
                c + "," + position + "," + tindex + "," + name));

        return index;
    }

    public String issueRemergeToSlave(String path)
        throws SlaveUnavailableException {
        String index = fetchIndex();
        sendCommand(new AsyncCommandArgument(index, "remerge", path));

        return index;
    }

    public void fetchRemergeResponseFromIndex(String index)
        throws IOException, SlaveUnavailableException {
        fetchResponse(index,0);
    }

    public boolean isOnlinePing() {
        return isOnline();
    }

    public boolean checkConnect(Socket socket) throws MalformedPatternException {
        return getMasks().check(socket);
    }

    public String getProperty(String key) {
        return _keysAndValues.getProperty(key);
    }

    public void addTransfer(TransferIndex transferIndex, RemoteTransfer transfer) {
        synchronized (_transfers) {
            _transfers.put(transferIndex, transfer);
        }
    }

    public RemoteTransfer getTransfer(TransferIndex transferIndex) {
        if (transferIndex == null) {
            throw new RuntimeException();
        }

        synchronized (_transfers) {
            if (!_transfers.containsKey(transferIndex)) {
                throw new FatalException();
            }

            return (RemoteTransfer) _transfers.get(transferIndex);
        }
    }

    public boolean hasKeyword(String string) {
        StringTokenizer st = new StringTokenizer(getProperty("keywords", ""),
                " ");

        while (st.hasMoreElements()) {
            if (st.nextToken().equals(string)) {
                return true;
            }
        }

        return false;
    }
}
