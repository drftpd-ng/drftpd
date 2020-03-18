package org.drftpd.slave.slave.diskselection;


import org.drftpd.slave.slave.Root;
import org.drftpd.slave.slave.Slave;

public abstract class DiskSelectionInterface {
	private Slave _slave;
	
	public DiskSelectionInterface(Slave slave) {
		_slave = slave;
	}
	
	public Slave getSlaveObject() {
		return _slave;
	}
	
	public abstract Root getBestRoot(String dir);
}
