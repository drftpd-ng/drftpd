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
package org.drftpd.permissions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.drftpd.usermanager.User;

/**
 * @author mog
 * @version $Id$
 */
public class Permission {
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
	 * @param user
	 * @return
	 */
	public boolean check(User user) {
		boolean allow = false;

		for (Iterator<String> iter = _users.iterator(); iter.hasNext();) {
			String aclUser = iter.next();
			allow = true;
			System.out.println("Evaluating permissions = " + aclUser);
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

				continue;
			} else if (aclUser.charAt(0) == '=') {
				// GROUP
				if (user == null) {
					continue;
				}
				if (user.isMemberOf(aclUser.substring(1))) {
					return allow;
				}
			} else {
				// FLAG, we don't have flags, we have groups and that's the same
				// but multiple letters
				// Does anyone use these?  Do we want to get rid of the = modifier?
				if (user == null) {
					continue;
				}
				if (user.isMemberOf(aclUser)) {
					return allow;
				}
			}
		}

		// didn't match..
		return _invert ? (!allow) : false;
	}
	
	public static ArrayList<String> makeUsers(Enumeration<Object> st) {
		ArrayList<String> users = new ArrayList<String>();

		while (st.hasMoreElements()) {
			users.add((String) st.nextElement());
		}

		return users;
	}
}
