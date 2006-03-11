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

package org.drftpd.irc.utils;

import org.schwering.irc.lib.IRCUser;

/**
 * This class exists just for one reason,
 * make an easy way to convert IRCCommands.
 * @author fr0w
 */
public class MessageCommand {

	IRCUser _user = null;
	String _dest = "";
	String _msg = "";
	
	/**
	 * @param user
	 * @param dest
	 * @param msg
	 */
	public MessageCommand(IRCUser user, String dest, String msg) {
		_user = user;
		_dest = dest;
		_msg = msg;
	}
	
	public String getDest() {
		return _dest;
	}
	
	public IRCUser getSource() {
		return _user;
	}
	
	public String getMessage() {
		return _msg;
	}
	
}
