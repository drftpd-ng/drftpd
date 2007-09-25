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

import java.util.Properties;

/**
 * @author mog
 * @version $Id$
 */
public class PropertyHelper {
	private PropertyHelper() {
	}

	public static String getProperty(Properties p, String name)
			throws NullPointerException {
		String result = p.getProperty(name);

		if (result == null) {
			throw new NullPointerException("Error getting setting " + name);
		}

		return result;
	}
	
	public static String getProperty(Properties p, String name,
			String defaultValue) throws NullPointerException {
		return p.getProperty(name, defaultValue);
		// result can't be null
	}
}
