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
package org.drftpd.master.slavemanagement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Optional pinning of a slave's TLS client certificate by its SHA-256 fingerprint.
 * <p>
 * The master asks slaves for a client certificate when {@code slavemanager.ssl.client_authentication}
 * is {@code optional} or {@code required} in master.conf. When a slave has a {@code fingerprint} property
 * ({@code SITE SLAVE <name> SET fingerprint SHA256:<hex>}), it may only connect with a certificate whose
 * SHA-256 fingerprint matches; otherwise the connection is refused (fail closed). Without that property
 * nothing changes: the fingerprint is only logged so it can be pinned later.
 * <p>
 * Pinning is only meaningful if every slave has its own key: when all slaves share the same drftpd.key,
 * they all present the same certificate and therefore the same fingerprint.
 */
public final class SlaveCertificatePin {

    private static final Logger logger = LogManager.getLogger(SlaveCertificatePin.class);

    /** Slave property holding the pinned fingerprint. */
    public static final String PROPERTY = "fingerprint";

    private static final String PREFIX = "SHA256:";

    private SlaveCertificatePin() {
    }

    /**
     * @param slaveName  name of the slave, for logging
     * @param configured the slave's pinned fingerprint, or null/empty when none is pinned
     * @param socket     the slave's control connection, after the TLS handshake
     * @return whether the slave may connect
     */
    static boolean check(String slaveName, String configured, Socket socket) {
        X509Certificate cert = null;
        if (socket instanceof SSLSocket sslSocket) {
            try {
                Certificate[] certs = sslSocket.getSession().getPeerCertificates();
                if (certs.length > 0 && certs[0] instanceof X509Certificate x509) {
                    cert = x509;
                }
            } catch (SSLPeerUnverifiedException e) {
                // No client certificate: client authentication is disabled or the slave sent none
            }
        }
        return decide(slaveName, configured, cert);
    }

    /**
     * @param cert the certificate the slave presented, or null if it presented none
     * @return whether the slave may connect
     */
    static boolean decide(String slaveName, String configured, X509Certificate cert) {
        boolean pinned = configured != null && !configured.isBlank();
        if (cert == null) {
            if (pinned) {
                logger.error("Slave {} has a pinned certificate fingerprint but presented no client certificate. "
                        + "Check slavemanager.ssl.client_authentication in master.conf", slaveName);
                return false;
            }
            return true;
        }

        String actual;
        try {
            actual = fingerprint(cert);
        } catch (CertificateEncodingException e) {
            logger.error("Unable to read the client certificate of slave {}", slaveName, e);
            return !pinned;
        }

        if (!pinned) {
            logger.info("Slave {} connected with certificate fingerprint {}. To only accept this certificate, run: "
                    + "SITE SLAVE {} SET {} {}", slaveName, actual, slaveName, PROPERTY, actual);
            return true;
        }
        if (!matches(configured, actual)) {
            logger.error("Slave {} certificate fingerprint mismatch, refusing connection. Expected {}, got {}",
                    slaveName, configured, actual);
            return false;
        }
        logger.debug("Slave {} certificate fingerprint verified", slaveName);
        return true;
    }

    /** Returns the fingerprint of the certificate as {@code SHA256:<uppercase hex>}. */
    static String fingerprint(X509Certificate cert) throws CertificateEncodingException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(cert.getEncoded());
            return PREFIX + HexFormat.of().withUpperCase().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            // Every Java platform is required to support SHA-256
            throw new IllegalStateException(e);
        }
    }

    /**
     * Compares fingerprints ignoring case, the optional {@code SHA256:} prefix, and the colons or spaces
     * that tools such as openssl and keytool put between bytes.
     */
    static boolean matches(String configured, String actual) {
        String a = normalize(configured);
        return !a.isEmpty() && a.equals(normalize(actual));
    }

    private static String normalize(String fingerprint) {
        String f = fingerprint == null ? "" : fingerprint.trim().toUpperCase(Locale.ROOT);
        if (f.startsWith(PREFIX)) {
            f = f.substring(PREFIX.length());
        } else if (f.startsWith("SHA-256:")) {
            f = f.substring("SHA-256:".length());
        }
        return f.replace(":", "").replace(" ", "");
    }
}
