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

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Arrays;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotSSLSocketFactory extends SSLSocketFactory {

    private static final Logger logger = LogManager.getLogger(SiteBotSSLSocketFactory.class);

    private SSLSocketFactory _factory;

    public SiteBotSSLSocketFactory(TrustManager trustManager) {
        try {
            SSLContext sslcontext = SSLContext.getInstance("TLSv1.3");
            sslcontext.init(null, new TrustManager[]{trustManager}, null);
            logger.debug("Globally supported ciphers on this host are as follows: '{}'", Arrays.toString(sslcontext.createSSLEngine().getSupportedCipherSuites()));
            logger.debug("Globally supported protocols on this host are as follows: '{}'", Arrays.toString(sslcontext.createSSLEngine().getSupportedProtocols()));
            _factory = sslcontext.getSocketFactory();
        } catch (Exception e) {
            logger.error("Exception creating socket factory", e);
        }
    }

    @Override
    public Socket createSocket(Socket socket, String s, int i, boolean flag) throws IOException {
        return _factory.createSocket(socket, s, i, flag);
    }

    @Override
    public Socket createSocket(InetAddress inaddr, int i, InetAddress inaddr1, int j) throws IOException {
        return _factory.createSocket(inaddr, i, inaddr1, j);
    }

    @Override
    public Socket createSocket(InetAddress inaddr, int i) throws IOException {
        return _factory.createSocket(inaddr, i);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inaddr, int j) throws IOException, java.net.UnknownHostException {
        return _factory.createSocket(s, i, inaddr, j);
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, java.net.UnknownHostException {
        return _factory.createSocket(s, i);
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

