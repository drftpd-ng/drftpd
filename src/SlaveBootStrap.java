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
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;

/**
 * @author mog
 *
 * Starts DrFTPD slave (net.sf.drftpd.slave.SlaveImpl) by loading a .jar file over the network.
 * Takes URL as first argument and passes the rest of the arguments to SlaveImpl.main()
 */
public class SlaveBootStrap {
	public static void main(String args[]) throws Throwable {
		URL urls[] = { new URL(args[0])};
		URLClassLoader cl = new URLClassLoader(urls);
		Method met =
			cl.loadClass("net.sf.drftpd.slave.SlaveImpl").getMethod(
				"main",
				new Class[] { String[].class });
		met.invoke(null, new Object[] { scrubArgs(args, 1)});
	}

	public static String[] scrubArgs(String args[], int scrub) {
		String ret[] = new String[args.length - scrub];
		System.arraycopy(args, scrub, ret, 0, ret.length);
		return ret;
	}
}
