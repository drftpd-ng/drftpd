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
package org.drftpd.master;

import java.util.HashMap;
import java.util.Properties;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;

/**
 * @author djb61
 * @version $Id$
 */
public class Session extends KeyedMap {

	public static final Key COMMANDS = new Key(Session.class, "session",
			HashMap.class);

	public void setCommands(HashMap<String,Properties> commands) {
		setObject(Session.COMMANDS, commands);
	}

	public HashMap<String,Properties> getCommands() {
		return (HashMap<String,Properties>) getObject(Session.COMMANDS, null);
	}
}
