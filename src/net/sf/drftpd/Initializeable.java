package net.sf.drftpd;

import net.sf.drftpd.master.ConnectionManager;

public interface Initializeable {
	public void init(ConnectionManager connectionManager);
}
