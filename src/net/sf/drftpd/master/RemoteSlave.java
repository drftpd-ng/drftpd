package net.sf.drftpd.master;

import java.io.Serializable;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.*;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;

/**
 * Class would fit both in net.sf.drftpd.slave and net.sf.drftpd.master.
 * However, as it is instantiated from the slave (or master with local slave),
 * and mainly because it is a helper class for Slave, it is located in net.sf.drftpd.slave.
 * 
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class RemoteSlave implements Serializable {

	private static Logger logger =
		Logger.getLogger(RemoteSlave.class.getName());
	static {
		logger.setLevel(Level.FINE);
	}

	private SlaveManagerImpl manager;
	private String name;
	private Slave slave;
	private SlaveStatus status;
	private long statusTime;
	private Collection masks;
	
	public RemoteSlave(String name) {
		this.name = name;
	}

	public RemoteSlave(String name, Collection masks) {
		this.name = name;
		this.masks = masks;
	}
	
	/**
	 * @deprecated
	 */
	public RemoteSlave(String name, Slave slave) {
		if (name == null)
			throw new IllegalArgumentException("name cannot be null (did you set slave.name?)");
		this.slave = slave;
		this.name = name;
	}
	
	public SlaveManagerImpl getManager() {
		return manager;
	}

	/**
	 * Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * 
	 * Throws NoAvailableSlaveException only if slave is offline
	 * @return
	 * @throws NoAvailableSlaveException
	 */
	public Slave getSlave() throws NoAvailableSlaveException {
		if (slave == null)
			throw new NoAvailableSlaveException("slave is offline");
		return slave;
	}

	/**
	 * Get's slave status, caches the status for 10 seconds.
	 * @return
	 * @throws RemoteException
	 * @throws NoAvailableSlaveException
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
		logger.log(
			Level.WARNING,
			"Caught exception when trying to communicate with " + this, ex);
		if (!isFatalRemoteException(ex)) {
			logger.log(Level.WARNING, ". Non-fatal exception, not removing");
			return false;
		}
		System.out.println(". Fatal exception, removing");
		//manager.getRoot().unmerge(this);
		setSlave(null);
		return true;
	}

	public int hashCode() {
		return this.getName().hashCode();
	}

	public static boolean isFatalRemoteException(RemoteException ex) {
		return (ex instanceof ConnectException);
	}
	
	public void setManager(SlaveManagerImpl manager) {
		if(this.manager != null) throw new IllegalStateException("Can't overwrite manager");
		this.manager = manager;
	}

	public boolean isAvailable() {
		try {
			getSlave().ping();
		} catch (RemoteException e) {
			handleRemoteException(e);
		} catch (NoAvailableSlaveException e) {
		}
		return slave != null;
	}

	public String toString() {
		String str = this.getName();
		try {
			//System.out.println("getRef().remoteToString(): "+
			str = str + "[slave=" + this.getSlave().toString() + "]";
		} catch (NoAvailableSlaveException e) {
			str = str + "[slave=offline]";
		}
		return str.toString();
	}

	/**
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		if (obj instanceof RemoteSlave) {
			RemoteSlave rslave = (RemoteSlave) obj;
			if (rslave.getName().equals(this.getName())) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return
	 */
	public Collection getMasks() {
		return masks;
	}

	/**
	 * @param slave
	 */
	public void setSlave(Slave slave) {
		this.slave = slave;
	}

	/**
	 * @param collection
	 */
	public void setMasks(Collection collection) {
		masks = collection;
	}

}
