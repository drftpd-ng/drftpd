package net.sf.drftpd;

import net.sf.drftpd.master.ConnectionManager;

/**
 * @version $Id: Initializeable.java,v 1.2 2003/12/23 13:38:18 mog Exp $
 */
public interface Initializeable {
	public void init(ConnectionManager connectionManager);
}
