/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.master.usermanager;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.ConnectionManager;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;


/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id: UserManager.java,v 1.23 2004/08/03 20:13:59 zubov Exp $
 */
public abstract class UserManager {
    protected ConnectionManager _connManager;
    protected Hashtable _users;

    public UserManager() {
        _users = new Hashtable();
    }

    public User create(String username) throws UserFileException {
        try {
            getUserByName(username);

            //bad
            throw new FileExistsException("User already exists");
        } catch (IOException e) {
            //bad
            throw new UserFileException(e);
        } catch (NoSuchUserException e) {
            //good
        }

        User user = _connManager.getGlobalContext().getUserManager().createUser(username);
        user.commit();

        return user;
    }

    public abstract User createUser(String username);

    public abstract void delete(String string);

    public Collection getAllGroups() throws UserFileException {
        Collection users = getAllUsers();
        ArrayList ret = new ArrayList();

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User myUser = (User) iter.next();
            Collection myGroups = myUser.getGroups();

            for (Iterator iterator = myGroups.iterator(); iterator.hasNext();) {
                String myGroup = (String) iterator.next();

                if (!ret.contains(myGroup)) {
                    ret.add(myGroup);
                }
            }

            if (!ret.contains(myUser.getGroupName())) {
                ret.add(myUser.getGroupName());
            }
        }

        return ret;
    }

    /**
     * Get all user names in the system.
     */
    public abstract List getAllUsers() throws UserFileException;

    public Collection getAllUsersByGroup(String group)
        throws UserFileException {
        Collection c = new ArrayList();

        for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
            User user = (User) iter.next();

            if (user.isMemberOf(group)) {
                c.add(user);
            }
        }

        return c;
    }

    /**
     * Get user by name.
     */

    //TODO garbage collected Map of users.
    public User getUserByName(String username)
        throws NoSuchUserException, UserFileException {
        User user = (User) getUserByNameUnchecked(username);

        if (user.isDeleted()) {
            throw new NoSuchUserException(user.getUsername() + " is deleted");
        }

        user.reset(_connManager);

        return user;
    }

    public abstract User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException;

    /**
     * A kind of constuctor defined in the interface for allowing the
     * usermanager to get a hold of the ConnectionManager object for dispatching
     * events etc.
     */
    public void init(ConnectionManager mgr) {
        _connManager = mgr;
    }

    public void remove(User user) {
        _users.remove(user.getUsername());
    }

    void rename(User oldUser, String newUsername)
        throws UserExistsException, UserFileException {
        if (!_users.contains(newUsername)) {
            try {
                getUserByNameUnchecked(newUsername);
            } catch (NoSuchUserException e) {
                _users.remove(oldUser.getUsername());
                _users.put(newUsername, oldUser);

                return;
            }
        }

        throw new UserExistsException("user " + newUsername + " exists");
    }

    public abstract void saveAll() throws UserFileException;
}
