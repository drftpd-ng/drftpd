package net.sf.drftpd.slave;

import java.io.IOException;
import java.io.Serializable;
import java.net.Socket;

/**
 * @author mog
 * @version $Id: Connection.java,v 1.7 2003/12/07 22:31:46 mog Exp $
 */
public abstract class Connection implements Serializable {
	public static final int TIMEOUT = 10000;

	public abstract Socket connect() throws IOException;
	protected void setSockOpts(Socket sock) throws IOException {
		/*
		 * IPTOS_LOWCOST (0x02)
		 * IPTOS_RELIABILITY (0x04)
		 * IPTOS_THROUGHPUT (0x08)
		 * IPTOS_LOWDELAY (0x10)
		 */
		sock.setTrafficClass(0x08);
		sock.setSoTimeout(TIMEOUT); // 30 second timeout
	}
}
