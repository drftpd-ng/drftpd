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


import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

/**
 * @author mog
 * @version $Id$
 */
public class SSLGetContext {
	static SSLContext ctx = null;

	private static final Logger logger = LogManager.getLogger(SSLGetContext.class);

	public static SSLContext getSSLContext() throws GeneralSecurityException,
			IOException {
		// Create a trust manager that does not validate certificate chains
		TrustManager[] trustAllCerts = new TrustManager[] { new X509TrustManager() {
			public java.security.cert.X509Certificate[] getAcceptedIssuers() {
				return null;
			}

			public void checkClientTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
			}

			public void checkServerTrusted(
					java.security.cert.X509Certificate[] certs, String authType) {
			}
		} };
		if (ctx != null)
			return ctx; // reuse previous SSLContext

		ctx = SSLContext.getInstance("TLSv1.2");

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

		KeyStore ks = KeyStore.getInstance("PKCS12");
		FileInputStream fis = null;
		try {
			fis = new FileInputStream("userdata/drftpd.key");
			ks.load(fis, "drftpd".toCharArray());
		} finally {
			if (fis != null) {
				fis.close();
			}
		}

		kmf.init(ks, "drftpd".toCharArray());

		ctx.init(kmf.getKeyManagers(), trustAllCerts, null);
		String[] ciphers = ctx.createSSLEngine().getSupportedCipherSuites();
		logger.info("Supported ciphers are as follows:");
		for (String cipher : ciphers) {
			logger.info(cipher);
		}
		String[] protocols = ctx.createSSLEngine().getSupportedProtocols();
		logger.info("Supported protocols are as follows:");
		for (String protocol : protocols) {
			logger.info(protocol);
		}
/*		for (String cipher : ciphers) {
			logger.info(cipher);
		}
*/		return ctx;
	}
}
