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
package net.sf.drftpd.master.config;

import net.sf.drftpd.master.usermanager.User;

import java.util.Collection;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: Permission.java,v 1.8 2004/10/05 02:11:23 mog Exp $
 */
public class Permission {
    private Collection _users;
    private boolean _invert = false;

    public Permission(Collection users) {
        _users = users;
    }

    public Permission(Collection users, boolean invert) {
        this(users);
        _invert = invert;
    }

    public boolean check(User user) {
        boolean allow = false;

        for (Iterator iter = _users.iterator(); iter.hasNext();) {
            String aclUser = (String) iter.next();
            allow = true;

            if (aclUser.charAt(0) == '!') {
                allow = false;
                aclUser = aclUser.substring(1);
            }

            if (aclUser.equals("*")) {
                return allow;
            } else if (aclUser.charAt(0) == '-') {
                //USER
                if (aclUser.substring(1).equals(user.getUsername())) {
                    return allow;
                }

                continue;
            } else if (aclUser.charAt(0) == '=') {
                //GROUP
                if (user.isMemberOf(aclUser.substring(1))) {
                    return allow;
                }
            } else {
                //FLAG, we don't have flags, we have groups and that's the same but multiple letters
                if (user.isMemberOf(aclUser)) {
                    return allow;
                }
            }
        }

        // didn't match.. 
        return _invert ? (!allow) : false;
    }
}
