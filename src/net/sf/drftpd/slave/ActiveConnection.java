package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: ActiveConnection.java,v 1.6 2004/02/03 20:28:46 mog Exp $
 */
public class ActiveConnection extends Connection {
	private static final Logger logger =
		Logger.getLogger(ActiveConnection.class);
	private SSLContext _ctx;
	private SocketAddress _addr;
	private Socket _sock;
	public ActiveConnection(SSLContext ctx, InetSocketAddress addr) {
		_addr = addr;
		_ctx = ctx;
	}

	public Socket connect() throws IOException {
		if (_ctx != null) {
			SSLSocket sslsock;
			sslsock = (SSLSocket) _ctx.getSocketFactory().createSocket();
			sslsock.connect(_addr, TIMEOUT);
			sslsock.setUseClientMode(false);
			sslsock.startHandshake();
			_sock = sslsock;
		} else {
			_sock = SocketFactory.getDefault().createSocket();
			_sock.connect(_addr, TIMEOUT);
		}
		setSockOpts(_sock);
		Socket sock = _sock;
		_sock = null;
		return sock;
	}

	public void abort() {
		try {
			if (_sock != null)
				_sock.close();
		} catch (IOException e) {
			logger.warn("abort() failed to close() socket", e);
		}
	}

}
