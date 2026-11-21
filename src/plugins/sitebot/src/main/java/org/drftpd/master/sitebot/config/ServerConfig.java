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
package org.drftpd.master.sitebot.config;

import org.drftpd.master.sitebot.SiteBotSSLSocketFactory;

import javax.net.SocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * @author djb61
 * @version $Id$
 */
public class ServerConfig {

    private final String _hostName;

    private final int _port;

    private final String _password;

    private final boolean _ssl;

    private final X509TrustManager _trustManager;

    private final String _saslUsername;

    private final String _saslPassword;

    private final boolean _saslRequired;

    private final boolean _saslAllowPlaintext;

    public ServerConfig(String hostName, int port, String password, boolean ssl, X509TrustManager trustManager) {
        this(hostName, port, password, ssl, trustManager, null, null, false, false);
    }

    public ServerConfig(String hostName, int port, String password, boolean ssl, X509TrustManager trustManager,
                        String saslUsername, String saslPassword, boolean saslRequired, boolean saslAllowPlaintext) {
        _hostName = hostName;
        _port = port;
        _password = password;
        _ssl = ssl;
        _trustManager = trustManager;
        _saslUsername = saslUsername;
        _saslPassword = saslPassword;
        _saslRequired = saslRequired;
        _saslAllowPlaintext = saslAllowPlaintext;
    }

    public String getHostName() {
        return _hostName;
    }

    public int getPort() {
        return _port;
    }

    public String getPassword() {
        return _password;
    }

    public SocketFactory getSocketFactory() {
        if (_ssl) {
            return new SiteBotSSLSocketFactory(_trustManager);
        }
        return null;
    }

    public boolean isSsl() {
        return _ssl;
    }

    public String getSaslUsername() {
        return _saslUsername;
    }

    public String getSaslPassword() {
        return _saslPassword;
    }

    /**
     * @return true when SASL must succeed, otherwise registration continues without it on failure
     */
    public boolean isSaslRequired() {
        return _saslRequired;
    }

    /**
     * @return true when both a SASL username and password are configured
     */
    public boolean isSaslEnabled() {
        return _saslUsername != null && !_saslUsername.isEmpty()
                && _saslPassword != null && !_saslPassword.isEmpty();
    }

    /**
     * @return true when SASL PLAIN may be sent over a connection without TLS (sasl.allow_plaintext=true)
     */
    public boolean isSaslPlaintextAllowed() {
        return _saslAllowPlaintext;
    }

    /**
     * @return true when SASL is configured but must not be used because the connection is not TLS
     * and sending the password in clear text has not been explicitly allowed
     */
    public boolean isSaslRefusedWithoutTls() {
        return isSaslEnabled() && !_ssl && !_saslAllowPlaintext;
    }
}
