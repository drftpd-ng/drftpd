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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.*;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Logger;
import org.jdom.Element;

/**
 * @author mog
 * @version $Id: RemoteSlave.java,v 1.48 2004/07/02 19:58:52 mog Exp $
 */
public class RemoteSlave implements Comparable {
	/**
	 * Used by JUnit tests
	 */
	public RemoteSlave(String name, Collection list) {
		Properties p = new Properties();
		p.setProperty("name", name);
		_name = name;
		updateConfig(p);
	}
	
	private void addQueueRename(String fileName,String destName) {
		if (isAvailable()) throw new IllegalStateException("Slave is available, you cannot queue an operation");
		if (_fileQueue.containsKey(fileName))
			throw new IllegalArgumentException(fileName + " is already in the queue for processing");
		_fileQueue.put(fileName,destName);
	}
	
	private void addQueueDelete(String fileName) {
		addQueueRename(fileName,null);
	}
	
	/**
	 * Rename files.
	 */
	public void rename(String from, String toDirPath, String toName)
		throws IOException {
		try {
			getSlave().rename(from,toDirPath,toName);
		} catch (RemoteException e) {
			handleRemoteException(e);
			addQueueRename(from,toDirPath+"/"+toName);
		} catch (IOException e) {
			setOffline(e.getMessage());
			addQueueRename(from,toDirPath+"/"+toName);
			throw e;
		} catch (SlaveUnavailableException e) {
			addQueueRename(from,toDirPath+"/"+toName);
		}
	}
		
	/**
	 * Delete files.
	 */
	public void delete(String path) throws IOException {
		try {
			getSlave().delete(path);
		} catch (RemoteException e) {
			handleRemoteException(e);
			addQueueDelete(path);
		} catch (FileNotFoundException e) {
			return;
		} catch (IOException e) {
			setOffline(e.getMessage());
			addQueueDelete(path);
			throw e;
		} catch (SlaveUnavailableException e) {
			addQueueDelete(path);
		}
	}
	
	private HashMap _fileQueue;

	private int _maxPath;

	private static final Logger logger = Logger.getLogger(RemoteSlave.class);

	private InetAddress _inetAddress;
	private long _lastDownloadSending = 0;
	private long _lastPing;
	private long _lastUploadReceiving = 0;
	private SlaveManagerImpl _manager;
	private Collection _masks;
	private String _name;
	private Slave _slave;
	private SlaveStatus _status;
	private Properties _config;

	private boolean _available;

	public void processQueue() throws RemoteException {
		for (Iterator iter = _fileQueue.keySet().iterator(); iter.hasNext();) {
			String sourceFile = (String) iter.next();
			String destFile = (String) _fileQueue.get(sourceFile);
			if (destFile == null) {
				try {
					_slave.delete(sourceFile);
				} catch (IOException e) {
					// just remove and continue, we can't do much
					// if the OS has the file locked
				}
			}
			else {
				String fileName = destFile.substring(destFile.lastIndexOf("/")+1);
				String destDir = destFile.substring(0,destFile.lastIndexOf("/"));
				try {
					_slave.rename(sourceFile,destDir,fileName);
				} catch (IOException e) {
					// just remove and continue, we can't do much
					// if the OS has the file locked
				}
			}
			iter.remove();
		}
	}

	public RemoteSlave(Properties config) {
		String name = FtpConfig.getProperty(config, "name");
		_name = name;
		_available = false;
		_slave = null;
		updateConfig(config);
	}

	public void updateConfig(Properties config) {
		String name = FtpConfig.getProperty(config, "name");
		if (name.equalsIgnoreCase("all")) {
			throw new IllegalArgumentException(
				name
					+ " is a reserved keyword, it can't be used as a slave name");
		}
		if (!name.equalsIgnoreCase(_name)) {
			throw new IllegalArgumentException(
				name
					+ " is not the same name as currently set for this RemoteSlave");
		}
		List masks = new ArrayList();
		String[] maskitems = config.getProperty("masks", "").split(",");
		for (int i = 0; i < maskitems.length; i++) {
			masks.add(maskitems[i]);
		}
		_masks = masks;
		_config = config;
		_fileQueue = new HashMap();
	}

	public Element getConfigXML() {
		Element root = new org.jdom.Element("slave");
		Enumeration e = _config.keys();
		while (e.hasMoreElements()) {
			String key = (String) e.nextElement();
			Element tmp = new Element(key);
			tmp.setText((String) _config.get(key));
			root.addContent(tmp);
		}
		Iterator i = _masks.iterator();
		while (i.hasNext()) {
			String mask = (String) i.next();
			Element tmp = new Element("mask");
			tmp.setText(mask);
			root.addContent(tmp);
		}
		return root;
	}

	public int compareTo(Object o) {
		if (!(o instanceof RemoteSlave))
			throw new IllegalArgumentException();
		return getName().compareTo(((RemoteSlave) o).getName());
	}

	public boolean equals(Object obj) {
		return ((RemoteSlave) obj).getName().equals(getName());
	}

	public InetAddress getInetAddress() {
		return _inetAddress;
	}

	public long getLastDownloadSending() {
		return _lastDownloadSending;
	}

	public long getLastTransfer() {
		return Math.max(getLastDownloadSending(), getLastUploadReceiving());
	}

	public long getLastUploadReceiving() {
		return _lastUploadReceiving;
	}

	public SlaveManagerImpl getManager() {
		return _manager;
	}

	public Collection getMasks() {
		return _masks;
	}

	public Hashtable getConfig() {
		return _config;
	}

	/**
	 * Returns the name.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * Throws NoAvailableSlaveException only if slave is offline
	 */
	public Slave getSlave() throws SlaveUnavailableException {
		if (!isAvailable())
			throw new SlaveUnavailableException("slave is offline");
		return _slave;
	}

	/**
	 * Returns the RemoteSlave's stored SlaveStatus
	 */
	public SlaveStatus getStatus() throws SlaveUnavailableException {
		if (_status == null) {
			throw new SlaveUnavailableException();
		}
		return _status;
	}

	public synchronized void updateStatus() throws SlaveUnavailableException {
		try {
			_status = getSlave().getSlaveStatus();
		} catch (RemoteException e) {
			handleRemoteException(e);
			_status = null;
		}
	}

	/**
	 * @param ex RemoteException
	 * @return true If exception was fatal and the slave was removed
	 */
	public void handleRemoteException(RemoteException ex) {
		//		if (!isFatalRemoteException(ex)) {
		//			logger.log(
		//				Level.WARN,
		//				"Caught non-fatal exception from "
		//					+ getName()
		//					+ ", not removing",
		//				ex);
		//			return false;
		//		}
		logger.warn("Exception from " + getName() + ", removing", ex);
		setOffline(ex.getCause().getMessage());
	}

	public int hashCode() {
		return getName().hashCode();
	}
	
	public void setAvailable(boolean available) {
		_available = available;
	}

	public boolean isAvailable() {
		return _available;
	}

	public boolean isAvailablePing() {
		try {
			getSlave().ping();
		} catch (RemoteException e) {
			handleRemoteException(e);
			return false;
		} catch (SlaveUnavailableException e) {
			return false;
		}
		return isAvailable();
	}

	/**
	 * @deprecated use isAvailablePing() instead
	 * @throws RemoteException
	 * @throws SlaveUnavailableException
	 */
	public void ping() throws RemoteException, SlaveUnavailableException {
		if (_slave == null)
			throw new SlaveUnavailableException(getName() + " is offline");
		if (System.currentTimeMillis() > _lastPing + 1000) {
			getSlave().ping();
		}
	}
	public void setLastDownloadSending(long lastDownloadSending) {
		_lastDownloadSending = lastDownloadSending;
	}
	public void setLastUploadReceiving(long lastUploadReceiving) {
		_lastUploadReceiving = lastUploadReceiving;
	}

	public void setManager(SlaveManagerImpl manager) {
		if (_manager != null)
			throw new IllegalStateException("Can't overwrite manager");
		_manager = manager;
	}

	public void setMasks(Collection masks) {
		_masks = masks;
	}

	public void setOffline(String reason) {
		if (_manager == null) {
			throw new RuntimeException("_manager == null");
		}
		if (_slave != null) {
			_manager.getConnectionManager().dispatchFtpEvent(
				new SlaveEvent("DELSLAVE", reason, this));
		}
		_slave = null;
		_status = null;
		_inetAddress = null;
		_maxPath = 0;
		setAvailable(false);
	}

	public void setSlave(
		Slave slave,
		InetAddress inetAddress,
		SlaveStatus status,
		int maxPath) throws RemoteException {
		if (slave == null)
			throw new IllegalArgumentException();
		_slave = slave;
		_inetAddress = inetAddress;
		_status = status;
		_maxPath = maxPath;
		processQueue();
	}

	public String toString() {
		try {
			return getName() + "[slave=" + getSlave().toString() + "]";
		} catch (SlaveUnavailableException e) {
			return getName() + "[slave=offline]";
		}
	}

	public static Hashtable rslavesToHashtable(Collection rslaves) {
		Hashtable map = new Hashtable(rslaves.size());
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			map.put(rslave.getName(), rslave);
		}
		return map;
	}

	public long getLastTransferForDirection(char dir) {
		if (dir == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			return getLastUploadReceiving();
		} else if (dir == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			return getLastDownloadSending();
		} else if (dir == Transfer.TRANSFER_THROUGHPUT) {
			return getLastTransfer();
		} else {
			throw new IllegalArgumentException();
		}
	}

	/**
	 * Returns an updated slaveRoot
	 */
	public LinkedRemoteFile getSlaveRoot()
		throws IOException, SlaveUnavailableException {
		if (_slave == null)
			throw new SlaveUnavailableException("Cannot getSlaveRoot() with Offline Slave");
		return _slave.getSlaveRoot();
	}

	public void setLastDirection(char direction, long l) {
		switch (direction) {
			case Transfer.TRANSFER_RECEIVING_UPLOAD :
				setLastUploadReceiving(l);
				return;
			case Transfer.TRANSFER_SENDING_DOWNLOAD :
				setLastDownloadSending(l);
				return;
			default :
				throw new IllegalArgumentException();
		}
	}
}
