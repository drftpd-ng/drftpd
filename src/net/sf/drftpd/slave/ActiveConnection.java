package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class ActiveConnection extends Connection {
	InetAddress addr;
	int port;

	public ActiveConnection(InetAddress addr, int port) {
		this.addr = addr;
		this.port = port;
	}
	/**
	 * @see net.sf.drftpd.slave.Connection#connect()
	 */
	public Socket connect() throws IOException {
		Socket sock = new Socket(addr, port);
		setSockOpts(sock);
		return sock;
	}

}
