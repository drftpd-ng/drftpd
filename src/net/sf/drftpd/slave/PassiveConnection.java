package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class PassiveConnection extends Connection {
	ServerSocket server;
	public PassiveConnection() throws IOException {
		server = new ServerSocket(0, 1);
	}

	public int getLocalPort() {
		return server.getLocalPort();
	}
	/**
	 * @see net.sf.drftpd.slave.Connection#connect()
	 */
	public Socket connect() throws IOException {
		Socket sock = server.accept();
		setSockOpts(sock);
		server.close();
		return sock;
	}

}
