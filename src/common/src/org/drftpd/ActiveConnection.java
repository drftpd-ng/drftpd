/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.slave.Connection;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * @author mog
 * @version $Id$
 */
public class ActiveConnection extends Connection {
	private static final Logger logger = LogManager.getLogger(ActiveConnection.class);

	private SSLContext _ctx;

	private InetSocketAddress _addr;

	private Socket _sock;

	private boolean _useSSLClientHandshake;
	
	private String _bindIP;

	public ActiveConnection(SSLContext ctx, InetSocketAddress addr,
			boolean useSSLClientHandshake, String bindIP) {
		_addr = addr;
		_ctx = ctx;
		_bindIP = bindIP;
		_useSSLClientHandshake = useSSLClientHandshake;
	}

	public Socket connect(String[] cipherSuites, String[] sslProtocols, int bufferSize) throws IOException {
        logger.debug("Connecting to {}:{}", _addr.getAddress().getHostAddress(), _addr.getPort());

		if (_ctx != null) {
			SSLSocket sslsock;
			sslsock = (SSLSocket) _ctx.getSocketFactory().createSocket();
			if (bufferSize > 0) {
				sslsock.setReceiveBufferSize(bufferSize);
			}
			
			if (_bindIP != null) {
				sslsock.bind(new InetSocketAddress(_bindIP,sslsock.getPort()));
			}
			
			sslsock.connect(_addr, TIMEOUT);
			setSockOpts(sslsock);
			if (cipherSuites != null && cipherSuites.length != 0) {
				sslsock.setEnabledCipherSuites(cipherSuites);
			}
			if (sslProtocols != null && sslProtocols.length != 0) {
				sslsock.setEnabledProtocols(sslProtocols);
			}
			sslsock.setUseClientMode(_useSSLClientHandshake);
			sslsock.startHandshake();
			_sock = sslsock;
		} else {
			_sock = SocketFactory.getDefault().createSocket();
			if (bufferSize > 0) {
				_sock.setReceiveBufferSize(bufferSize);
			}
			
			if (_bindIP != null) {
				_sock.bind(new InetSocketAddress(_bindIP,_sock.getPort()));
			}
			 
			_sock.connect(_addr, TIMEOUT);
			setSockOpts(_sock);
		}

		Socket sock = _sock;
		_sock = null;

		return sock;
	}

	public void abort() {
		try {
			if (_sock != null) {
				_sock.close();
			}
		} catch (IOException e) {
			logger.warn("abort() failed to close() socket", e);
		}
	}
}
