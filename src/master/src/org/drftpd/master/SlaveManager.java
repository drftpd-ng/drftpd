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

import com.cedarsoftware.util.io.JsonReader;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.SSLGetContext;
import org.drftpd.exceptions.*;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.protocol.master.AbstractBasicIssuer;
import org.drftpd.protocol.master.AbstractIssuer;
import org.drftpd.protocol.master.MasterProtocolCentral;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.vfs.DirectoryHandle;

import javax.net.ssl.SSLSocket;
import java.beans.XMLDecoder;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveManager implements Runnable, TimeEventInterface {
	private static final Logger logger = LogManager.getLogger(SlaveManager.class
			.getName());

	private static final String slavePath = "userdata/slaves/";

	private static final int socketTimeout = 10000; // 10 seconds, for Socket

	protected static final int actualTimeout = 60000; // one minute, evaluated
														// on a SocketTimeout
	
	
	private static AbstractBasicIssuer _basicIssuer = null;
	
	protected Map<String,RemoteSlave> _rslaves = new ConcurrentHashMap<>();

	private int _port;

	protected ServerSocket _serverSocket;

	private boolean _sslSlaves;
	
	private MasterProtocolCentral _central;

    private boolean _listForSlaves = true;

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

		for (String slavepath : slavePathFile.list()) {

			if (!slavepath.endsWith(".xml") && !slavepath.endsWith(".json")) {
				continue;
			}

			String slavename = slavepath.substring(0, slavepath.lastIndexOf('.'));

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
		try (InputStream in = new FileInputStream(getSlaveFile(slavename));
			 JsonReader reader = new JsonReader(in)) {
            logger.debug("Loading slave '{}' Json data from disk.", slavename);
			RemoteSlave rslave = (RemoteSlave) reader.readObject();
			if (rslave.getName().equals(slavename)) {
				_rslaves.put(slavename,rslave);
				return rslave;
			}
			logger.warn("Tried to lookup a slave with the same name, different case", new Throwable());
			throw new ObjectNotFoundException();
		} catch (FileNotFoundException e) {
			// Lets see if there is a legacy xml slave file to load
			return getSlaveByXMLNameUnchecked(slavename);
		} catch (Exception e) {
			throw new FatalException("Error loading " + slavename + " : " + e.getMessage(), e);
		}
	}

	private RemoteSlave getSlaveByXMLNameUnchecked(String slavename)
			throws ObjectNotFoundException {
		RemoteSlave rslave;
		File xmlSlaveFile = getXMLSlaveFile(slavename);
		try (XMLDecoder in = new XMLDecoder(new BufferedInputStream(new FileInputStream(xmlSlaveFile)))) {
            logger.debug("Loading slave '{}' XML data from disk.", slavename);
			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			rslave = (RemoteSlave) in.readObject();
			Thread.currentThread().setContextClassLoader(prevCL);

			if (rslave.getName().equals(slavename)) {
				_rslaves.put(slavename,rslave);
				// Commit new json slave file and delete old xml
				rslave.commit();
				if (!xmlSlaveFile.delete()) {
                    logger.error("Failed to delete old xml slave file: {}", xmlSlaveFile.getName());
				}
				return rslave;
			}
			logger.warn("Tried to lookup a slave with the same name, different case", new Throwable());
			throw new ObjectNotFoundException();
		} catch (FileNotFoundException e) {
			throw new ObjectNotFoundException(e);
		} catch (Exception e) {
			throw new FatalException("Error loading " + slavename, e);
		}
	}

	protected File getSlaveFile(String slavename) {
		return new File(slavePath + slavename + ".json");
	}

	protected File getXMLSlaveFile(String slavename) {
		return new File(slavePath + slavename + ".xml");
	}

	protected void addShutdownHook() {
		// add shutdown hook last
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
getGlobalContext().getSlaveManager().listForSlaves(false);
logger.info("Running shutdown hook");
            for (RemoteSlave rslave : _rslaves.values()) {
                rslave.shutdown();
            }
}));
	}

	public void delSlave(String slaveName) {
		RemoteSlave rslave;

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
		HashMap<Long, RemoteSlave> map = new HashMap<>();

		for (RemoteSlave rslave : slaveList) {
			if (exemptSlaves.contains(rslave)) {
				continue;
			}

			Long size;

			try {
				size = rslave.getSlaveStatusAvailable().getDiskSpaceAvailable();
			} catch (SlaveUnavailableException e) {
				continue;
			}

			map.put(size, rslave);
		}

		ArrayList<Long> sorted = new ArrayList<>(map.keySet());

		if (ascending) {
			Collections.sort(sorted);
		} else {
			sorted.sort(Collections.reverseOrder());
		}

		HashSet<RemoteSlave> returnMe = new HashSet<>();

		for (ListIterator<Long> iter = sorted.listIterator(); iter.hasNext();) {
			if (iter.nextIndex() == numOfSlaves) {
				break;
			}
			Long key = iter.next();
			RemoteSlave rslave = map.get(key);
			returnMe.add(rslave);
		}

		return returnMe;
	}

	public RemoteSlave findSmallestFreeSlave() {
		Collection<RemoteSlave> slaveList = getSlaves();
		long smallSize = Integer.MAX_VALUE;
		RemoteSlave smallSlave = null;

		for (RemoteSlave rslave : slaveList) {
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

		for (RemoteSlave rslave : getSlaves()) {
			try {
				allStatus = allStatus.append(rslave.getSlaveStatusAvailable());
			} catch (SlaveUnavailableException e) {
				// slave is offline, continue
			}
		}

		return allStatus;
	}

	public HashMap<String, SlaveStatus> getAllStatusArray() {
		HashMap<String, SlaveStatus> ret = new HashMap<>(
                getSlaves().size());

		for (RemoteSlave rslave : getSlaves()) {
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
		ArrayList<RemoteSlave> availableSlaves = new ArrayList<>();

		for (RemoteSlave rslave : getSlaves()) {
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
		List<RemoteSlave> slaveList = new ArrayList<>(_rslaves.values());
		Collections.sort(slaveList);
		return slaveList;
	}

	/**
	 * Returns true if one or more slaves are online, false otherwise.
	 * 
	 * @return true if one or more slaves are online, false otherwise.
	 */
	public boolean hasAvailableSlaves() {
		for (RemoteSlave remoteSlave : _rslaves.values()) {
			if (remoteSlave.isAvailable()) {
				return true;
			}
		}
		return false;
	}

    public void listForSlaves(boolean portOpen) {
        _listForSlaves = portOpen;
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
            logger.info("Listening for slaves on port {}", _port);
		} catch (Exception e) {
			throw new FatalException(e);
		}

		Socket socket = null;

		while (_listForSlaves) {
			RemoteSlave rslave;
			ObjectInputStream in;
			ObjectOutputStream out;

			try {
				socket = _serverSocket.accept();
				socket.setSoTimeout(socketTimeout);
				if (socket instanceof SSLSocket) {
					if (GlobalContext.getConfig().getCipherSuites() != null) {
						((SSLSocket) socket).setEnabledCipherSuites(GlobalContext.getConfig().getCipherSuites());
					}
					if (GlobalContext.getConfig().getSSLProtocols() != null) {
						((SSLSocket) socket).setEnabledProtocols(GlobalContext.getConfig().getSSLProtocols());
					}
					((SSLSocket) socket).setUseClientMode(false);
					((SSLSocket) socket).startHandshake();
				}
                logger.debug("Slave connected from {}", socket.getRemoteSocketAddress());

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
                    logger.info("Slave {} does not exist, use \"site addslave\"", slavename);
					socket.close();
					continue;
				}

				if (rslave.isOnline()) {
					out.writeObject(new AsyncCommandArgument("", "error",
							"Already online"));
					out.flush();
					socket.close();
					throw new IOException("Already online: " + slavename);
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
                    logger.error("{} is not a valid ip for {}", socket.getInetAddress(), rslave.getName());
					socket.close();					
					
					continue;
				}
				
				rslave.connect(socket, in, out);
			} catch (Exception e) {
				rslave.setOffline(e);
				logger.error(e);
			} catch (Throwable t) {
				logger.fatal("Throwable in SlaveManager loop", t);
			}
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
	 * @param directory
	 */
	public void deleteOnAllSlaves(DirectoryHandle directory) {
		HashMap<RemoteSlave, String> slaveMap = new HashMap<>();
		Collection<RemoteSlave> slaves = new ArrayList<>(_rslaves.values());
		for (RemoteSlave rslave : slaves) {
			String index;
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
