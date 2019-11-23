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
package org.drftpd.master;

import com.cedarsoftware.util.io.JsonIoException;
import com.cedarsoftware.util.io.JsonWriter;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.event.SlaveEvent;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.exceptions.FatalException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.protocol.ProtocolException;
import org.drftpd.slave.*;
import org.drftpd.slave.async.*;
import org.drftpd.stats.ExtendedTimedStats;
import org.drftpd.usermanager.Entity;
import org.drftpd.util.HostMaskCollection;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class RemoteSlave extends ExtendedTimedStats implements Runnable, Comparable<RemoteSlave>,
		Entity, Commitable {

	private static final Logger logger = LogManager.getLogger(RemoteSlave.class);

	private transient boolean _isAvailable;

	private transient boolean _isRemerging;

	private transient boolean _remergeChecksums;

	protected transient int _errors;

	private transient int _prevSocketTimeout;

	private transient long _lastDownloadSending = 0;

	protected transient long _lastNetworkError;

	private transient long _lastUploadReceiving = 0;

	private transient long _lastResponseReceived = System.currentTimeMillis();

	private transient long _lastCommandSent = System.currentTimeMillis();

	private transient int _maxPath;

	private String _name;

	private transient DiskStatus _status;

	private HostMaskCollection _ipMasks;

	private Properties _keysAndValues;
	
	private transient KeyedMap<Key<?>, Object> _transientKeyedMap;

	private ConcurrentLinkedDeque<QueuedOperation> _renameQueue;

	private transient LinkedBlockingDeque<String> _indexPool;

	private transient ConcurrentHashMap<String, AsyncResponse> _indexWithCommands;

	private transient ObjectInputStream _sin;

	private transient Socket _socket;

	private transient ObjectOutputStream _sout;

	private transient ConcurrentHashMap<TransferIndex, RemoteTransfer> _transfers;

	public transient AtomicBoolean _remergePaused;
	
	private transient boolean _initRemergeCompleted;

	private transient Object _commandMonitor;

	private transient LinkedBlockingQueue<RemergeMessage> _remergeQueue;

	private transient LinkedBlockingQueue<FileHandle> _crcQueue;

	private transient RemergeThread _remergeThread;

	private transient CrcThread _crcThread;

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
	
	public static final Key<Boolean> SSL = new Key<>(RemoteSlave.class, "ssl");

	public static Hashtable<String,RemoteSlave> rslavesToHashtable(Collection<RemoteSlave> rslaves) {
		Hashtable<String, RemoteSlave> map = new Hashtable<>(
                rslaves.size());

		for (RemoteSlave rslave : rslaves) {
			map.put(rslave.getName(), rslave);
		}

		return map;
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
	
	public KeyedMap<Key<?>, Object> getTransientKeyedMap() {
		return _transientKeyedMap;
	}

	/**
	 * Needed in order for this class to be a Bean
	 */
	public void setProperties(Properties keysAndValues) {
		_keysAndValues = keysAndValues;
	}

	public void commit() {
		CommitManager.getCommitManager().add(this);
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
	public SlaveStatus getSlaveStatus()
			throws SlaveUnavailableException {
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
	 * @throws ProtocolException 
	 */
	private void initializeSlaveAfterThreadIsRunning() throws IOException,
	SlaveUnavailableException, ProtocolException {
		commit();
		processQueue();

		// checking 'maxpath'
		String maxPathIndex = SlaveManager.getBasicIssuer().issueMaxPathToSlave(this);
		_maxPath = fetchMaxPathFromIndex(maxPathIndex);
		
		// checking ssl availability
		String checkSSLIndex = SlaveManager.getBasicIssuer().issueCheckSSL(this);
		getTransientKeyedMap().setObject(SSL, fetchCheckSSLFromIndex(checkSSLIndex));

		long skipAgeCutoff = 0L;
		
		String remergeMode = GlobalContext.getConfig().getMainProperties().getProperty("partial.remerge.mode");
		_remergeChecksums = GlobalContext.getConfig().getMainProperties().
				getProperty("enableremergechecksums", "false").equalsIgnoreCase("true");
		boolean partialRemerge = false;
		boolean instantOnline = false;
		if (remergeMode == null) {
			logger.error("Slave partial remerge undefined in master.conf, defaulting to \"off\"");
		} else {
			if (remergeMode.equalsIgnoreCase("connect")) {
				try {
					skipAgeCutoff = Long.valueOf(getProperty("lastConnect"));
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
			fetchResponse(remergeIndex, 0);
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
	 *         connect
	 */
	public boolean isAvailable() {
		return _isAvailable;
	}

	public boolean isAvailablePing() {
		if (!isAvailable()) {
			return false;
		}

		try {
			String index = SlaveManager.getBasicIssuer().issuePingToSlave(this);
			fetchResponse(index);
		} catch (SlaveUnavailableException e) {
			setOffline(e);
			return false;
		} catch (RemoteIOException e) {
			setOffline("The slave encountered an IOException while running ping...this is almost not possible");
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

	/**
	 * @return true if CRC is to be added on remerge for files missing CRC in VFS
	 */
	public boolean remergeChecksums() {
		return _remergeChecksums;
	}

	public void processQueue() throws IOException, SlaveUnavailableException {
		QueuedOperation item = null;
		while ((item = _renameQueue.poll()) != null) {
			String sourceFile = item.getSource();
			String destFile = item.getDestination();

			if (destFile == null) { // delete
				try {
					fetchResponse(SlaveManager.getBasicIssuer().issueDeleteToSlave(this, sourceFile), 300000);
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
					fetchResponse(SlaveManager.getBasicIssuer().issueRenameToSlave(this, sourceFile, destDir, fileName));
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

	public void setAvailable(boolean available) {
		_isAvailable = available;
	}

	public void setRemerging(boolean remerging) {
		_isRemerging = remerging;
	}

	protected void makeAvailableAfterRemerge() {
		_initRemergeCompleted = true;
        setProperty("lastConnect", Long.toString(System.currentTimeMillis()));
        if (GlobalContext.getConfig().getMainProperties().getProperty("partial.remerge.mode").equalsIgnoreCase("instant")) {
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
		case Transfer.TRANSFER_RECEIVING_UPLOAD:
			setLastUploadReceiving(l);

			return;

		case Transfer.TRANSFER_SENDING_DOWNLOAD:
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
	 * Deletes files/directories and waits for the response Meant to be used if
	 * you don't want to utilize asynchronization
	 */
	public void simpleDelete(String path) {
		try {
			fetchResponse(SlaveManager.getBasicIssuer().issueDeleteToSlave(this, path), 300000);
		} catch (RemoteIOException e) {
			if (e.getCause() instanceof FileNotFoundException) {
				return;
			}

			setOffline("IOException deleting file, check logs for specific error");
			addQueueDelete(path);
			logger.error("IOException deleting file, file will be deleted when slave comes online",	e);
		} catch (SlaveUnavailableException e) {
			// Already offline and we ARE successful in deleting the file
			addQueueDelete(path);
		}
	}

	/**
	 * Renames files/directories and waits for the response
	 */
	public void simpleRename(String from, String toDirPath, String toName) {
		String simplePath;
		if (toDirPath.endsWith("/")) {
			simplePath = toDirPath + toName;
		} else {
			simplePath = toDirPath + "/" + toName;
		}
		try {
			fetchResponse(SlaveManager.getBasicIssuer().issueRenameToSlave(this, from, toDirPath, toName));
		} catch (RemoteIOException e) {
			setOffline(e);
			addQueueRename(from, simplePath);
		} catch (SlaveUnavailableException e) {
			addQueueRename(from, simplePath);
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

	public synchronized void connect(Socket socket, ObjectInputStream in,
			ObjectOutputStream out) {
		_socket = socket;
		_sout = out;
		_sin = in;
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
		
		if (_transfers == null) {
			_transfers = new ConcurrentHashMap<>();
		} else {
			_transfers.clear();
		}
		
		_errors = 0;
		_lastNetworkError = System.currentTimeMillis();
		_initRemergeCompleted = false;
		setRemerging(true);
		
		try {
			GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().handshakeWithSlave(this);
		} catch (ProtocolException e) {
			setOffline(e);
		}
		
		class InitiateRemergeThread implements Runnable {
			public void run() {
				try {
					initializeSlaveAfterThreadIsRunning();
				} catch (Exception e) {
					setOffline(e);
				}
			}
		}

		new Thread(new InitiateRemergeThread(), "RemoteSlaveRemerge - " + getName()).start();		
		start();
	}

	private void start() {
		Thread t = new Thread(this);
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
	 * @see fetchResponse(String index, int wait)
	 */
	public AsyncResponse fetchResponse(String index)
			throws SlaveUnavailableException, RemoteIOException {
		return fetchResponse(index, getActualTimeout());
	}

	/**
	 * returns an AsyncResponse for that index and throws any exceptions thrown
	 * on the Slave side
	 */
	public AsyncResponse fetchResponse(String index, int wait)
			throws SlaveUnavailableException, RemoteIOException {
		long total = System.currentTimeMillis();

		while (isOnline() && !_indexWithCommands.containsKey(index)) {
			try {
				synchronized(_commandMonitor) {
					_commandMonitor.wait(1000);
				}

				// will wait a maximum of 1000 milliseconds before waking up
			} catch (InterruptedException e) {
			}

			if ((wait != 0) && ((System.currentTimeMillis() - total) >= wait)) {
				setOffline("Slave has taken too long while waiting for reply " + index);
			}
		}

		if (!isOnline()) {
			throw new SlaveUnavailableException("Slave went offline while processing command");
		}

		AsyncResponse rar = _indexWithCommands.remove(index);
		_indexPool.push(index);

		if (rar instanceof AsyncResponseException) {
			Throwable t = ((AsyncResponseException) rar).getThrowable();

			if (t instanceof IOException) {
				throw new RemoteIOException((IOException) t);
			}

			logger.error("Exception on slave that is unable to be handled by the master", t);
			setOffline("Exception on slave that is unable to be handled by the master");
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

	public long getCheckSumForPath(String path) throws IOException,
			SlaveUnavailableException {
		try {
			return fetchChecksumFromIndex(SlaveManager.getBasicIssuer().issueChecksumToSlave(this, path));
		} catch (RemoteIOException e) {
			throw e.getCause();
		}
	}

	public String moreInfo() {
		try {
			return getName() + ":address=[" + getPASVIP() + "]port=["
					+ Integer.toString(getPort()) + "]";
		} catch (SlaveUnavailableException e) {
			return getName() + ":offline";
		}
	}

	public void run() {
        logger.debug("Starting RemoteSlave for {}", getName());

		try {
			String pingIndex = null;
			while (isOnline()) {
				AsyncResponse ar = null;

				try {
					ar = readAsyncResponse();
					_lastResponseReceived = System.currentTimeMillis();
				} catch (SlaveUnavailableException e3) {
					// no reason for slave thread to be running if the slave is
					// not online
					return;
				} catch (SocketTimeoutException e) {
					// handled below
				}

				if ((getActualTimeout() > (System.currentTimeMillis() - _lastResponseReceived))
						&& ((getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastResponseReceived)) || (getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastCommandSent)))) {
					if (pingIndex != null) {
						logger.error("Ping lost, no response from slave, sending new ping to slave");
						_indexPool.push(pingIndex);
					}
					pingIndex = SlaveManager.getBasicIssuer().issuePingToSlave(this);
				} else if (getActualTimeout() < (System.currentTimeMillis() - _lastResponseReceived)) {
					setOffline("Slave seems to have gone offline, have not received a response in "
							+ (System.currentTimeMillis() - _lastResponseReceived)
							+ " milliseconds");
					throw new SlaveUnavailableException();
				}

				if (isOnline() && !_initRemergeCompleted) {
					if (_remergePaused.get()) {
						// Do we need to resume?
						if (_remergeQueue.size() <= Integer.parseInt(GlobalContext.getConfig().getMainProperties().getProperty("remerge.resume.threshold", "50"))) {
							_socket.setSoTimeout(_prevSocketTimeout); // Restore old time out
							SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
							_remergePaused.set(false);
                            logger.debug("Issued remerge resume to slave, current remerge queue is {}", _remergeQueue.size());
                        }
					} else {
						// Do we need to pause?
						if (_remergeQueue.size() > Integer.parseInt(GlobalContext.getConfig().getMainProperties().getProperty("remerge.pause.threshold", "250"))) {
							SlaveManager.getBasicIssuer().issueRemergePauseToSlave(this);
							_prevSocketTimeout = _socket.getSoTimeout();
							// Set lower timeout so it reacts faster when queueSize goes back down
							_socket.setSoTimeout(100);
							_remergePaused.set(true);
                            logger.debug("Issued remerge pause to slave, current remerge queue is {}", _remergeQueue.size());
						}
					}
				}

				if (ar == null) {
					continue;
				}

				if (!(ar instanceof AsyncResponseRemerge)
						&& !(ar instanceof AsyncResponseTransferStatus)) {
                    logger.debug("Received: {}", ar);
				}

				if (ar instanceof AsyncResponseTransfer) {
					AsyncResponseTransfer art = (AsyncResponseTransfer) ar;
					addTransfer((art.getConnectInfo().getTransferIndex()),
							new RemoteTransfer(art.getConnectInfo(), this));
				}

                switch (ar.getIndex()) {
                    case "Remerge":
                        putRemergeQueue(new RemergeMessage((AsyncResponseRemerge) ar, this));
                        break;
                    case "DiskStatus":
                        _status = ((AsyncResponseDiskStatus) ar)
                                .getDiskStatus();
                        break;
                    case "TransferStatus":
                        TransferStatus ats = ((AsyncResponseTransferStatus) ar)
                                .getTransferStatus();
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
                        break;
                    default:
                        _indexWithCommands.put(ar.getIndex(), ar);
                        if (pingIndex != null
                                && pingIndex.equals(ar.getIndex())) {
                            fetchResponse(pingIndex);
                            pingIndex = null;
                        } else if (ar.getIndex().equals("SiteBotMessage")) {
                            String message = ((AsyncResponseSiteBotMessage) ar).getMessage();
                            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, this));
                        } else {
                            synchronized (_commandMonitor) {
                                _commandMonitor.notifyAll();
                            }
                        }
                        break;
                }
			}
		} catch (Throwable e) {
			setOffline("error: " + e.getMessage());
			logger.error("", e);
		}
	}

	private int getActualTimeout() {
		return Integer.parseInt(getProperty("timeout", Integer
				.toString(SlaveManager.actualTimeout)));
	}

	private void removeTransfer(TransferIndex transferIndex) {
		RemoteTransfer transfer =  _transfers.remove(transferIndex);

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
		setOfflineReal(reason);
	}

	private void setOfflineReal(String reason) {
		// If remerging and remerge is paused, wake it
		if (_isRemerging) {
			if (_remergePaused.get()) {
				try {
					SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
				} catch (SlaveUnavailableException e) {
					// Socket already closed to slave, ignore.
				}
				_remergePaused.set(false);
			}
			_isRemerging = false;
		}
		// If the slave is still processing the remerge queue clear all
		// outstanding entries
		_remergeQueue.clear();
		_crcQueue.clear();
		if (_sin != null) {
			try {
				_sin.close();
			} catch (IOException e) {
			}
			_sin = null;
		}
		if (_sout != null) {
			try {
				_sout.flush();
				_sout.close();
			} catch (IOException e) {
			}
			_sout = null;
		}
		if (_socket != null) {
			setProperty("lastOnline", Long.toString(System.currentTimeMillis()));
			try {
				_socket.close();
			} catch (IOException e) {
			}
			_socket = null;
		}
		if (_indexWithCommands != null)
			_indexWithCommands.clear();
		if (_transfers != null)
			_transfers.clear();
		_maxPath = 0;
		_status = null;

		if (_isAvailable) {
			GlobalContext.getEventService().publishAsync(
					new SlaveEvent("DELSLAVE", reason, this));
		}
		else {
			GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", reason, this));
		}

		setAvailable(false);
	}

	public void setOffline(Throwable t) {
		logger.info("setOffline()", t);

		if (t.getMessage() == null) {
			setOfflineReal("No Message");
		} else {
			setOfflineReal(t.getMessage());
		}
	}

	/**
	 * fetches the next AsyncResponse, if IOException is encountered, the slave
	 * is setOffline() and the Exception is thrown
	 * 
	 * @throws SlaveUnavailableException
	 * @throws SocketTimeoutException
	 */
	private AsyncResponse readAsyncResponse() throws SlaveUnavailableException,
			SocketTimeoutException {
		Object obj;
		ObjectInputStream in = _sin;
		if (!isOnline()) {
			throw new SlaveUnavailableException("Slave is unavailable");
		}
		while (true) {
			try {
				obj = in.readObject();
			} catch (ClassNotFoundException e) {
				logger.error("ClassNotFound reading AsyncResponse", e);
				setOffline("ClassNotFound reading AsyncResponse");
				throw new SlaveUnavailableException(
						"Slave is unavailable - Class Not Found");
			} catch (SocketTimeoutException e) {
				// don't want this to be caught by IOException below
				throw e;
			} catch (IOException e) {
				logger.error("IOException reading AsyncResponse", e);
				setOffline("IOException reading AsyncResponse");
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
		StringTokenizer st = new StringTokenizer(getProperty("keywords", "")," ");

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

	public void setRenameQueue(ConcurrentLinkedDeque<QueuedOperation> renameQueue) {
		if (renameQueue == null) {
			_renameQueue = new ConcurrentLinkedDeque<>();
		} else {
			_renameQueue = renameQueue;
		}
	}

	public void shutdown() {
		try {
			sendCommand(new AsyncCommand("shutdown", "shutdown"));
			setOfflineReal("shutdown gracefully");
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
		Map<String,Object> params = new HashMap<>();
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
		logger.debug("REMERGE: putting message into queue");
		try {
			_remergeQueue.put(message);
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
