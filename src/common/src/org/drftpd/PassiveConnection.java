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
import org.drftpd.util.PortRange;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;

/**
 * @author mog
 * @version $Id$
 */
public class PassiveConnection extends Connection {
	private static final Logger logger = LogManager.getLogger(PassiveConnection.class);

	private ServerSocket _serverSocket;

	// Default is to initiate the handshake
	private boolean _useSSLClientMode = false;

	/**
	 * @param ctx
	 * @param portRange
	 * @throws IOException
	 *             Creates a PassiveConnection - If ctx==null, the Connection
	 *             will not use SSL
	 */
	public PassiveConnection(SSLContext ctx, PortRange portRange,
			boolean useSSLClientMode, String bindIP) throws IOException {
		_useSSLClientMode = useSSLClientMode;
		if (ctx != null) {
			_serverSocket = portRange.getPort(ctx.getServerSocketFactory(),bindIP);
		} else {
			_serverSocket = portRange.getPort(ServerSocketFactory.getDefault(),bindIP);
		}
		_serverSocket.setSoTimeout(TIMEOUT);
	}

	public Socket connect(String[] cipherSuites, String[] sslProtocols, int bufferSize) throws IOException {
		// bufferSize has already been set on the ServerSocket
		// just need to accept this param to comply with the Connection class
		
		if (_serverSocket == null) {
			// can happen if abort() is called before connect()
			throw new SocketException(
					"abort() was called before connect()");
		}
		
		Socket sock = null;
		try {
			sock = _serverSocket.accept();
		} finally {
			if (_serverSocket != null) {
				_serverSocket.close();
			}
			_serverSocket = null;
		}
		
		if (sock == null) {
			// can happen if abort() is called while serverSocket.accept() is
			// waiting
			throw new SocketException(
					"abort() was called while waiting for accept()");
		}

		setSockOpts(sock);

		if (sock instanceof SSLSocket) {
			SSLSocket sslsock = (SSLSocket) sock;
			if (cipherSuites != null && cipherSuites.length != 0) {
				sslsock.setEnabledCipherSuites(cipherSuites);
			}
			if (sslProtocols != null && sslProtocols.length != 0) {
				sslsock.setEnabledProtocols(sslProtocols);
			}
			sslsock.setUseClientMode(_useSSLClientMode);
			sslsock.startHandshake();
		}


		return sock;
	}

	public int getLocalPort() {
		if (_serverSocket == null) {
			throw new NullPointerException("_serverSocket == null");
		}

		return _serverSocket.getLocalPort();
	}

	public void abort() {
		try {
			if (_serverSocket != null) {
				_serverSocket.close();
			}
		} catch (IOException e) {
			logger.error("failed to close() server socket", e);
		}
		_serverSocket = null;
	}

	protected void finalize() throws Throwable {
		if (_serverSocket != null) {
            logger.debug("Closing extraneous ServerSocket - {}, accept() was never called on the ServerSocket", _serverSocket.getLocalPort());
			_serverSocket.close();
		}
	}

}
