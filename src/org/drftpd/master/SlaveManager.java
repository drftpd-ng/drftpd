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

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;

import org.drftpd.io.SafeFileWriter;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.MLSTSerialize;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.async.AsyncCommandArgument;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

import org.drftpd.usermanager.UserFileException;

import java.beans.XMLDecoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.ServerSocket;
import java.net.Socket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveManager implements Runnable {
	private static final Logger logger = Logger.getLogger(SlaveManager.class
			.getName());

	private static final String slavePath = "slaves/";

	private static final File slavePathFile = new File(slavePath);

	protected GlobalContext _gctx;

	protected List<RemoteSlave> _rslaves = new ArrayList<RemoteSlave>();

	private int _port;

	protected ServerSocket _serverSocket;

	private LinkedBlockingQueue<RemergeMessage> _remergeQueue = new LinkedBlockingQueue<RemergeMessage>();

	private RemergeThread _remergeThread;

	public SlaveManager(Properties p, GlobalContext gctx)
			throws SlaveFileException {
		this();
		_gctx = gctx;
		_port = Integer.parseInt(PropertyHelper.getProperty(p,
				"master.bindport"));
		loadSlaves();
	}

	/**
	 * For JUnit tests
	 */
	public SlaveManager() {
	}

	private void loadSlaves() throws SlaveFileException {
		if (!slavePathFile.exists() && !slavePathFile.mkdirs()) {
			throw new SlaveFileException(new IOException(
					"Error creating folders: " + slavePathFile));
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

		Collections.sort(_rslaves);
	}

	public void newSlave(String slavename) {
		addSlave(new RemoteSlave(slavename, getGlobalContext()));
	}

	public void addSlave(RemoteSlave rslave) {
		_rslaves.add(rslave);
		Collections.sort(_rslaves);
	}

	private RemoteSlave getSlaveByNameUnchecked(String slavename)
			throws ObjectNotFoundException {
		if (slavename == null) {
			throw new NullPointerException();
		}

		RemoteSlave rslave = null;

		try {
			XMLDecoder in = new XMLDecoder(new FileInputStream(
					getSlaveFile(slavename)));

			rslave = (RemoteSlave) in.readObject();
			in.close();
			rslave.init(getGlobalContext());

			//throws RuntimeException
			_rslaves.add(rslave);

			return rslave;
		} catch (FileNotFoundException e) {
			throw new ObjectNotFoundException(e);
		} catch (Exception e) {
			throw new FatalException("Error loading "+slavename, e);
		}
	}

	protected File getSlaveFile(String slavename) {
		return new File(slavePath + slavename + ".xml");
	}

	protected void addShutdownHook() {
		//add shutdown hook last
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Running shutdown hook");
				saveFilelist();

				try {
					getGlobalContext().getConnectionManager()
							.getGlobalContext().getUserManager().saveAll();
				} catch (UserFileException e) {
					logger.warn("", e);
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
			_rslaves.remove(rslave);
			getGlobalContext().getRoot().unmergeDir(rslave);
		} catch (ObjectNotFoundException e) {
			throw new IllegalArgumentException("Slave not found");
		}
	}

	public HashSet<RemoteSlave> findSlavesBySpace(int numOfSlaves, Set exemptSlaves,
			boolean ascending) {
		Collection<RemoteSlave> slaveList = getSlaves();
		HashMap<Long, RemoteSlave> map = new HashMap<Long, RemoteSlave>();

		for (Iterator<RemoteSlave> iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = iter.next();

			if (exemptSlaves.contains(rslave)) {
				continue;
			}

			Long size;

			try {
				size = new Long(rslave.getSlaveStatusAvailable()
						.getDiskSpaceAvailable());
			} catch (SlaveUnavailableException e) {
				continue;
			}

			map.put(size, rslave);
		}

		ArrayList sorted = new ArrayList(map.keySet());

		if (ascending) {
			Collections.sort(sorted);
		} else {
			Collections.sort(sorted, Collections.reverseOrder());
		}

		HashSet<RemoteSlave> returnMe = new HashSet<RemoteSlave>();

		for (ListIterator iter = sorted.listIterator(); iter.hasNext();) {
			if (iter.nextIndex() == numOfSlaves) {
				break;
			}

			Long key = (Long) iter.next();
			RemoteSlave rslave = (RemoteSlave) map.get(key);
			returnMe.add(rslave);
		}

		return returnMe;
	}

	public RemoteSlave findSmallestFreeSlave() {
		Collection slaveList = getGlobalContext().getConnectionManager()
				.getGlobalContext().getSlaveManager().getSlaves();
		long smallSize = Integer.MAX_VALUE;
		RemoteSlave smallSlave = null;

		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
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

		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();

			try {
				allStatus = allStatus.append(rslave.getSlaveStatusAvailable());
			} catch (SlaveUnavailableException e) {
				//slave is offline, continue
			}
		}

		return allStatus;
	}

	public HashMap getAllStatusArray() {
		//SlaveStatus[] ret = new SlaveStatus[getSlaves().size()];
		HashMap ret = new HashMap(getSlaves().size());

		for (Iterator<RemoteSlave> iter = getSlaves().iterator(); iter
				.hasNext();) {
			RemoteSlave rslave = iter.next();

			try {
				ret.put(rslave.getName(), rslave.getSlaveStatus());
			} catch (SlaveUnavailableException e) {
				ret.put(rslave.getName(), (Object) null);
			}
		}

		return ret;
	}

	//	private Random rand = new Random();
	//	public RemoteSlave getASlave() {
	//		ArrayList retSlaves = new ArrayList();
	//		for (Iterator iter = this.rslaves.iterator(); iter.hasNext();) {
	//			RemoteSlave rslave = (RemoteSlave) iter.next();
	//			if (!rslave.isAvailable())
	//				continue;
	//			retSlaves.add(rslave);
	//		}
	//
	//		int num = rand.nextInt(retSlaves.size());
	//		logger.fine(
	//			"Slave "
	//				+ num
	//				+ " selected out of "
	//				+ retSlaves.size()
	//				+ " available slaves");
	//		return (RemoteSlave) retSlaves.get(num);
	//	}
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
		if (_gctx == null) {
			throw new NullPointerException();
		}

		return _gctx;
	}

	public RemoteSlave getRemoteSlave(String s) throws ObjectNotFoundException {
		for (Iterator<RemoteSlave> iter = getSlaves().iterator(); iter
				.hasNext();) {
			RemoteSlave rslave = iter.next();

			if (rslave.getName().equals(s)) {
				return rslave;
			}
		}

		return getSlaveByNameUnchecked(s);
	}

	public List<RemoteSlave> getSlaves() {
		if (_rslaves == null) {
			throw new NullPointerException();
		}

		return Collections.unmodifiableList(_rslaves);
	}

	/**
	 * Returns true if one or more slaves are online, false otherwise.
	 * 
	 * @return true if one or more slaves are online, false otherwise.
	 */
	public boolean hasAvailableSlaves() {
		for (Iterator<RemoteSlave> iter = _rslaves.iterator(); iter.hasNext();) {
			if (iter.next().isAvailable()) {
				return true;
			}
		}
		return false;
	}

	public void saveFilelist() {
		try {
			SafeFileWriter out = new SafeFileWriter("files.mlst");

			try {
				MLSTSerialize.serialize(getGlobalContext()
						.getConnectionManager().getGlobalContext().getRoot(),
						out);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			logger.warn("Error saving files.mlst", e);
		}
	}

	/** ping's all slaves, returns number of slaves removed */
	public int verifySlaves() {
		int removed = 0;

		synchronized (_rslaves) {
			for (Iterator i = _rslaves.iterator(); i.hasNext();) {
				RemoteSlave slave = (RemoteSlave) i.next();

				if (!slave.isAvailablePing()) {
					removed++;
				}
			}
		}

		return removed;
	}

	public void run() {
		try {
			_serverSocket = new ServerSocket(_port);
			//_serverSocket.setReuseAddress(true);
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
				socket.setSoTimeout(0);
				logger.debug("Slave connected from "
						+ socket.getRemoteSocketAddress());

				in = new ObjectInputStream(socket.getInputStream());
				out = new ObjectOutputStream(socket.getOutputStream());

				String slavename = RemoteSlave.getSlaveNameFromObjectInput(in);

				try {
					rslave = getRemoteSlave(slavename);
				} catch (ObjectNotFoundException e) {
					out
							.writeObject(new AsyncCommandArgument(
									"",
									"error",
									slavename
											+ " does not exist, use \"site addslave\""));
					logger.info("Slave " + slavename
							+ " does not exist, use \"site addslave\"");
					continue;
				}

				if (rslave.isOnlinePing()) {
					out.writeObject(new AsyncCommandArgument("", "error",
							"Already online"));
					out.flush();
					socket.close();
					throw new IOException("Already online");
				}
			} catch (Exception e) {
				try {
					socket.close();
				} catch (IOException e1) {
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
				logger.error("FATAL: Throwable in SalveManager loop");
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
			_remergeThread = new RemergeThread(getGlobalContext());
			_remergeThread.start();
		}
	}

	/**
	 * Cancels all transfers in directory
	 */
	public void cancelTransfersInDirectory(LinkedRemoteFileInterface dir) {
		if (!dir.isDirectory()) {
			throw new IllegalArgumentException(dir + " is not a directory");
		}
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
}

class RemergeThread extends Thread {
	private static final Logger logger = Logger.getLogger(RemergeThread.class);

	private GlobalContext _gctx;

	public RemergeThread(GlobalContext gctx) {
		super("RemergeThread");
		_gctx = gctx;
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
				continue;
			}

			LinkedRemoteFileInterface lrf;

			try {
				lrf = getGlobalContext().getRoot().lookupFile(
						msg.getDirectory());
			} catch (FileNotFoundException e1) {
				lrf = getGlobalContext().getRoot().createDirectories(
						msg.getDirectory());
			}

			try {
				lrf.remerge(msg.getFiles(), msg.getRslave());
			} catch (IOException e2) {
				logger.error("IOException during remerge", e2);
				msg.getRslave().setOffline("IOException during remerge");
			}
		}
	}

	private GlobalContext getGlobalContext() {
		return _gctx;
	}
}
