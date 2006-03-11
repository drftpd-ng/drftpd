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

import java.util.ArrayList;
import java.util.StringTokenizer;

import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.schwering.irc.lib.IRCUser;

/**
 * IRCPermissions.
 * @author fr0w
 */
public class IRCPermission {
	
	private ArrayList<String> _sourceList = new ArrayList<String>();
	private String _dest = null;
	private String _permissions = null;
	
	private static final Logger logger = Logger.getLogger(IRCPermission.class);
	
	/**
	 * Constructor.
	 * @param source, might contain separated by comma values. (public, private, #channel.names.)
	 * @param dest, might contain a single value. (public, private, source, #channel.names.)
	 * @param permissions (perms.conf like permissions.)
	 */
	public IRCPermission(String source, String dest, String permissions) {
		for (String s : source.split(",")) {
			_sourceList.add(s);
		}
		_permissions = permissions;
		_dest = dest;
	}		
	
	/**
	 * Accepts channel names, "public", or "private"
	 */
	public boolean checkSource(String source) {
		if (_sourceList.contains(source)) { // matches private or channel names
			return true;
		}
		return source.startsWith("#") && _sourceList.contains("public");
	}
	
	/**
	 * Might contain this values: private,public,source,#chan
	 * - private: will return a query msg.
	 * - public: will spam to ALL channels.
	 * - source: will return the output to where the cmd was issued.
	 * - #chan: will spam to this specific channel.
	 * @return the destination.
	 */
	public String getDestination() {
		return _dest;
	}
	
	public boolean checkPermission(IRCUser user) {
		if (_permissions.equals("*")) {
			return true;
		}
		try {
			return new Permission(FtpConfig.makeUsers(new StringTokenizer(_permissions))).check(lookupUser(user));
		} catch (Exception e) {
			logger.warn(e);
			return false;
		}
	}
	
	public User lookupUser(IRCUser user) throws Exception {
		String ident = user.getNick() + "!" + user.getUsername() + "@" + user.getHost();
		try {
			return GlobalContext.getGlobalContext().getUserManager().getUserByIdent(ident);
		} catch (Exception e) {
			//logger.warn("Could not identify " + ident);
			throw new Exception(e.getMessage());			
		}

	}
}
