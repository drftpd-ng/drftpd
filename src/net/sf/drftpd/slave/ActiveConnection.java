package net.sf.drftpd.slave;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

/**
 * @author mog
 * @version $Id: ActiveConnection.java,v 1.4 2003/12/07 22:31:46 mog Exp $
 */
public class ActiveConnection extends Connection {
	private SSLContext _ctx;
	private boolean _encrypted;
	private SocketAddress _addr;
	public ActiveConnection(SSLContext ctx, InetSocketAddress addr) {
		_addr = addr;
		_ctx = ctx;
	}

	public Socket connect() throws IOException {
		Socket sock;
		if (_ctx != null) {
			SSLSocket sslsock;
			sslsock = (SSLSocket) _ctx.getSocketFactory().createSocket();
			sslsock.connect(_addr, TIMEOUT);
			sslsock.setUseClientMode(false);
			sslsock.startHandshake();
			sock = sslsock;
		} else {
			sock = SocketFactory.getDefault().createSocket();
			sock.connect(_addr, TIMEOUT);
		}
		setSockOpts(sock);
		return sock;
	}

}
