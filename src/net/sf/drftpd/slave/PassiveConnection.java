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
 * @version $Id: PassiveConnection.java,v 1.10 2004/02/03 20:28:46 mog Exp $
 */
public class PassiveConnection extends Connection {
	private PortRange _portRange;
	private static final Logger logger = Logger.getLogger(PassiveConnection.class);
	private ServerSocket _server;

	public PassiveConnection(SSLContext ctx, PortRange portRange, InetSocketAddress bindAddr)
		throws IOException {
		if (ctx != null) {
			SSLServerSocket sslserver;
			sslserver =
				(SSLServerSocket) ctx
					.getServerSocketFactory()
					.createServerSocket();
			_server = sslserver;
		} else {
			_server = ServerSocketFactory.getDefault().createServerSocket();
		}
		if(bindAddr.getPort() == 0) {
			_portRange = portRange;
			_server.bind(new InetSocketAddress(bindAddr.getAddress(), portRange.getPort()));
		} else {
			_server.bind(bindAddr, 1);
		}
		_server.setSoTimeout(TIMEOUT);
	}

	public Socket connect() throws IOException {
		Socket sock = _server.accept();
		_server.close();
		_portRange.releasePort(_server.getLocalPort());
		_server = null;
		_portRange = null;

		setSockOpts(sock);
		if (sock instanceof SSLSocket) {
			SSLSocket sslsock = (SSLSocket) sock;
			sslsock.setUseClientMode(false);
			sslsock.startHandshake();
		}
		return sock;
	}

	public int getLocalPort() {
		return _server.getLocalPort();
	}

	public void abort() {
		try {
			_server.close();
		} catch (IOException e) {
			logger.warn("failed to close() server socket", e);
		}
	}

}
