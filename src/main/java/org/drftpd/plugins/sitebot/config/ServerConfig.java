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
package org.drftpd.plugins.sitebot.config;

import org.drftpd.plugins.sitebot.SiteBotSSLSocketFactory;

import javax.net.SocketFactory;
import javax.net.ssl.X509TrustManager;

/**
 * @author djb61
 * @version $Id$
 */
public class ServerConfig {

	private String _hostName;

	private int _port;

	private String _password;

	private boolean _ssl;

	private X509TrustManager _trustManager;

	public ServerConfig(String hostName, int port, String password, boolean ssl, X509TrustManager trustManager) {
		_hostName = hostName;
		_port = port;
		_password = password;
		_ssl = ssl;
		_trustManager = trustManager;
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
}
