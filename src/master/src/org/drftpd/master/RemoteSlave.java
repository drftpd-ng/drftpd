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

import java.beans.DefaultPersistenceDelegate;
import java.beans.ExceptionListener;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
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
import org.drftpd.slave.ConnectInfo;
import org.drftpd.slave.DiskStatus;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.TransferStatus;
import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseChecksum;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseMaxPath;
import org.drftpd.slave.async.AsyncResponseRemerge;
import org.drftpd.slave.async.AsyncResponseSSLCheck;
import org.drftpd.slave.async.AsyncResponseTransfer;
import org.drftpd.slave.async.AsyncResponseTransferStatus;
import org.drftpd.stats.ExtendedTimedStats;
import org.drftpd.usermanager.Entity;
import org.drftpd.util.HostMask;
import org.drftpd.util.HostMaskCollection;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class RemoteSlave extends ExtendedTimedStats implements Runnable, Comparable<RemoteSlave>,
		Entity, Commitable {
	private final String[] transientFields = { "available",
			"lastDownloadSending", "lastUploadReceiving" };

	private static final Logger logger = Logger.getLogger(RemoteSlave.class);

	private transient boolean _isAvailable;

	private transient boolean _isRemerging;

	protected transient int _errors;

	private transient long _lastDownloadSending = 0;

	protected transient long _lastNetworkError;

	private transient long _lastUploadReceiving = 0;

	private transient long _lastResponseReceived = System.currentTimeMillis();

	private transient long _lastCommandSent = System.currentTimeMillis();

	private transient int _maxPath;

	private transient String _name;

	private transient DiskStatus _status;

	private HostMaskCollection _ipMasks;

	private Properties _keysAndValues;
	
	private KeyedMap<Key<?>, Object> _transientKeyedMap;

	private LinkedList<QueuedOperation> _renameQueue;

	private transient LinkedBlockingDeque<String> _indexPool;

	private transient ConcurrentHashMap<String, AsyncResponse> _indexWithCommands;

	private transient ObjectInputStream _sin;

	private transient Socket _socket;

	private transient ObjectOutputStream _sout;

	private transient ConcurrentHashMap<TransferIndex, RemoteTransfer> _transfers;

	private transient AtomicBoolean _remergePaused;
	
	private transient boolean _initRemergeCompleted;

	private transient Object _commandMonitor;

	private transient LinkedBlockingQueue<RemergeMessage> _remergeQueue;

	private transient RemergeThread _remergeThread;

	public RemoteSlave(String name) {
		_name = name;
		_keysAndValues = new Properties();
		_transientKeyedMap = new KeyedMap<Key<?>, Object>();
		_ipMasks = new HostMaskCollection();
		_renameQueue = new LinkedList<QueuedOperation>();
		_remergePaused = new AtomicBoolean();
		_remergeQueue = new LinkedBlockingQueue<RemergeMessage>();
		_commandMonitor = new Object();
	}
	
	public static final Key<Boolean> SSL = new Key<Boolean>(RemoteSlave.class, "ssl");

	public static Hashtable<String,RemoteSlave> rslavesToHashtable(Collection<RemoteSlave> rslaves) {
		Hashtable<String, RemoteSlave> map = new Hashtable<String, RemoteSlave>(
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
		boolean partialRemerge = false;
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
			}
		}
		String remergeIndex;
		if (partialRemerge) {
			remergeIndex = SlaveManager.getBasicIssuer().issueRemergeToSlave(this, "/", true, skipAgeCutoff,
					System.currentTimeMillis());
		} else {
			remergeIndex = SlaveManager.getBasicIssuer().issueRemergeToSlave(this, "/", false, 0L, 0L);
		}

		try {
			fetchResponse(remergeIndex, 0);
		} catch (RemoteIOException e) {
			throw new IOException(e.getMessage());
		}

		putRemergeQueue(new RemergeMessage(this));

		// TODO move lastConnect time setting to makeAvailableAfterRemerge()
		setProperty("lastConnect", Long.toString(System.currentTimeMillis()));
		_initRemergeCompleted = true;
		if (_remergePaused.get()) {
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

	public void processQueue() throws IOException, SlaveUnavailableException {
		// no for-each loop, needs iter.remove()
		for (Iterator<QueuedOperation> iter = _renameQueue.iterator(); iter
				.hasNext();) {
			QueuedOperation item = iter.next();
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
					iter.remove();
					commit();
				}
			} else { // rename
				String fileName = destFile
						.substring(destFile.lastIndexOf("/") + 1);
				String destDir = destFile.substring(0, destFile
						.lastIndexOf("/"));
				try {
					fetchResponse(SlaveManager.getBasicIssuer().issueRenameToSlave(this, sourceFile, destDir,
							fileName));
				} catch (RemoteIOException e) {
					if (!(e.getCause() instanceof FileNotFoundException)) {
						throw e.getCause();
					}
				} finally {
					iter.remove();
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
		// TODO move lastconnect time set to here
		setAvailable(true);
		logger.info("Slave added: '" + getName() + "' status: " + _status);
		GlobalContext.getEventService().publishAsync(new SlaveEvent("ADDSLAVE", this));
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
			logger
					.error(
							"IOException deleting file, file will be deleted when slave comes online",
							e);
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
			_indexPool = new LinkedBlockingDeque<String>(256);
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
			_indexWithCommands = new ConcurrentHashMap<String, AsyncResponse>();
		} else {
			_indexWithCommands.clear();
		}
		
		if (_transfers == null) {
			_transfers = new ConcurrentHashMap<TransferIndex, RemoteTransfer>();
		} else {
			_transfers.clear();
		}
		
		_errors = 0;
		_lastNetworkError = System.currentTimeMillis();
		_initRemergeCompleted = false;
		
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
			} catch (InterruptedException e1) {
			}
		}

		throw new SlaveUnavailableException(
				"Slave was offline or went offline while fetching an index");
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
		return fetchResponse(index, 60 * 1000);
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
				setOffline("Slave has taken too long while waiting for reply "
						+ index);
			}
		}

		if (!isOnline()) {
			throw new SlaveUnavailableException(
					"Slave went offline while processing command");
		}

		AsyncResponse rar = _indexWithCommands.remove(index);
		_indexPool.push(index);

		if (rar instanceof AsyncResponseException) {
			Throwable t = ((AsyncResponseException) rar).getThrowable();

			if (t instanceof IOException) {
				throw new RemoteIOException((IOException) t);
			}

			logger
					.error(
							"Exception on slave that is unable to be handled by the master",
							t);
			setOffline("Exception on slave that is unable to be handled by the master");
			throw new SlaveUnavailableException(
					"Exception on slave that is unable to be handled by the master");
		}
		return rar;
	}

	public synchronized String getPASVIP() throws SlaveUnavailableException {
		if (!isOnline())
			throw new SlaveUnavailableException();
		return getProperty("pasv_addr", _socket.getInetAddress()
				.getHostAddress());
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
		logger.debug("Starting RemoteSlave for " + getName());

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

				if (pingIndex == null
						&& ((getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastResponseReceived)) || (getActualTimeout() / 2 < (System
								.currentTimeMillis() - _lastCommandSent)))) {
					pingIndex = SlaveManager.getBasicIssuer().issuePingToSlave(this);
				} else if (getActualTimeout() < (System.currentTimeMillis() - _lastResponseReceived)) {
					setOffline("Slave seems to have gone offline, have not received a response in "
							+ (System.currentTimeMillis() - _lastResponseReceived)
							+ " milliseconds");
					throw new SlaveUnavailableException();
				}

				if (isOnline() && !isAvailable() && !_initRemergeCompleted) {
					int queueSize = CommitManager.getCommitManager().getQueueSize();
					if (_remergePaused.get()) {
						// Do we need to resume
						if (queueSize <= Integer.parseInt(GlobalContext.getConfig()
								.getMainProperties().getProperty("remerge.resume.threshold", "50"))) {
							SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(this);
							_remergePaused.set(false);
							logger.debug("Issued remerge resume to slave, current commit queue is " + queueSize);
						}
					} else {
						// Do we need to pause
						if (queueSize > Integer.parseInt(GlobalContext.getConfig()
								.getMainProperties().getProperty("remerge.pause.threshold", "250"))) {
							SlaveManager.getBasicIssuer().issueRemergePauseToSlave(this);
							_remergePaused.set(true);
							logger.debug("Issued remerge pause to slave, current commit queue is " + queueSize);
						}
					}
				}

				if (ar == null) {
					continue;
				}

				if (!(ar instanceof AsyncResponseRemerge)
						&& !(ar instanceof AsyncResponseTransferStatus)) {
					logger.debug("Received: " + ar);
				}

				if (ar instanceof AsyncResponseTransfer) {
					AsyncResponseTransfer art = (AsyncResponseTransfer) ar;
					addTransfer((art.getConnectInfo().getTransferIndex()),
							new RemoteTransfer(art.getConnectInfo(), this));
				}

				if (ar.getIndex().equals("Remerge")) {
					putRemergeQueue(new RemergeMessage((AsyncResponseRemerge) ar, this));
				} else if (ar.getIndex().equals("DiskStatus")) {
					_status = ((AsyncResponseDiskStatus) ar)
					.getDiskStatus();
				} else if (ar.getIndex().equals("TransferStatus")) {
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
				} else {
					_indexWithCommands.put(ar.getIndex(), ar);
					if (pingIndex != null
							&& pingIndex.equals(ar.getIndex())) {
						fetchResponse(pingIndex);
						pingIndex = null;
					} else {
						synchronized (_commandMonitor) {
							_commandMonitor.notifyAll();
						}
					}
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
		logger.debug("setOffline() " + reason);
		setOfflineReal(reason);
	}

	private void setOfflineReal(String reason) {
		// If the slave is still processing the remerge queue clear all
		// outstanding entries
		_remergeQueue.clear();
		if (_socket != null) {
			setProperty("lastOnline", Long.toString(System.currentTimeMillis()));
			try {
				_socket.close();
			} catch (IOException e) {
			}
			_socket = null;
		}
		_sin = null;
		_sout = null;
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
				logger.error("Throwing away an unexpected class - "
						+ obj.getClass().getName() + " - " + obj);
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
			throw new SlaveUnavailableException(
					"error sending command (exception already handled)", e);
		}
		_lastCommandSent = System.currentTimeMillis();
	}

	public boolean checkConnect(Socket socket) throws MalformedPatternException {
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
		StringTokenizer st = new StringTokenizer(getProperty("keywords", ""),
				" ");

		while (st.hasMoreElements()) {
			if (st.nextToken().equals(string)) {
				return true;
			}
		}

		return false;
	}

	public LinkedList<QueuedOperation> getRenameQueue() {
		return _renameQueue;
	}

	public void setRenameQueue(LinkedList<QueuedOperation> renameQueue) {
		_renameQueue = renameQueue;
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

	public void writeToDisk() throws IOException {
		XMLEncoder out = null;
		try {

			out = new XMLEncoder(new SafeFileOutputStream(
					(getGlobalContext().getSlaveManager().getSlaveFile(this
							.getName()))));
			out.setExceptionListener(new ExceptionListener() {
				public void exceptionThrown(Exception e) {
					logger.warn("", e);
				}
			});
			out.setPersistenceDelegate(Key.class,
					new DefaultPersistenceDelegate(new String[] { "owner", "key" }));
			out.setPersistenceDelegate(HostMask.class,
					new DefaultPersistenceDelegate(new String[] { "mask" }));
			out.setPersistenceDelegate(RemoteSlave.class,
					new DefaultPersistenceDelegate(new String[] { "name" }));
			out.setPersistenceDelegate(QueuedOperation.class,
					new DefaultPersistenceDelegate(new String[] { "source",
							"destination" }));
			try {
				PropertyDescriptor[] pdArr = Introspector.getBeanInfo(
						RemoteSlave.class).getPropertyDescriptors();
				ArrayList<String> transientList = new ArrayList<String>();
				transientList.addAll(Arrays.asList(transientFields));
				for (PropertyDescriptor pd : pdArr) {
					if (transientList.contains(pd.getName())) {
						pd.setValue("transient", Boolean.TRUE);
					}
				}
			} catch (IntrospectionException e1) {
				logger.error("I don't know what to do here", e1);
				throw new RuntimeException(e1);
			}
			out.writeObject(this);
			logger.debug("Wrote slavefile for " + this.getName());
		} catch (IOException ex) {
			throw new RuntimeException("Error writing slavefile for "
					+ this.getName() + ": " + ex.getMessage(), ex);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	public ObjectOutputStream getOutputStream() {
		return _sout;
	}
	
	public ObjectInputStream getInputStream() {
		return _sin;
	}

	private void putRemergeQueue(RemergeMessage message) {
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

	private class RemergeThread extends Thread {

		public RemergeThread(String slaveName) {
			super("RemergeThread - " + slaveName);
		}

		public void run() {
			while (true) {
				RemergeMessage msg;
				try {
					msg = _remergeQueue.take();
				} catch (InterruptedException e) {
					logger.info("", e);
					continue;
				}

				if (msg.isCompleted()) {
					msg.getRslave().makeAvailableAfterRemerge();
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
}
