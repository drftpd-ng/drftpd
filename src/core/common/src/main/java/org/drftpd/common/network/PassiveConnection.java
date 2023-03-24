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
package org.drftpd.common.network;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.slave.Connection;
import org.drftpd.common.util.PortRange;

import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;

/**
 * @author mog
 * @version $Id$
 */
public class PassiveConnection extends Connection {
    private static final Logger logger = LogManager.getLogger(PassiveConnection.class);

    private ServerSocket _serverSocket;

    // Default is to initiate the handshake
    private final boolean _useSSLClientMode;

    /**
     * Insert text here
     *
     * @param ctx The SSL/TLS Security Context
     * @param portRange The port range we can use to get a local port
     * @param useSSLClientMode Whether this will be a server connection or client connection
     * @param bindIP The InetAddress representing the ip we need to bind to or null in which case we bind to all interfaces
     *
     * @throws IOException Creates a PassiveConnection - If ctx==null, the Connection will not use SSL
     */
    public PassiveConnection(SSLContext ctx, PortRange portRange, boolean useSSLClientMode, InetAddress bindIP) throws SocketException {
        _useSSLClientMode = useSSLClientMode;
        if (ctx != null) {
            _serverSocket = portRange.getPort(ctx.getServerSocketFactory(), bindIP);
        } else {
            _serverSocket = portRange.getPort(ServerSocketFactory.getDefault(), bindIP);
        }
        _serverSocket.setSoTimeout(TIMEOUT);
    }

    public Socket connect(String[] cipherSuites, String[] sslProtocols, int bufferSize) throws IOException, SocketException {
        // bufferSize has already been set on the ServerSocket
        // just need to accept this param to comply with the Connection class

        if (_serverSocket == null) {
            // can happen if abort() is called before connect()
            throw new SocketException("abort() was called before connect()");
        }

        Socket sock;
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
            SSLSocket sslSock = (SSLSocket) sock;
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
            sslSock.setUseClientMode(_useSSLClientMode);
            sslSock.startHandshake();
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
}
