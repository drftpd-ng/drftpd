package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

/**
 * @author mog
 * @version $Id: ActiveConnection.java,v 1.3 2003/11/17 20:13:11 mog Exp $
 */
public class ActiveConnection extends Connection {
	private SocketAddress _addr;
	private static final int TIMEOUT = 10000;
	
	public ActiveConnection(InetAddress addr, int port) {
		_addr = new InetSocketAddress(addr, port);
	}

	public Socket connect() throws IOException {
		Socket sock = new Socket();
		sock.connect(_addr, TIMEOUT);
		setSockOpts(sock);
		return sock;
	}

}
