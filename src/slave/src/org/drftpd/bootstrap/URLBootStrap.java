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
package org.drftpd.bootstrap;

import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * Starts DrFTPD slave (org.drftpd.slave.Slave) by loading a .jar file over the
 * network. Takes URL as first argument and passes the rest of the arguments to
 * Slave.main()
 * 
 * @author mog
 * @version $Id$
 */
public class URLBootStrap {
	public static void main(String[] args) throws Throwable {
		URL[] urls = { new URL(args[0]) };
		URLClassLoader cl = new URLClassLoader(urls);
		Method met = cl.loadClass(args[1]).getMethod("main",
                String[].class);
		met.invoke(null, new Object[] { scrubArgs(args, 2) });
		// Close cl
		try {
			cl.close();
		} catch (IOException e) {
			// already closed
		}
	}

	public static String[] scrubArgs(String[] args, int scrub) {
		String[] ret = new String[args.length - scrub];
		System.arraycopy(args, scrub, ret, 0, ret.length);

		return ret;
	}
}
