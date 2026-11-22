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
package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SNIHostName;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;
import java.util.List;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotSSLSocketFactory extends SSLSocketFactory {

    private static final Logger logger = LogManager.getLogger(SiteBotSSLSocketFactory.class);

    private SSLSocketFactory _factory;

    private final boolean _verifyHostName;

    public SiteBotSSLSocketFactory(TrustManager trustManager) {
        this(trustManager, false);
    }

    /**
     * @param trustManager   the trust manager validating the server certificate chain, null for the JDK default
     * @param verifyHostName true to enable host name verification (HTTPS endpoint identification) and SNI on
     *                       every socket created by this factory, before its handshake
     */
    public SiteBotSSLSocketFactory(TrustManager trustManager, boolean verifyHostName) {
        _verifyHostName = verifyHostName;
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLSv1.3");
            sslcontext.init(null, trustManager == null ? null : new TrustManager[]{trustManager}, null);
            logger.debug("Globally supported ciphers on this host are as follows: '{}'", Arrays.toString(sslcontext.createSSLEngine().getSupportedCipherSuites()));
            logger.debug("Globally supported protocols on this host are as follows: '{}'", Arrays.toString(sslcontext.createSSLEngine().getSupportedProtocols()));
            _factory = sslcontext.getSocketFactory();
        } catch (Exception e) {
            logger.error("Exception creating socket factory", e);
        }
    }

    @Override
    public Socket createSocket(Socket socket, String s, int i, boolean flag) throws IOException {
        return configure(_factory.createSocket(socket, s, i, flag), s);
    }

    @Override
    public Socket createSocket(InetAddress inaddr, int i, InetAddress inaddr1, int j) throws IOException {
        return configure(_factory.createSocket(inaddr, i, inaddr1, j), null);
    }

    @Override
    public Socket createSocket(InetAddress inaddr, int i) throws IOException {
        return configure(_factory.createSocket(inaddr, i), null);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inaddr, int j) throws IOException {
        return configure(_factory.createSocket(s, i, inaddr, j), s);
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException {
        return configure(_factory.createSocket(s, i), s);
    }

    private Socket configure(Socket socket, String host) {
        if (_verifyHostName && socket instanceof SSLSocket) {
            enableHostNameVerification((SSLSocket) socket, host);
        }
        return socket;
    }

    /**
     * Enables host name verification (HTTPS endpoint identification, RFC 2818/6125) and sets SNI to the host
     * name, if it is one. Must be called before the handshake starts.
     */
    public static void enableHostNameVerification(SSLSocket socket, String host) {
        SSLParameters params = socket.getSSLParameters();
        params.setEndpointIdentificationAlgorithm("HTTPS");
        if (host != null && !host.isEmpty() && !isIpLiteral(host)) {
            try {
                params.setServerNames(List.of(new SNIHostName(host)));
            } catch (IllegalArgumentException e) {
                // not a valid SNI host name: the certificate is still checked against it
                logger.debug("Not sending SNI for [{}]: {}", host, e.getMessage());
            }
        }
        socket.setSSLParameters(params);
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return _factory.getSupportedCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return _factory.getSupportedCipherSuites();
    }
}

