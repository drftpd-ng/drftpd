package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.server.RMISocketFactory;

import org.apache.log4j.Logger;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class PassiveConnection extends Connection {
	private static Logger logger = Logger.getLogger(PassiveConnection.class);
	ServerSocket server;
	
	/**
	 * @param bindAddr
	 * @throws IOException
	 */
	public PassiveConnection(InetAddress bindAddr) throws IOException {
		server = RMISocketFactory.getDefaultSocketFactory().createServerSocket(0);
		//server = new ServerSocket(0, 1, bindAddr);
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
