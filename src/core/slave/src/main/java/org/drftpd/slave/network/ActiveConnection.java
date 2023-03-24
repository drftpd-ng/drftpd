/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.slave.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.slave.Connection;

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Arrays;

/**
 * @author mog
 * @version $Id$
 */
public class ActiveConnection extends Connection {
    private static final Logger logger = LogManager.getLogger(ActiveConnection.class);

    private final SSLContext _ctx;

    private final InetSocketAddress _addr;

    private Socket _sock;

    private final boolean _useSSLClientHandshake;

    private final InetAddress _bindIP;

    public ActiveConnection(SSLContext ctx, InetSocketAddress addr, boolean useSSLClientHandshake, InetAddress bindIP) {
        _addr = addr;
        _ctx = ctx;
        _bindIP = bindIP;
        _useSSLClientHandshake = useSSLClientHandshake;
    }

    public Socket connect(String[] cipherSuites, String[] sslProtocols, int bufferSize) throws IOException, java.net.SocketException {
        logger.debug("Connecting to {}:{}", _addr.getAddress().getHostAddress(), _addr.getPort());

        if (_ctx != null) {
            SSLSocket sslSock = (SSLSocket) _ctx.getSocketFactory().createSocket();
            if (bufferSize > 0) {
                sslSock.setReceiveBufferSize(bufferSize);
            }

            if (_bindIP != null) {
                sslSock.bind(new InetSocketAddress(_bindIP, sslSock.getPort()));
            }

            sslSock.connect(_addr, TIMEOUT);
            setSockOpts(sslSock);
            if (cipherSuites != null && cipherSuites.length != 0) {
                sslSock.setEnabledCipherSuites(cipherSuites);
            }
            if (sslProtocols != null && sslProtocols.length != 0) {
                sslSock.setEnabledProtocols(sslProtocols);
            }
            logger.debug("[{}] Enabled ciphers for this new connection are as follows: '{}'",
                    sslSock.getRemoteSocketAddress(), Arrays.toString(sslSock.getEnabledCipherSuites()));
            logger.debug("[{}] Enabled protocols for this new connection are as follows: '{}'",
                    sslSock.getRemoteSocketAddress(), Arrays.toString(sslSock.getEnabledProtocols()));
            sslSock.setUseClientMode(_useSSLClientHandshake);
            sslSock.startHandshake();
            _sock = sslSock;
        } else {
            _sock = SocketFactory.getDefault().createSocket();
            if (bufferSize > 0) {
                _sock.setReceiveBufferSize(bufferSize);
            }

            if (_bindIP != null) {
                _sock.bind(new InetSocketAddress(_bindIP, _sock.getPort()));
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
