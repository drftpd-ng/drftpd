package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;

import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: PassiveConnection.java,v 1.9 2004/02/03 20:12:50 mog Exp $
 */
public class PassiveConnection extends Connection {
	private PortRange _portRange;
	private static final Logger logger = Logger.getLogger(PassiveConnection.class);
	ServerSocket server;

	public PassiveConnection(SSLContext ctx, PortRange portRange, InetSocketAddress bindAddr)
		throws IOException {
		if (ctx != null) {
			SSLServerSocket sslserver;
			sslserver =
				(SSLServerSocket) ctx
					.getServerSocketFactory()
					.createServerSocket();
			server = sslserver;
		} else {
			server = ServerSocketFactory.getDefault().createServerSocket();
		}
		if(bindAddr.getPort() == 0) {
			_portRange = portRange;
			server.bind(new InetSocketAddress(bindAddr.getAddress(), portRange.getPort()));
		} else {
			server.bind(bindAddr, 1);
		}
		server.setSoTimeout(TIMEOUT);
	}

	public Socket connect() throws IOException {
		Socket sock = server.accept();
		server.close();
		_portRange.releasePort(server.getLocalPort());

		setSockOpts(sock);
		if (sock instanceof SSLSocket) {
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
