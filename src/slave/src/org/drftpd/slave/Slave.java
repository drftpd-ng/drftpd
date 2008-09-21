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

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;

import org.apache.log4j.Logger;
import org.drftpd.PropertyHelper;
import org.drftpd.SSLGetContext;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.SSLUnavailableException;
import org.drftpd.id3.ID3Tag;
import org.drftpd.id3.MP3File;
import org.drftpd.master.QueuedOperation;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseDiskStatus;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseTransferStatus;
import org.drftpd.slave.diskselection.DiskSelectionInterface;
import org.drftpd.util.PortRange;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

import se.mog.io.File;
import se.mog.io.PermissionDeniedException;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class Slave {
	public static final boolean isWin32 = System.getProperty("os.name").startsWith("Windows");

	private static final Logger logger = Logger.getLogger(Slave.class);

	private static final int socketTimeout = 10000; // 10 seconds, for Socket

	protected static final int actualTimeout = 60000; // one minute, evaluated on a SocketTimeout

	public static final String VERSION = "DrFTPD 2.1.0-dev";

	public static final String separator = "/";

	private int _bufferSize;

	private String[] _cipherSuites;

	private SSLContext _ctx;

	private boolean _downloadChecksums;

	private RootCollection _roots;

	private Socket _s;
	
	private ObjectInputStream _sin;

	private ObjectOutputStream _sout;

	private HashMap<TransferIndex, Transfer> _transfers;

	private boolean _uploadChecksums;

	private PortRange _portRange;

	private Set<QueuedOperation> _renameQueue = null;

	private int _timeout;

	private boolean _sslMaster;
	
	private SlaveProtocolCentral _central;
	
	private DiskSelectionInterface _diskSelection = null;

	protected Slave() {
	}

	public Slave(Properties p) throws IOException, SSLUnavailableException {
		InetSocketAddress addr = new InetSocketAddress(PropertyHelper
				.getProperty(p, "master.host"), Integer.parseInt(PropertyHelper
				.getProperty(p, "master.bindport")));
		
		// Whatever interface the slave uses to connect to the master, is the
		// interface that the master will report to clients requesting PASV
		// transfers from this slave, unless pasv_addr is set on the master for this slave
		logger.info("Connecting to master at " + addr);

		String slavename = PropertyHelper.getProperty(p, "slave.name");

		if (isWin32) {
			_renameQueue = new HashSet<QueuedOperation>();
		}

		try {
			_ctx = SSLGetContext.getSSLContext();
		} catch (Exception e) {
			logger.warn("Error loading SSLContext, no secure connections will be avaible.");
			_cipherSuites = null;
		}

		ArrayList<String> cipherSuites = new ArrayList<String>();
		for (int x = 1;; x++) {
			String cipherSuite = p.getProperty("cipher." + x);
			if (cipherSuite != null) {
				cipherSuites.add(cipherSuite);
			} else {
				break;
			}
		}
		if (cipherSuites.size() == 0) {
			_cipherSuites = null;
		} else {
			_cipherSuites = new String[cipherSuites.size()];
			for (int x = 0; x < _cipherSuites.length; x++) {
				_cipherSuites[x] = cipherSuites.get(x);
			}
		}

		_sslMaster = p.getProperty("slave.masterSSL", "false").equalsIgnoreCase("true");
		if (_sslMaster && _ctx == null) {
			throw new SSLUnavailableException("Secure connection to master enabled but SSL isn't ready");
		}

		if (_sslMaster) {
			_s = _ctx.getSocketFactory().createSocket();
		} else {
			_s = new Socket();
		}

		try {
			_timeout = Integer.parseInt(PropertyHelper.getProperty(p, "slave.timeout"));
		} catch (NullPointerException e) {
			_timeout = actualTimeout;
		}
		_s.setSoTimeout(socketTimeout);
		_s.connect(addr);
		if (_s instanceof SSLSocket) {
			if (getCipherSuites() != null) {
				((SSLSocket) _s).setEnabledCipherSuites(getCipherSuites());
			}
			((SSLSocket) _s).setUseClientMode(true);
			
			try {
				((SSLSocket) _s).startHandshake();
			} catch (SSLHandshakeException e) {
				throw new SSLUnavailableException("Handshake failure, maybe master isn't SSL ready or SSL is disabled.", e);
			}
		}
		_sout = new ObjectOutputStream(_s.getOutputStream());
		_sin = new ObjectInputStream(_s.getInputStream());

		_central = new SlaveProtocolCentral(this);
		
		_sout.writeObject(slavename);
		_sout.flush();
		_sout.reset();

		_uploadChecksums = p.getProperty("enableuploadchecksums", "true").equals("true");
		_downloadChecksums = p.getProperty("enabledownloadchecksums", "true").equals("true");
		_bufferSize = Integer.parseInt(p.getProperty("bufferSize", "0"));
	
		_roots = getDefaultRootBasket(p);
		loadDiskSelection(p);

		_transfers = new HashMap<TransferIndex, Transfer>();

		try {
			int minport = Integer.parseInt(p.getProperty("slave.portfrom"));
			int maxport = Integer.parseInt(p.getProperty("slave.portto"));
			_portRange = new PortRange(minport, maxport, _bufferSize);
		} catch (NumberFormatException e) {
			_portRange = new PortRange(_bufferSize);
		}
	}
	
	private void loadDiskSelection(Properties cfg) {
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint exp = manager.getRegistry().getExtensionPoint("slave", "DiskSelection");
		String desiredDiskSelection = PropertyHelper.getProperty(cfg, "diskselection");
		
		for (Extension ext : exp.getAvailableExtensions()) {
			String pluginId = ext.getDeclaringPluginDescriptor().getId();
			if (pluginId.equals(desiredDiskSelection)) {
				ClassLoader classLoader = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());
				String className = ext.getParameter("Class").valueAsString();

				try {
					if (!manager.isPluginActivated(ext.getDeclaringPluginDescriptor()))
						manager.activatePlugin(pluginId);

					Class<?> clazz = classLoader.loadClass(className);
					
					_diskSelection = (DiskSelectionInterface) clazz
											.getConstructor(new Class[] { Slave.class }).newInstance(new Object[] { this });
				} catch (Exception e) {
					throw new RuntimeException("Unable to load DiskSelection", e);
				}
			}
		}
		
		if (_diskSelection == null) {
			throw new RuntimeException("Unable to load DiskSelection");
		}
	}

	public DiskSelectionInterface getDiskSelection() {
		return _diskSelection;
	}
	
	public RootCollection getDefaultRootBasket(Properties cfg) throws IOException {
		ArrayList<Root> roots = new ArrayList<Root>();

		for (int i = 1; true; i++) {
			String rootString = cfg.getProperty("slave.root." + i);

			if (rootString == null) {
				break;
			}

			logger.info("slave.root." + i + ": " + rootString);
			roots.add(new Root(rootString));
		}

		return new RootCollection(this, roots);
	}

	public static void boot() throws Exception {
		System.out.println("DrFTPD Slave starting, further logging will be done through log4j");

		// Try using the JuiCE JCE if available

		Class<?> juiceJCE = null;
		Class<?> bcJCE = null;

		try {
			juiceJCE = Class.forName("org.apache.security.juice.provider.JuiCEProviderOpenSSL");
		} catch (ClassNotFoundException e) {
			logger.info("JuiCE JCE not installed, using java native JSSE");
		}

		// Only try installing JuiCE provider if the class was found

		if (juiceJCE != null) {
			int provider1Pos = java.security.Security.insertProviderAt((java.security.Provider) juiceJCE
				.newInstance(), 2);
			if (provider1Pos == -1) {
				logger.info("Loading of JuiCE JCE failed");
			}
			else {
				logger.debug("Juice JCE Provider successfully inserted at position: "+provider1Pos);
			}
			try {
				bcJCE = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
			} catch (ClassNotFoundException e) {
				logger.fatal("JuiCE JCE found but Bouncy Castle JCE not installed, please check installation");
			}
			if (bcJCE != null) {
				int provider2Pos = java.security.Security.insertProviderAt((java.security.Provider) bcJCE
					.newInstance(), 3);
				if (provider2Pos == -1) {
					logger.info("Loading of Bouncy Castle JCE failed");
				}
				else {
					logger.debug("Bouncy Castle JCE Provider successfully inserted at position: "+provider2Pos);
				}
			}
		}
		
		Properties p = new Properties();
		FileInputStream fis = new FileInputStream("slave.conf");
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
		s.listenForCommands();
	}

	public class FileLockRunnable implements Runnable {

		public void run() {
			while (true) {
				synchronized (_transfers) {
					try {
						_transfers.wait(5000);
					} catch (InterruptedException e) {
					}
					synchronized (_renameQueue) {
						for (Iterator<QueuedOperation> iter = _renameQueue.iterator(); iter
								.hasNext();) {
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
									throw new RuntimeException("Win32 stinks",
											e);
								}
							} else { // rename
								String fileName = qo.getDestination()
										.substring(
												qo.getDestination()
														.lastIndexOf("/") + 1);
								String destDir = qo.getDestination()
										.substring(
												0,
												qo.getDestination()
														.lastIndexOf("/"));
								try {
									rename(qo.getSource(), destDir, fileName);
									// rename successful
									iter.remove();
								} catch (PermissionDeniedException e) {
									// keep it in the queue
								} catch (FileNotFoundException e) {
									iter.remove();
								} catch (IOException e) {
									throw new RuntimeException("Win32 stinks",
											e);
								}
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
		synchronized (_transfers) {
			_transfers.put(transfer.getTransferIndex(), transfer);
		}
	}

	public long checkSum(String path) throws IOException {
		logger.debug("Checksumming: " + path);

		CheckedInputStream in = null;

		try {
			CRC32 crc32 = new CRC32();
			in = new CheckedInputStream(new FileInputStream(_roots
					.getFile(path)), crc32);

			byte[] buf = new byte[4096];

			while (in.read(buf) != -1) {
			}

			return crc32.getValue();
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	public void delete(String path) throws IOException {
		// now deletes files as well as directories, recursive!
		Collection<Root> files = null;
		try {
			files = _roots.getMultipleRootsForFile(path);
		} catch (FileNotFoundException e) {
			// all is good, it's already gone
			return;
		}

		for (Iterator<Root> iter = files.iterator(); iter.hasNext();) {
			Root root = iter.next();
			File file = root.getFile(path);

			if (!file.exists()) {
				iter.remove();
				continue;
				// should never occur
			}

			if (file.isDirectory()) {
				if (!file.deleteRecursive()) {
					throw new PermissionDeniedException("delete failed on "
							+ path);
				}
				logger.info("DELETEDIR: " + path);
			} else if (file.isFile()) {
				File dir = new File(file.getParentFile());
				logger.info("DELETE: " + path);
				file.delete();

				String [] dirList = dir.list();

				while ((dirList != null) &&
				       (dirList.length == 0)) {
					if (dir.getPath().length() <= root.getPath().length()) {
						break;
					}

					java.io.File tmpFile = dir.getParentFile();

					dir.delete();
					logger.info("rmdir: " + dir.getPath());

					if (tmpFile == null) {
						break;
					}
					dir = new File(tmpFile);

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

	public ID3Tag getID3v1Tag(String path) throws IOException {
		String absPath = _roots.getFile(path).getAbsolutePath();
		logger.warn("Extracting ID3Tag info from: " + absPath);

		MP3File mp3 = null;
		try {
			mp3 = new MP3File(absPath, "r");

			if (!mp3.hasID3v1Tag) {
				mp3.close();
				throw new IOException("No id3tag found for " + absPath);
			}

			ID3Tag id3tag = mp3.readID3v1Tag();
			mp3.close();

			return id3tag;
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException: ", e);
		} catch (IOException e) {
			logger.warn("IOException: ", e);
		} finally {
			if (mp3 != null)
				mp3.close();
		}

		return null;
	}

	public RootCollection getRoots() {
		return _roots;
	}

	/*private String getDIZFile(String path) throws IOException {
		ZipEntry zipEntry = null;
		ZipInputStream zipInput = null;
		byte[] buf = new byte[20 * 1024];
		int numRd;
		try {

			zipInput = new ZipInputStream(new BufferedInputStream(
					new FileInputStream(_roots.getFile(path))));

			// Access a list of all of the files in the zip archive
			while ((zipEntry = zipInput.getNextEntry()) != null) {
				// Is this entry a DIZ file?
				if (zipEntry.getName().toLowerCase().endsWith(".diz")) {
					// Read 20 KBytes from the DIZ file, hopefully this
					// will be the entire file.
					numRd = zipInput.read(buf, 0, 20 * 1024);

					if (numRd > 0) {
						return new String(buf, 0, numRd);
					} else {
						throw new FileNotFoundException(
								"0 bytes read from .zip file - " + path);
					}

				}
			}
		} catch (Throwable t) {
			logger.error("Error extracting .diz from zipfile", t);
		} finally {
			try {
				if (zipInput != null) {
					zipInput.close();
				}
			} catch (IOException e) {
			}
		}
		throw new FileNotFoundException("No diz entry in - " + path);
	}*/

	public DiskStatus getDiskStatus() {
		return new DiskStatus(_roots.getTotalDiskSpaceAvailable(), _roots.getTotalDiskSpaceCapacity());
	}

	public Transfer getTransfer(TransferIndex index) {
		synchronized (_transfers) {
			return _transfers.get(index);
		}
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
			AsyncCommandArgument ac = null;

			try {
				ac = (AsyncCommandArgument) _sin.readObject();

				if (ac == null) {
					continue;
				}
				lastCommandReceived = System.currentTimeMillis();
			} catch (ClassNotFoundException e) {
				throw new RuntimeException(e);
			} catch (EOFException e) {
				logger
						.debug("Lost connection to the master, may have been kicked offline");
				return;
			} catch (SocketTimeoutException e) {
				// if no communication for slave.timeout (_timeout) time, than
				// connection to the master is dead or there is a configuration
				// error
				if (_timeout < (System.currentTimeMillis() - lastCommandReceived)) {
					logger
							.error("Slave is going offline as it hasn't received any communication from the master in "
									+ (System.currentTimeMillis() - lastCommandReceived)
									+ " milliseconds");
					throw new RuntimeException(e);
				}
				continue;
			}

			logger.debug("Slave fetched " + ac);
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
		synchronized (_renameQueue) {
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
	}

	public void removeTransfer(Transfer transfer) {
		synchronized (_transfers) {
			if (_transfers.remove(transfer.getTransferIndex()) == null) {
				throw new IllegalStateException();
			}
			_transfers.notifyAll();
		}
	}

	public void rename(String from, String toDirPath, String toName)
			throws IOException {
		for (Iterator<Root> iter = _roots.iterator(); iter.hasNext();) {
			Root root = iter.next();

			File fromfile = root.getFile(from);

			if (!fromfile.exists()) {
				continue;
			}

			File toDir = root.getFile(toDirPath);
			toDir.mkdirs();

			File tofile = new File(toDir.getPath() + File.separator + toName);

			// !win32 == true on linux
			// !win32 && equalsignore == true on win32
			if (tofile.exists()
					&& !(isWin32 && fromfile.getName().equalsIgnoreCase(toName))) {
				throw new FileExistsException("cannot rename from " + fromfile
						+ " to " + tofile + ", destination exists");
			}

			if (!fromfile.renameTo(tofile)) {
				throw new PermissionDeniedException("renameTo(" + fromfile
						+ ", " + tofile + ") failed");
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
				logger.debug("Slave wrote response - " + response);
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
		synchronized (_transfers) {
			return new ArrayList<Transfer>(_transfers.values());
		}
	}

	public String[] getCipherSuites() {
		// returns null if none are configured explicitly
		if (_cipherSuites == null) {
			return null;
		}
		return _cipherSuites;
	}
	
	// 00000000000000000000000000
	
	
	public HashMap<TransferIndex, Transfer> getTransferMap() {
		synchronized (_transfers) {
			return new HashMap<TransferIndex, Transfer>(_transfers);
		}
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
	
	public ObjectInputStream getInputStream() {
		return _sin;
	}
	
	public ObjectOutputStream getOutputStream() {
		return _sout;
	}
	
	public SlaveProtocolCentral getProtocolCentral() {
		return _central;
	}
}
