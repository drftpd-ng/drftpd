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
package net.sf.drftpd.util;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/**
 * @author mog
 * @version $Id: SSLGetContext.java,v 1.3 2004/02/10 00:03:32 mog Exp $
 */
public class SSLGetContext {
	public static SSLContext getSSLContext()
		throws GeneralSecurityException, IOException {
		SSLContext ctx = SSLContext.getInstance("TLS");

		KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");

		KeyStore ks = KeyStore.getInstance("JKS");
		ks.load(new FileInputStream("drftpd.key"), "drftpd".toCharArray());

		kmf.init(ks, "drftpd".toCharArray());

		ctx.init(kmf.getKeyManagers(), null, null);
		return ctx;
	}
}
