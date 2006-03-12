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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;

import org.apache.log4j.Logger;
import org.drftpd.slave.Connection;


/**
 * @author mog
 * @version $Id$
 */
public class ActiveConnection extends Connection {
    private static final Logger logger = Logger.getLogger(ActiveConnection.class);
    private SSLContext _ctx;
    private InetSocketAddress _addr;
    private Socket _sock;
    private boolean _useSSLClientHandshake;
    
    public ActiveConnection(SSLContext ctx, InetSocketAddress addr,
			boolean useSSLClientHandshake) {
        _addr = addr;
        _ctx = ctx;
        _useSSLClientHandshake = useSSLClientHandshake;
	}

    public Socket connect() throws IOException {
        logger.debug("Connecting to " + _addr.getHostName() + ":" +
            _addr.getPort());

        if (_ctx != null) {
            SSLSocket sslsock;
            sslsock = (SSLSocket) _ctx.getSocketFactory().createSocket();
            sslsock.connect(_addr, TIMEOUT);
            setSockOpts(sslsock);
            sslsock.setUseClientMode(_useSSLClientHandshake);
            sslsock.startHandshake();
            _sock = sslsock;
        } else {
            _sock = SocketFactory.getDefault().createSocket();
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
