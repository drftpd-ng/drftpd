package net.sf.drftpd.slave;

import java.io.Serializable;
import java.rmi.RemoteException;

import net.sf.drftpd.master.SlaveManagerImpl;

public class RemoteSlave implements Serializable {
	protected long statusTime;
	protected SlaveStatus status;
	protected SlaveManagerImpl manager;
	protected String name="mog";
	protected String prefix="";
	public void setManager(SlaveManagerImpl manager) {
		this.manager = manager;
	}
	
	public SlaveManagerImpl getManager() {
		return manager;
	}
	
	public SlaveStatus getStatus() throws RemoteException {
		if(statusTime < System.currentTimeMillis()-10000) {
			status=getSlave().getSlaveStatus();
			statusTime = System.currentTimeMillis();
		}
		return status;
	}

	public RemoteSlave(Slave slave, String prefix) {
		this.slave = slave;
		this.prefix = prefix;
	}

	protected Slave slave;
	public Slave getSlave() {
		return slave;
	}

	public String toString() {
		return slave.toString();
	}
	
	/**
	 * Returns the name.
	 */
	public String getName() {
		return name;
	}

}
