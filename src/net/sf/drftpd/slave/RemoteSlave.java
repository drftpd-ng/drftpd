package net.sf.drftpd.slave;

import java.io.Serializable;
import java.rmi.RemoteException;

import net.sf.drftpd.master.SlaveManagerImpl;

public class RemoteSlave implements Serializable {
	protected SlaveManagerImpl manager;
	protected String name="mog";
	protected String prefix="";

	protected Slave slave;
	protected SlaveStatus status;
	protected long statusTime;

	public RemoteSlave(Slave slave, String prefix) {
		this.slave = slave;
		this.prefix = prefix;
	}
	
	public RemoteSlave(Slave slave) {
		this(slave, null);
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
	public Slave getSlave() {
		return slave;
	}
	
	public SlaveStatus getStatus() throws RemoteException {
		if(statusTime < System.currentTimeMillis()-10000) {
			status=getSlave().getSlaveStatus();
			statusTime = System.currentTimeMillis();
		}
		return status;
	}
	public void setManager(SlaveManagerImpl manager) {
		this.manager = manager;
	}

	public String toString() {
		return slave.toString();
	}

}
