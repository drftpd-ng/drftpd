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

import java.beans.XMLDecoder;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import javax.net.ssl.SSLSocket;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.SSLGetContext;
import org.drftpd.exceptions.FatalException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.exceptions.SSLUnavailableException;
import org.drftpd.exceptions.SlaveFileException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.protocol.master.AbstractBasicIssuer;
import org.drftpd.protocol.master.AbstractIssuer;
import org.drftpd.protocol.master.MasterProtocolCentral;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveManager implements Runnable, TimeEventInterface {
	private static final Logger logger = Logger.getLogger(SlaveManager.class
			.getName());

	private static final String slavePath = "slaves/";

	private static final int socketTimeout = 10000; // 10 seconds, for Socket

	protected static final int actualTimeout = 60000; // one minute, evaluated
														// on a SocketTimeout
	
	
	private static AbstractBasicIssuer _basicIssuer = null;
	
	protected Map<String,RemoteSlave> _rslaves = new ConcurrentHashMap<String,RemoteSlave>();

	private int _port;

	protected ServerSocket _serverSocket;

	private LinkedBlockingQueue<RemergeMessage> _remergeQueue = new LinkedBlockingQueue<RemergeMessage>();

	private RemergeThread _remergeThread;

	private boolean _sslSlaves;
	
	private MasterProtocolCentral _central;

	public SlaveManager() {
		
	}
	
	public SlaveManager(Properties p) throws SlaveFileException {
		_sslSlaves = p.getProperty("master.slaveSSL", "false").equalsIgnoreCase("true");
		try {
			if (_sslSlaves && GlobalContext.getGlobalContext().getSSLContext() == null) {
				throw new SSLUnavailableException("Secure connections to slave required but SSL isn't available");
			}
		} catch (Exception e) {
			throw new FatalException(e);
		}
		
		_port = Integer.parseInt(PropertyHelper.getProperty(p, "master.bindport"));
		_central = new MasterProtocolCentral();
		loadSlaves();
	}

	private void loadSlaves() throws SlaveFileException {
		File slavePathFile = new File(slavePath);
		if (!slavePathFile.exists() && !slavePathFile.mkdirs()) {
			throw new SlaveFileException(new IOException(
					"Error creating directories: " + slavePathFile));
		}

		String[] slavepaths = slavePathFile.list();

		for (int i = 0; i < slavepaths.length; i++) {
			String slavepath = slavepaths[i];

			if (!slavepath.endsWith(".xml")) {
				continue;
			}

			String slavename = slavepath.substring(0, slavepath.length()
					- ".xml".length());

			try {
				getSlaveByNameUnchecked(slavename);
			} catch (ObjectNotFoundException e) {
				throw new SlaveFileException(e);
			}

			// throws IOException
		}
	}

	public void newSlave(String slavename) {
		addSlave(new RemoteSlave(slavename));
	}

	public synchronized void addSlave(RemoteSlave rslave) {
		_rslaves.put(rslave.getName(), rslave);
	}

	private RemoteSlave getSlaveByNameUnchecked(String slavename)
			throws ObjectNotFoundException {
		if (slavename == null) {
			throw new NullPointerException();
		}

		RemoteSlave rslave = null;
		XMLDecoder in = null;

		try {
			in = new XMLDecoder(new FileInputStream(
					getSlaveFile(slavename)));

			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			rslave = (RemoteSlave) in.readObject();
			Thread.currentThread().setContextClassLoader(prevCL);

			if (rslave.getName().equals(slavename)) {
				_rslaves.put(slavename,rslave);
				return rslave;
			}
			logger
					.warn(
							"Tried to lookup a slave with the same name, different case",
							new Throwable());
			throw new ObjectNotFoundException();
		} catch (FileNotFoundException e) {
			throw new ObjectNotFoundException(e);
		} catch (Exception e) {
			throw new FatalException("Error loading " + slavename, e);
		} finally {
			if (in != null) {
				in.close();
			}
		}
	}

	protected File getSlaveFile(String slavename) {
		return new File(slavePath + slavename + ".xml");
	}

	protected void addShutdownHook() {
		// add shutdown hook last
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Running shutdown hook");
				for (RemoteSlave rslave : _rslaves.values()) {
					rslave.shutdown();
				}				
			}
		});
	}

	public void delSlave(String slaveName) {
		RemoteSlave rslave = null;

		try {
			rslave = getRemoteSlave(slaveName);
			getSlaveFile(rslave.getName()).delete();
			rslave.setOffline("Slave has been deleted");
			_rslaves.remove(slaveName);
			getGlobalContext().getRoot().removeSlave(rslave);
		} catch (ObjectNotFoundException e) {
			throw new IllegalArgumentException("Slave not found");
		} catch (FileNotFoundException e) {
			logger.debug("FileNotFoundException in delSlave()", e);
		}
	}

	public HashSet<RemoteSlave> findSlavesBySpace(int numOfSlaves,
			Set<RemoteSlave> exemptSlaves, boolean ascending) {
		Collection<RemoteSlave> slaveList = getSlaves();
		HashMap<Long, RemoteSlave> map = new HashMap<Long, RemoteSlave>();

		for (Iterator<RemoteSlave> iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();

			if (exemptSlaves.contains(rslave)) {
				continue;
			}

			Long size;

			try {
				size = Long.valueOf(rslave.getSlaveStatusAvailable()
						.getDiskSpaceAvailable());
			} catch (SlaveUnavailableException e) {
				continue;
			}

			map.put(size, rslave);
		}

		ArrayList<Long> sorted = new ArrayList<Long>(map.keySet());

		if (ascending) {
			Collections.sort(sorted);
		} else {
			Collections.sort(sorted, Collections.reverseOrder());
		}

		HashSet<RemoteSlave> returnMe = new HashSet<RemoteSlave>();

		for (ListIterator<Long> iter = sorted.listIterator(); iter.hasNext();) {
			if (iter.nextIndex() == numOfSlaves) {
				break;
			}
			Long key = iter.next();
			RemoteSlave rslave = (RemoteSlave) map.get(key);
			returnMe.add(rslave);
		}

		return returnMe;
	}

	public RemoteSlave findSmallestFreeSlave() {
		Collection<RemoteSlave> slaveList = getSlaves();
		long smallSize = Integer.MAX_VALUE;
		RemoteSlave smallSlave = null;

		for (Iterator<RemoteSlave> iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();
			long size = Integer.MAX_VALUE;

			try {
				size = rslave.getSlaveStatusAvailable().getDiskSpaceAvailable();
			} catch (SlaveUnavailableException e) {
				continue;
			}

			if (size < smallSize) {
				smallSize = size;
				smallSlave = rslave;
			}
		}

		return smallSlave;
	}

	/**
	 * Not cached at all since RemoteSlave objects cache their SlaveStatus
	 */
	public SlaveStatus getAllStatus() {
		SlaveStatus allStatus = new SlaveStatus();

		for (Iterator<RemoteSlave> iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();

			try {
				allStatus = allStatus.append(rslave.getSlaveStatusAvailable());
			} catch (SlaveUnavailableException e) {
				// slave is offline, continue
			}
		}

		return allStatus;
	}

	public HashMap<String, SlaveStatus> getAllStatusArray() {
		HashMap<String, SlaveStatus> ret = new HashMap<String, SlaveStatus>(
				getSlaves().size());

		for (Iterator<RemoteSlave> iter = getSlaves().iterator(); iter
				.hasNext();) {
			RemoteSlave rslave = iter.next();

			try {
				ret.put(rslave.getName(), rslave.getSlaveStatus());
			} catch (SlaveUnavailableException e) {
				ret.put(rslave.getName(), null);
			}
		}

		return ret;
	}

	/**
	 * Returns a modifiable list of available RemoteSlave's
	 */
	public Collection<RemoteSlave> getAvailableSlaves()
			throws NoAvailableSlaveException {
		ArrayList<RemoteSlave> availableSlaves = new ArrayList<RemoteSlave>();

		for (Iterator<RemoteSlave> iter = getSlaves().iterator(); iter
				.hasNext();) {
			RemoteSlave rslave = iter.next();

			if (!rslave.isAvailable()) {
				continue;
			}

			availableSlaves.add(rslave);
		}

		if (availableSlaves.isEmpty()) {
			throw new NoAvailableSlaveException("No slaves online");
		}

		return availableSlaves;
	}

	public GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public RemoteSlave getRemoteSlave(String s) throws ObjectNotFoundException {
		RemoteSlave rslave = _rslaves.get(s);
		if (rslave == null) {
			return getSlaveByNameUnchecked(s);
		}
		return rslave;
	}

	public List<RemoteSlave> getSlaves() {
		if (_rslaves == null) {
			throw new NullPointerException();
		}
		List<RemoteSlave> slaveList = new ArrayList<RemoteSlave>(_rslaves.values());
		Collections.sort(slaveList);
		return slaveList;
	}

	/**
	 * Returns true if one or more slaves are online, false otherwise.
	 * 
	 * @return true if one or more slaves are online, false otherwise.
	 */
	public boolean hasAvailableSlaves() {
		for (Iterator<RemoteSlave> iter = _rslaves.values().iterator(); iter.hasNext();) {
			if (iter.next().isAvailable()) {
				return true;
			}
		}
		return false;
	}

	public void run() {
		try {
			if (_sslSlaves) {
				_serverSocket = SSLGetContext.getSSLContext()
						.getServerSocketFactory().createServerSocket(_port);
			} else {
				_serverSocket = new ServerSocket(_port);
			}
			// _serverSocket.setReuseAddress(true);
			logger.info("Listening for slaves on port " + _port);
		} catch (Exception e) {
			throw new FatalException(e);
		}

		Socket socket = null;

		while (true) {
			RemoteSlave rslave = null;
			ObjectInputStream in = null;
			ObjectOutputStream out = null;

			try {
				socket = _serverSocket.accept();
				socket.setSoTimeout(socketTimeout);
				if (socket instanceof SSLSocket) {
					if (GlobalContext.getConfig().getCipherSuites() != null) {
						((SSLSocket) socket).setEnabledCipherSuites(GlobalContext.getConfig().getCipherSuites());
					}
					((SSLSocket) socket).setUseClientMode(false);
					((SSLSocket) socket).startHandshake();
				}
				logger.debug("Slave connected from "
						+ socket.getRemoteSocketAddress());

				out = new ObjectOutputStream(new BufferedOutputStream(socket.getOutputStream()));
				out.flush();
				in = new ObjectInputStream(new BufferedInputStream(socket.getInputStream()));

				String slavename = RemoteSlave.getSlaveNameFromObjectInput(in);

				try {
					rslave = getRemoteSlave(slavename);
				} catch (ObjectNotFoundException e) {
					out
							.writeObject(new AsyncCommandArgument(
									"error",
									"error",
									slavename
											+ " does not exist, use \"site addslave\""));
					logger.info("Slave " + slavename
							+ " does not exist, use \"site addslave\"");
					socket.close();
					continue;
				}

				if (rslave.isOnline()) {
					out.writeObject(new AsyncCommandArgument("", "error",
							"Already online"));
					out.flush();
					socket.close();
					throw new IOException("Already online");
				}
			} catch (Exception e) {
				if (socket != null) {
					try {
						socket.close();
					} catch (IOException e1) {
					}
				}

				logger.error("", e);

				continue;
			}

			try {
				if (!rslave.checkConnect(socket)) {
					out.writeObject(new AsyncCommandArgument("", "error",
							socket.getInetAddress()
									+ " is not a valid mask for "
									+ rslave.getName()));
					logger.error(socket.getInetAddress()
							+ " is not a valid ip for " + rslave.getName());
					socket.close();					
					
					continue;
				}
				
				rslave.connect(socket, in, out);
			} catch (Exception e) {
				rslave.setOffline(e);
				logger.error(e);
			} catch (Throwable t) {
				logger.fatal("Throwable in SalveManager loop", t);
			}
		}
	}

	public BlockingQueue<RemergeMessage> getRemergeQueue() {
		return _remergeQueue;
	}

	/**
	 * @param message
	 */
	public void putRemergeQueue(RemergeMessage message) {
		try {
			_remergeQueue.put(message);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
		if (_remergeThread == null || !_remergeThread.isAlive()) {
			_remergeThread = new RemergeThread();
			_remergeThread.start();
		}
	}

	/**
	 * Cancels all transfers in directory
	 */
	public void cancelTransfersInDirectory(DirectoryHandle dir) {
		for (RemoteSlave rs : getSlaves()) {
			try {
				for (RemoteTransfer rt : rs.getTransfers()) {
					String path = rt.getPathNull();
					if (path != null) {
						if (path.startsWith(dir.getPath())) {
							rt.abort("Directory is nuked");
						}
					}
				}
			} catch (SlaveUnavailableException ignore) {
			}
		}
	}

	/**
	 * Accepts directories and does the physical deletes asynchronously Waits
	 * for a response and handles errors on each slave Use
	 * RemoteSlave.simpleDelete(path) if you want to delete files
	 * 
	 * @param file
	 */
	public void deleteOnAllSlaves(DirectoryHandle directory) {
		HashMap<RemoteSlave, String> slaveMap = new HashMap<RemoteSlave, String>();
		Collection<RemoteSlave> slaves = new ArrayList<RemoteSlave>(_rslaves.values());
		for (RemoteSlave rslave : slaves) {
			String index = null;
			try {
				AbstractBasicIssuer basicIssuer = (AbstractBasicIssuer) getIssuerForClass(AbstractBasicIssuer.class); 
				index = basicIssuer.issueDeleteToSlave(rslave, directory.getPath());
				slaveMap.put(rslave, index);
			} catch (SlaveUnavailableException e) {
				rslave.addQueueDelete(directory.getPath());
			}
		}
		for (Entry<RemoteSlave,String> slaveEntry : slaveMap.entrySet()) {
			RemoteSlave rslave = slaveEntry.getKey();
			String index = slaveEntry.getValue();
			try {
				rslave.fetchResponse(index, 300000);
			} catch (SlaveUnavailableException e) {
				rslave.addQueueDelete(directory.getPath());
			} catch (RemoteIOException e) {
				if (e.getCause() instanceof FileNotFoundException) {
					continue;
				}
				rslave
						.setOffline("IOException deleting file, check logs for specific error");
				rslave.addQueueDelete(directory.getPath());
				logger
						.error(
								"IOException deleting file, file will be deleted when slave comes online",
								e);
				rslave.addQueueDelete(directory.getPath());
			}
		}
	}

	public void renameOnAllSlaves(String fromPath, String toDirPath,
			String toName) {
		synchronized (this) {
			for (RemoteSlave rslave : _rslaves.values()) {
				rslave.simpleRename(fromPath, toDirPath, toName);
			}
		}
	}
	
	public MasterProtocolCentral getProtocolCentral() {
		return _central;
	}
	
	public AbstractIssuer getIssuerForClass(Class<?> clazz) {
		return _central.getIssuerForClass(clazz);
	}

	public void resetDay(Date d) {
		for (RemoteSlave rs : _rslaves.values()) {
			rs.resetDay(d);
			rs.commit();
		}
	}

	public void resetHour(Date d) {
		for (RemoteSlave rs : _rslaves.values()) {
			rs.resetHour(d);
			rs.commit();
		}		
	}

	public void resetMonth(Date d) {
		for (RemoteSlave rs : _rslaves.values()) {
			rs.resetMonth(d);
			rs.commit();
		}	
	}

	public void resetWeek(Date d) {
		for (RemoteSlave rs : _rslaves.values()) {
			rs.resetWeek(d);
			rs.commit();
		}	
	}

	public void resetYear(Date d) {
		for (RemoteSlave rs : _rslaves.values()) {
			rs.resetYear(d);
			rs.commit();
		}	
	}

	public static AbstractBasicIssuer getBasicIssuer() {
		if (_basicIssuer == null) { // avoid unecessary lookups.
			_basicIssuer = (AbstractBasicIssuer) GlobalContext.getGlobalContext().getSlaveManager().
						getProtocolCentral().getIssuerForClass(AbstractBasicIssuer.class);
		}
		return _basicIssuer; 
	}
}

class RemergeThread extends Thread {
	private static final Logger logger = Logger.getLogger(RemergeThread.class);

	public RemergeThread() {
		super("RemergeThread");
	}

	public void run() {
		while (true) {
			RemergeMessage msg;
			try {
				msg = getGlobalContext().getSlaveManager().getRemergeQueue()
						.take();
			} catch (InterruptedException e) {
				logger.info("", e);
				continue;
			}

			if (msg.isCompleted()) {
				msg.getRslave().makeAvailableAfterRemerge();
				continue;
			}

			DirectoryHandle dir = new DirectoryHandle(msg.getDirectory());

			try {
				dir.remerge(msg.getFiles(), msg.getRslave(), msg.getLastModified());
			} catch (IOException e2) {
				logger.error("IOException during remerge", e2);
				msg.getRslave().setOffline("IOException during remerge");
			} catch (SlaveUnavailableException e) {
				logger.error("SlaveUnavailableException during remerge", e);
				msg.getRslave().setOffline("SlaveUnavailableException during remerge");
			}
		}
	}

	private static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}
}
