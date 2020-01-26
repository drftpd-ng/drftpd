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
package org.drftpd.slave;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.PropertyHelper;
import org.drftpd.SSLGetContext;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.SSLUnavailableException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.io.PhysicalFile;
import org.drftpd.master.QueuedOperation;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.slave.async.*;
import org.drftpd.slave.diskselection.DiskSelectionInterface;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PortRange;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class Slave {
	public static final boolean isWin32 = System.getProperty("os.name").startsWith("Windows");

	private static final Logger logger = LogManager.getLogger(Slave.class);

	private static final int socketTimeout = 10000; // 10 seconds, for Socket

	private static final int actualTimeout = 60000; // one minute, evaluated on a SocketTimeout

	public static final String separator = "/";

	private int _bufferSize;

	private String[] _cipherSuites;

	private String[] _sslProtocols;

	private SSLContext _ctx;

	private boolean _downloadChecksums;

	private RootCollection _roots;

	private Socket _socket;

	private ObjectInputStream _sin;

	private ObjectOutputStream _sout;

	private Map<TransferIndex, Transfer> _transfers;

	private boolean _uploadChecksums;

	private PortRange _portRange;

	private Set<QueuedOperation> _renameQueue = null;

	private int _timeout;

	private SlaveProtocolCentral _central;

	private DiskSelectionInterface _diskSelection = null;

	private boolean _ignorePartialRemerge;

	private boolean _threadedRemerge;

	private boolean _concurrentRootIteration;

	private String _bindIP = null;

	private boolean _online;

	protected Slave() {
	}

	public Slave(Properties p) throws IOException, SSLUnavailableException {
		InetSocketAddress addr = new InetSocketAddress(PropertyHelper.getProperty(p, "master.host"),
				Integer.parseInt(PropertyHelper.getProperty(p, "master.bindport")));

		// Whatever interface the slave uses to connect to the master, is the
		// interface that the master will report to clients requesting PASV
		// transfers from this slave, unless pasv_addr is set on the master for this
		// slave
        logger.info("Connecting to master at {}", addr);

		String slavename = PropertyHelper.getProperty(p, "slave.name");

		if (isWin32) {
			_renameQueue = Collections.newSetFromMap(new ConcurrentHashMap<>());
		}

		try {
			_ctx = SSLGetContext.getSSLContext();
		} catch (Exception e) {
			logger.warn("Error loading SSLContext, no secure connections will be available.");
			_cipherSuites = null;
			_sslProtocols = null;
		}

		List<String> cipherSuites = new ArrayList<>();
		List<String> supportedCipherSuites = new ArrayList<>();
		try {
			supportedCipherSuites
					.addAll(Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getCipherSuites()));
		} catch (Exception e) {
			logger.error("Unable to get supported cipher suites, using default.", e);
		}
		// Parse cipher suite whitelist rules
		boolean whitelist = false;
		for (int x = 1;; x++) {
			String whitelistPattern = p.getProperty("cipher.whitelist." + x);
			if (whitelistPattern == null) {
				break;
			} else if (whitelistPattern.trim().length() == 0) {
				continue;
			}
			if (!whitelist)
				whitelist = true;
			for (String cipherSuite : supportedCipherSuites) {
				if (cipherSuite.matches(whitelistPattern)) {
					cipherSuites.add(cipherSuite);
				}
			}
		}
		if (cipherSuites.isEmpty()) {
			// No whitelist rule or whitelist pattern bad, add default set
			cipherSuites.addAll(supportedCipherSuites);
			if (whitelist) {
				// There are at least one whitelist pattern specified
				logger.warn("Bad whitelist pattern, no matching ciphers found. "
						+ "Adding default cipher set before continuing with blacklist check");
			}
		}
		// Parse cipher suite blacklist rules and remove matching ciphers from set
		for (int x = 1;; x++) {
			String blacklistPattern = p.getProperty("cipher.blacklist." + x);
			if (blacklistPattern == null) {
				break;
			} else if (blacklistPattern.trim().isEmpty()) {
				continue;
			}
			cipherSuites.removeIf(cipherSuite -> cipherSuite.matches(blacklistPattern));
		}
		if (cipherSuites.isEmpty()) {
			_cipherSuites = null;
		} else {
			_cipherSuites = cipherSuites.toArray(new String[cipherSuites.size()]);
		}

		List<String> sslProtocols = new ArrayList<>();
		List<String> supportedSSLProtocols;
		try {
			supportedSSLProtocols = Arrays.asList(SSLContext.getDefault().getSupportedSSLParameters().getProtocols());
			for (int x = 1;; x++) {
				String sslProtocol = p.getProperty("protocol." + x);
				if (sslProtocol == null) {
					break;
				} else if (supportedSSLProtocols.contains(sslProtocol)) {
					sslProtocols.add(sslProtocol);
				}
			}
		} catch (Exception e) {
			logger.error("Unable to get supported SSL protocols, using default.", e);
		}
		if (sslProtocols.size() == 0) {
			_sslProtocols = null;
		} else {
			_sslProtocols = sslProtocols.toArray(new String[sslProtocols.size()]);
		}

		boolean sslMaster = p.getProperty("slave.masterSSL", "false").equalsIgnoreCase("true");
		if (sslMaster && _ctx == null) {
			throw new SSLUnavailableException("Secure connection to master enabled but SSL isn't ready");
		}

		if (sslMaster) {
			_socket = _ctx.getSocketFactory().createSocket();
		} else {
			_socket = new Socket();
		}

		if (PropertyHelper.getProperty(p, "bind.ip", null) != null) {
			try {
				_socket.bind(new InetSocketAddress(PropertyHelper.getProperty(p, "bind.ip"), 0));
				_bindIP = PropertyHelper.getProperty(p, "bind.ip", null);
			} catch (IOException e) {
				throw new IOException("Unable To Bind Port Correctly");
			}
		}

		try {
			_timeout = Integer.parseInt(PropertyHelper.getProperty(p, "slave.timeout"));
		} catch (NullPointerException e) {
			_timeout = actualTimeout;
		}
		_socket.setSoTimeout(socketTimeout);
		_socket.connect(addr);
		if (_socket instanceof SSLSocket) {
			if (getCipherSuites() != null) {
				((SSLSocket) _socket).setEnabledCipherSuites(getCipherSuites());
			}
			if (getSSLProtocols() != null) {
				((SSLSocket) _socket).setEnabledProtocols(getSSLProtocols());
			}
			((SSLSocket) _socket).setUseClientMode(true);

			try {
				((SSLSocket) _socket).startHandshake();
			} catch (SSLHandshakeException e) {
				throw new SSLUnavailableException("Handshake failure, maybe master isn't SSL ready or SSL is disabled.",
						e);
			}
		}
		_sout = new ObjectOutputStream(new BufferedOutputStream(_socket.getOutputStream()));
		_sout.flush();
		_sin = new ObjectInputStream(new BufferedInputStream(_socket.getInputStream()));

		_central = new SlaveProtocolCentral(this);

		_sout.writeObject(slavename);
		_sout.flush();
		_sout.reset();

		_uploadChecksums = p.getProperty("enableuploadchecksums", "true").equals("true");
		_downloadChecksums = p.getProperty("enabledownloadchecksums", "true").equals("true");
		_bufferSize = Integer.parseInt(p.getProperty("bufferSize", "0"));

		_concurrentRootIteration = p.getProperty("concurrent.root.iteration", "false").equalsIgnoreCase("true");
		_roots = getDefaultRootBasket(p);
		loadDiskSelection(p);

		_transfers = new ConcurrentHashMap<>();

		try {
			int minport = Integer.parseInt(p.getProperty("slave.portfrom"));
			int maxport = Integer.parseInt(p.getProperty("slave.portto"));
			_portRange = new PortRange(minport, maxport, _bufferSize);
		} catch (NumberFormatException e) {
			_portRange = new PortRange(_bufferSize);
		}

		_ignorePartialRemerge = p.getProperty("ignore.partialremerge", "false").equalsIgnoreCase("true");
		_threadedRemerge = p.getProperty("threadedremerge", "false").equalsIgnoreCase("true");
	}

	private void loadDiskSelection(Properties cfg) {
		String desiredDs = PropertyHelper.getProperty(cfg, "diskselection");
		try {
			_diskSelection = CommonPluginUtils.getSinglePluginObject(this, "slave", "DiskSelection", "Class", desiredDs,
					new Class[] { Slave.class }, new Object[] { this });
		} catch (Exception e) {
			throw new RuntimeException(
					"Cannot create instance of diskselection, check 'diskselection' in the configuration file", e);
		}
	}

	public DiskSelectionInterface getDiskSelection() {
		return _diskSelection;
	}

	public RootCollection getDefaultRootBasket(Properties cfg) throws IOException {
		ArrayList<Root> roots = new ArrayList<>();

		for (int i = 1; true; i++) {
			String rootString = cfg.getProperty("slave.root." + i);

			if (rootString == null) {
				break;
			}

            logger.info("slave.root.{}: {}", i, rootString);
			roots.add(new Root(rootString));
		}

		return new RootCollection(this, roots);
	}

	public static void boot() throws Exception {
		System.out.println("DrFTPD " + CommonPluginUtils.getPluginVersionForObject(Slave.class)
				+ " Slave starting, further logging will be done through log4j");

		Thread.currentThread().setName("Slave Main Thread");

		Properties p = new Properties();
		FileInputStream fis = new FileInputStream("conf/slave.conf");
		p.load(fis);
		fis.close();

		Slave s = new Slave(p);
		s.getProtocolCentral().handshakeWithMaster();

		if (isWin32) {
			s.startFileLockThread();
		}
		try {
			s.sendResponse(new AsyncResponseDiskStatus(s.getDiskStatus()));
		} catch (Throwable t) {
			logger.fatal("Error, check config on master for this slave");
		}
		s.setOnline(true);
		try {
			s.listenForCommands();
		} finally {
			s.shutdown();
		}
	}

	public void shutdown() {
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
			try {
				_socket.close();
			} catch (IOException e) {
			}
			_socket = null;
		}
		setOnline(false);
	}

	public void setOnline(boolean online) {
		_online = online;
	}

	public boolean isOnline() {
		return _online;
	}

	public class FileLockRunnable implements Runnable {

		public void run() {
			while (true) {
				synchronized (_transfers) {
					try {
						_transfers.wait(5000);
					} catch (InterruptedException e) {
					}
					for (Iterator<QueuedOperation> iter = _renameQueue.iterator(); iter.hasNext();) {
						QueuedOperation qo = iter.next();
						if (qo.getDestination() == null) { // delete
							try {
								delete(qo.getSource());
								// delete successful
								iter.remove();
							} catch (PermissionDeniedException e) {
								// keep it in the queue
							} catch (FileNotFoundException e) {
								iter.remove();
							} catch (IOException e) {
								throw new RuntimeException("Win32 stinks", e);
							}
						} else { // rename
							String fileName = qo.getDestination().substring(qo.getDestination().lastIndexOf("/") + 1);
							String destDir = qo.getDestination().substring(0, qo.getDestination().lastIndexOf("/"));
							try {
								rename(qo.getSource(), destDir, fileName);
								// rename successful
								iter.remove();
							} catch (PermissionDeniedException e) {
								// keep it in the queue
							} catch (FileNotFoundException e) {
								iter.remove();
							} catch (IOException e) {
								throw new RuntimeException("Win32 stinks", e);
							}
						}
					}
				}
			}
		}
	}

	private void startFileLockThread() {
		Thread t = new Thread(new FileLockRunnable());
		t.setName("FileLockThread");
		t.start();
	}

	public void addTransfer(Transfer transfer) {
		_transfers.put(transfer.getTransferIndex(), transfer);
	}

	public long checkSum(String path) throws IOException {
		return checkSum(_roots.getFile(path));
	}

	public long checkSum(PhysicalFile file) throws IOException {
        logger.debug("Checksumming: {}", file.getPath());

		CRC32 crc32 = new CRC32();
		try (CheckedInputStream in = new CheckedInputStream(new BufferedInputStream(new FileInputStream(file)),
				crc32)) {
			byte[] buf = new byte[16384];
			while (in.read(buf) != -1) {
			}
			return crc32.getValue();
		}
	}

	public void delete(String path) throws IOException {
		// now deletes files as well as directories, recursive!
		Collection<Root> files;
		try {
			files = _roots.getMultipleRootsForFile(path);
		} catch (FileNotFoundException e) {
			// all is good, it's already gone
			return;
		}

		for (Iterator<Root> iter = files.iterator(); iter.hasNext();) {
			Root root = iter.next();
			PhysicalFile file = root.getFile(path);

			if (!file.exists()) {
				iter.remove();
				continue;
				// should never occur
			}

			if (file.isDirectory()) {
				if (!file.deleteRecursive()) {
					throw new PermissionDeniedException("delete failed on " + path);
				}
                logger.info("DELETEDIR: {}", path);
			} else if (file.isFile()) {
				File dir = new PhysicalFile(file.getParentFile());
                logger.info("DELETE: {}", path);
                logger.info("rmfile: {}", file.getPath());
				if (!file.delete()) {
					throw new PermissionDeniedException("delete failed on " + path);
				}

				String[] dirList = dir.list();

				// If the parent directory is empty, then loop to delete it along with empty
				// parents
				while ((dirList != null) && (dirList.length == 0)) {
					// Stop at the root
					if (dir.getPath().length() <= root.getPath().length()) {
						break;
					}

					// Get the parent dir
					java.io.File tmpFile = dir.getParentFile();

					try {
						if (Files.deleteIfExists(dir.toPath())) {
                            logger.info("Dir empty, rmdir: {}", dir.getPath());
						} else {
                            logger.info("dir was empty, but doesn't exist anymore, that is fine {}", dir.getPath());
						}
					} catch (DirectoryNotEmptyException dnee) {
                        logger.info("dir was not empty, that is fine, we keep {}", dir.getPath());
						break;
					}

					// If the parent dir doesn't exist, break the loop
					if (tmpFile == null) {
						break;
					}

					// Rearm the loop on the parent dir
					dir = new PhysicalFile(tmpFile);
					dirList = dir.list();
				}
			}
		}
	}

	public int getBufferSize() {
		return _bufferSize;
	}

	public boolean getDownloadChecksums() {
		return _downloadChecksums;
	}

	public RootCollection getRoots() {
		return _roots;
	}

	public DiskStatus getDiskStatus() {
		return new DiskStatus(_roots.getTotalDiskSpaceAvailable(), _roots.getTotalDiskSpaceCapacity());
	}

	public Transfer getTransfer(TransferIndex index) {
		return _transfers.get(index);
	}

	public boolean getUploadChecksums() {
		return _uploadChecksums;
	}

	private AsyncResponse handleCommand(AsyncCommandArgument ac) {
		return _central.handleCommand(ac);
	}

	private void listenForCommands() throws IOException {
		long lastCommandReceived = System.currentTimeMillis();
		while (true) {
			AsyncCommandArgument ac;

			try {
				ac = (AsyncCommandArgument) _sin.readObject();

				if (ac == null) {
					continue;
				}
				lastCommandReceived = System.currentTimeMillis();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} catch (EOFException e) {
				logger.debug("Lost connection to the master, may have been kicked offline");
				return;
			} catch (SocketTimeoutException e) {
				// if no communication for slave.timeout (_timeout) time, than
				// connection to the master is dead or there is a configuration
				// error
				if (_timeout < (System.currentTimeMillis() - lastCommandReceived)) {
                    logger.error("Slave is going offline as it hasn't received any communication from the master in {} milliseconds", System.currentTimeMillis() - lastCommandReceived);
					throw new RuntimeException(e);
				}
				continue;
			}

            logger.debug("Slave fetched {}", ac);
			class AsyncCommandHandler implements Runnable {
				private AsyncCommandArgument _command = null;

				public AsyncCommandHandler(AsyncCommandArgument command) {
					_command = command;
				}

				public void run() {
					try {
						sendResponse(handleCommand(_command));
					} catch (Throwable e) {
						sendResponse(new AsyncResponseException(_command.getIndex(), e));
					}
				}
			}
			Thread t = new Thread(new AsyncCommandHandler(ac));
			t.setName("AsyncCommandHandler - " + ac.getClass());
			t.start();
		}
	}

	public String mapPathToRenameQueue(String path) {
		if (!isWin32) { // there is no renameQueue
			return path;
		}
		for (QueuedOperation qo : _renameQueue) {
			if (qo.getDestination() == null) {
				continue;
			}
			if (qo.getDestination().equals(path)) {
				return qo.getSource();
			}
		}
		return path;
	}

	public void removeTransfer(Transfer transfer) {
		// Synchronization only needed for Win32 to notify FileLockThread
		if (isWin32) {
			synchronized (_transfers) {
				if (_transfers.remove(transfer.getTransferIndex()) == null) {
					throw new IllegalStateException();
				}
				_transfers.notifyAll();
			}
		} else {
			if (_transfers.remove(transfer.getTransferIndex()) == null) {
				throw new IllegalStateException();
			}
		}
	}

	public void rename(String from, String toDirPath, String toName) throws IOException {
		for (Iterator<Root> iter = _roots.iterator(); iter.hasNext();) {
			Root root = iter.next();

			File fromfile = root.getFile(from);

			if (!fromfile.exists()) {
				continue;
			}

			File toDir = root.getFile(toDirPath);
			File tofile = new File(toDir.getPath() + File.separator + toName);

			if (!toDir.exists() && !toDir.mkdirs()) {
				throw new PermissionDeniedException(
						"renameTo(" + fromfile + ", " + tofile + ") failed to create destination folder");
			}

			// !win32 == true on linux
			// !win32 && equalsignore == true on win32
			if (tofile.exists() && !(isWin32 && fromfile.getName().equalsIgnoreCase(toName))) {
				throw new FileExistsException(
						"cannot rename from " + fromfile + " to " + tofile + ", destination exists");
			}

			if (!fromfile.renameTo(tofile)) {
				throw new PermissionDeniedException("renameTo(" + fromfile + ", " + tofile + ") failed");
			}
		}
	}

	public synchronized void sendResponse(AsyncResponse response) {
		if (response == null) {
			// handler doesn't return anything or it sends reply on it's own
			// (threaded for example)
			return;
		}

		try {
			_sout.writeObject(response);
			_sout.flush();
			_sout.reset();
			if (!(response instanceof AsyncResponseTransferStatus)) {
                logger.debug("Slave wrote response - {}", response);
			}

			if (response instanceof AsyncResponseException) {
				logger.debug("", ((AsyncResponseException) response).getThrowable());
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * @return The current list of Transfer objects
	 */
	public ArrayList<Transfer> getTransfersList() {
		return new ArrayList<>(_transfers.values());
	}

	public String[] getCipherSuites() {
		// returns null if none are configured explicitly
		if (_cipherSuites == null) {
			return null;
		}
		return _cipherSuites;
	}

	public String[] getSSLProtocols() {
		// returns null if none are configured explicitly
		if (_sslProtocols == null) {
			return null;
		}
		return _sslProtocols;
	}

	public Map<TransferIndex, Transfer> getTransferMap() {
		return _transfers;
	}

	public SSLContext getSSLContext() {
		return _ctx;
	}

	public Set<QueuedOperation> getRenameQueue() {
		return _renameQueue;
	}

	public PortRange getPortRange() {
		return _portRange;
	}

	public String getBindIP() {
		return _bindIP;
	}

	public ObjectInputStream getInputStream() {
		return _sin;
	}

	public ObjectOutputStream getOutputStream() {
		return _sout;
	}

	public SlaveProtocolCentral getProtocolCentral() {
		return _central;
	}

	public boolean ignorePartialRemerge() {
		return _ignorePartialRemerge;
	}

	public boolean threadedRemerge() {
		return _threadedRemerge;
	}

	public boolean concurrentRootIteration() {
		return _concurrentRootIteration;
	}
}
