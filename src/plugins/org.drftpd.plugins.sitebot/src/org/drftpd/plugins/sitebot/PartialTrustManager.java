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
package org.drftpd.plugins.sitebot;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;
import java.io.*;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map.Entry;

/**
 * @author djb61
 * @version $Id$
 */
public class PartialTrustManager implements X509TrustManager {

	private static final Logger logger = LogManager.getLogger(PartialTrustManager.class);

	private HashMap<String,X509Certificate> _certs = new HashMap<>();

	private CertificateFactory _factory; 

	private TrustManager[] _defaultManagers;

	public PartialTrustManager(String password) {
		FileInputStream fis = null;
		try {
			_factory = CertificateFactory.getInstance("X.509");
			TrustManagerFactory managerFactory = TrustManagerFactory.getInstance("SunX509","SunJSSE");
			// Load the JDK's cacerts keystore file
	        String filename = System.getProperty("java.home")
	            + "/lib/security/cacerts".replace('/', File.separatorChar);
	        fis = new FileInputStream(filename);
	        KeyStore keystore = KeyStore.getInstance(KeyStore.getDefaultType());
	        keystore.load(fis, password.toCharArray());

			loadCerts();
			for (Entry<String,X509Certificate> entry : _certs.entrySet()) {
				keystore.setCertificateEntry(entry.getKey(), entry.getValue());
			}
			managerFactory.init(keystore);
			_defaultManagers = managerFactory.getTrustManagers();
		} catch (CertificateException e) {
			logger.error("X509 SSL provider unavailable",e);
		} catch (KeyStoreException e) {
			logger.error("X509 SSL provider unavailable",e);
		} catch (NoSuchAlgorithmException e) {
			logger.error("Encryption algorithm unavailable",e);
		} catch (NoSuchProviderException e) {
			logger.error("Encryption provider unavailable",e);
		} catch (FileNotFoundException e) {
			logger.error("System keystore not found", e);
		} catch (IOException e) {
			logger.error("Error loading system keystore",e);
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
	}

	public void checkClientTrusted(X509Certificate[] chain, String authType)
		throws CertificateException {

	}

	public void checkServerTrusted(X509Certificate[] chain, String authType)
		throws CertificateException {
		for (TrustManager manager : _defaultManagers) {
			if (manager instanceof X509TrustManager) {
				X509TrustManager x509Manager = (X509TrustManager) manager;
				x509Manager.checkServerTrusted(chain, authType);
			}
		}
	}

	public X509Certificate[] getAcceptedIssuers() {
		return new X509Certificate[0];
	}

	private void loadCerts() {
		File dir = new File("conf/plugins/irc/certs");
		if (!dir.isDirectory())
			throw new RuntimeException("conf/plugins/irc/certs" + " is not a directory");

		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				loadCert(file);
			} // else, ignore it.
		}
	}

	private void loadCert(File file) {
		FileInputStream fis = null;
		BufferedInputStream bis = null;

		try {			
			fis = new FileInputStream(file);
			bis = new BufferedInputStream(fis);

			while (bis.available() > 0) {
				X509Certificate cert = (X509Certificate) _factory.generateCertificate(bis);
				_certs.put(file.getName(),cert);
			}
		} catch (CertificateException e) {
			logger.error("An error ocurred while loading a trusted SSL certificate",e);
		} catch (FileNotFoundException e) {
			logger.error("Weird the file was just there, how come it's gone?", e);
		} catch (IOException e) {
			logger.error("An error ocurred while loading a trusted SSL certificate");
		} finally {
			if (bis != null) {
				try {
					bis.close();
				} catch (IOException e) {
				}
			}
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
	}
}
