package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: PassiveConnection.java,v 1.6 2003/12/07 22:31:46 mog Exp $
 */
public class PassiveConnection extends Connection {
	private static Logger logger = Logger.getLogger(PassiveConnection.class);
	ServerSocket server;

	public PassiveConnection(SSLContext ctx, InetSocketAddress bindAddr)
		throws IOException {
		if (ctx != null) {
			SSLServerSocket sslserver;
			sslserver =
				(SSLServerSocket) ctx
					.getServerSocketFactory()
					.createServerSocket();
			sslserver.bind(bindAddr, 1);
		} else {
			server = ServerSocketFactory.getDefault().createServerSocket();
			server.bind(bindAddr, 1);
		}
	}

	public Socket connect() throws IOException {
		Socket sock = server.accept();
		setSockOpts(sock);
		server.close();
		if(sock instanceof SSLSocket) {
			SSLSocket sslsock = (SSLSocket) sock;
			sslsock.setUseClientMode(false);
			sslsock.startHandshake();
		}
		return sock;
	}

	public int getLocalPort() {
		return server.getLocalPort();
	}

}
