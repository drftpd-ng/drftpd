package net.sf.drftpd.master;

import java.net.InetAddress;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: RemoteSlave.java,v 1.22 2004/01/13 20:30:53 mog Exp $
 */
public class RemoteSlave implements Comparable {

	private static final Logger logger =
		Logger.getLogger(RemoteSlave.class.getName());

	public static boolean isFatalRemoteException(RemoteException ex) {
		return (
			ex instanceof ConnectException || ex instanceof ConnectIOException);
	}

	private InetAddress _inetAddress;
	private long _lastDownloadSending = 0;
	private long _lastPing;
	private long _lastUploadReceiving = 0;
	private SlaveManagerImpl _manager;
	private Collection _masks;
	private String _name;
	private Slave _slave;
	//private SlaveStatus _status;
	//private long _statusTime;

	public RemoteSlave(String name) {
		_name = name;
	}
	public RemoteSlave(String name, Collection masks) {
		this(name);
		_masks = masks;
	}

	public RemoteSlave(
		String name,
		Collection masks,
		SlaveManagerImpl manager) {
		this(name, masks);
		_manager = manager;
	}

	public int compareTo(Object o) {
		if (!(o instanceof RemoteSlave))
			throw new IllegalArgumentException();
		return getName().compareTo(((RemoteSlave) o).getName());
	}

	public boolean equals(Object obj) {
		if (obj instanceof RemoteSlave) {
			RemoteSlave rslave = (RemoteSlave) obj;
			if (rslave.getName().equals(this.getName())) {
				return true;
			}
		}
		return false;
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

	/**
	 * Returns the name.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * Throws NoAvailableSlaveException only if slave is offline
	 */
	public Slave getSlave() throws NoAvailableSlaveException {
		if (_slave == null)
			throw new NoAvailableSlaveException("slave is offline");
		return _slave;
	}

	/**
	 * Get's slave status, caches the status for 10 seconds.
	 */
	public SlaveStatus getStatus()
		throws RemoteException, NoAvailableSlaveException {
		return getSlave().getSlaveStatus();

		//		if (statusTime < System.currentTimeMillis() - 10000) {
		//			status = getSlave().getSlaveStatus();
		//			statusTime = System.currentTimeMillis();
		//		}
		//		return status;
	}

	/**
	 * @param ex RemoteException
	 * @return true If exception was fatal and the slave was removed 
	 */
	public boolean handleRemoteException(RemoteException ex) {
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
		return true;
	}

	public int hashCode() {
		return getName().hashCode();
	}

	public boolean isAvailable() {
		return _slave != null;
	}

	public boolean isAvailablePing() {
		try {
			getSlave().ping();
		} catch (RemoteException e) {
			handleRemoteException(e);
			return false;
		} catch (NoAvailableSlaveException e) {
			return false;
		}
		return isAvailable();
	}

	public void ping() throws RemoteException, NoAvailableSlaveException {
		if (_slave == null)
			throw new NoAvailableSlaveException(getName() + " is offline");
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
		assert _manager != null;
		if (_slave != null) {
			_manager.getConnectionManager().dispatchFtpEvent(
				new SlaveEvent("DELSLAVE", reason, this));
		}
		_slave = null;
		_inetAddress = null;
	}

	public void setSlave(Slave slave, InetAddress inetAddress) {
		if (slave == null)
			throw new IllegalArgumentException();
		_slave = slave;
		_inetAddress = inetAddress;
	}

	public String toString() {
		try {
			return getName() + "[slave=" + getSlave().toString() + "]";
		} catch (NoAvailableSlaveException e) {
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

}
