package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.Socket;

/**
 * @author mog
 * @version $Id: Connection.java,v 1.10 2004/02/03 20:28:46 mog Exp $
 */
public abstract class Connection {
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
		sock.setSoTimeout(TIMEOUT);
	}
	public abstract void abort();
}
