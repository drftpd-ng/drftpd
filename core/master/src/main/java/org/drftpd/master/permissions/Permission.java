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
package org.drftpd.master.permissions;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.usermanager.User;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author mog
 * @version $Id$
 */
public class Permission {
	private static final Logger logger = LogManager.getLogger();

	protected Collection<String> _users;

	private boolean _invert = false;

	public Permission(Collection<String> users) {
		_users = users;
	}

	public Permission(Collection<String> users, boolean invert) {
		this(users);
		_invert = invert;
	}

	public Permission(String permissionString) {
		this(makeUsers(new StringTokenizer(permissionString)));
	}

	/**
	 * Accepts 5 kinds of modifiers Authenticated users = * Non-authenticated
	 * users = % Group = =<groupname> User = -<username> NOT = !<nextmodifier>
	 * Accepts a null User for purposes of evaluating permission for
	 * Non-authenticated users
	 * If no Permission line is given, this assumes !% is the last one
	 * returns true if the User has permission
	 *
	 * @param user The User Object we need to check
	 * @return true if the user is allowed, false otherwise
	 */
	public boolean check(User user) {
		boolean allow = false;

		for (String aclUser : _users) {
			logger.debug("[Permission::check] aclUser: [" + aclUser + "]");
			allow = true;
			if (aclUser.charAt(0) == '!') {
				allow = false;
				aclUser = aclUser.substring(1);
			}
			if (aclUser.equals("%")) {
				return allow;
			} else if (aclUser.equals("*") && user != null) {
				return allow;
			} else if (aclUser.charAt(0) == '-') {
				// USER
				if (user == null) {
					continue;
				}
				if (aclUser.substring(1).equals(user.getName())) {
					return allow;
				}

			} else if (aclUser.charAt(0) == '=') {
				// GROUP
				if (user == null) {
					continue;
				}
				// TODO: Monkey patch this so we can revisit this later when we fix =deleted, =siteop and =gadmin (maybe more?)
				if (aclUser.equals("=gadmin")) {
					if (GlobalContext.getGlobalContext().getUserManager().isGroupAdmin(user)) {
						return allow;
					}
				} else if (user.isMemberOf(aclUser.substring(1))) {
					return allow;
				}
			} else {
				// FLAG, we don't have flags
				logger.error("Incorrect usage of perms string '" + aclUser + "' is unsupported");
			}
		}

		// didn't match..
		return _invert && (!allow);
	}

	public static ArrayList<String> makeUsers(Enumeration<Object> st) {
		ArrayList<String> users = new ArrayList<>();

		while (st.hasMoreElements()) {
			users.add((String) st.nextElement());
		}

		return users;
	}
}
