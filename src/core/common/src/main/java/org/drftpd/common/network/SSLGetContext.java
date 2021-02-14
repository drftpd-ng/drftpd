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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.Arrays;

/**
 * @author mog
 * @version $Id$
 */
public class SSLGetContext {

    private static final Logger logger = LogManager.getLogger(SSLGetContext.class);

    static SSLContext _context = null;

    public static SSLContext getSSLContext() throws GeneralSecurityException, IOException {
        // Setup a new context if we do not have one already
        if (_context == null) {
            // Create a trust manager that does not validate certificate chains
            // TODO: Maybe we should make this a configurable option so that if users want to load official keys it supports it OK?
            TrustManager[] trustAllCerts = new TrustManager[]{new X509TrustManager() {
                public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                    return null;
                }

                public void checkClientTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }

                public void checkServerTrusted(
                    java.security.cert.X509Certificate[] certs, String authType) {
                }
            }};

            KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

            KeyStore ks = KeyStore.getInstance("PKCS12");
            try (FileInputStream fis = new FileInputStream("config/drftpd.key")) {
                ks.load(fis, "drftpd".toCharArray());
            }

            kmf.init(ks, "drftpd".toCharArray());

            _context = SSLContext.getInstance("TLSv1.3");
            _context.init(kmf.getKeyManagers(), trustAllCerts, null);
            logger.debug("Supported ciphers are as follows: '{}'", Arrays.toString(_context.createSSLEngine().getSupportedCipherSuites()));
            logger.debug("Supported protocols are as follows: '{}'", Arrays.toString(_context.createSSLEngine().getSupportedProtocols()));
        }

        return _context;
    }
}
