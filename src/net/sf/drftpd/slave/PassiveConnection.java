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
 * @version $Id: PassiveConnection.java,v 1.11 2004/02/10 00:03:31 mog Exp $
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
