package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class PassiveConnection extends Connection {
	ServerSocket server;
	
	public PassiveConnection(InetAddress bindAddr) throws IOException {
		server = new ServerSocket(0, 1, bindAddr);
	}
	
	public Socket connect() throws IOException {
		Socket sock = server.accept();
		setSockOpts(sock);
		server.close();
		return sock;
	}

	public int getLocalPort() {
		return server.getLocalPort();
	}

}
