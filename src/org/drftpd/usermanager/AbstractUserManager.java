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
package org.drftpd.usermanager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FileExistsException;

import org.apache.log4j.Logger;
import org.drftpd.commands.UserManagment;
import org.drftpd.master.ConnectionManager;

import se.mog.io.PermissionDeniedException;

/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id$
 */
public abstract class AbstractUserManager implements UserManager {
    protected ConnectionManager _connManager;
    protected Hashtable<String, User> _users = new Hashtable<String, User>();
	private static final Logger logger = Logger.getLogger(AbstractUserManager.class);

	/**
	 * For DummyUserManager, skips creation of userfile directory.
	 */
	public AbstractUserManager() {
	}

	/**
	 * <p>Cannot be a constructor because the child class fields don't get
	 * initialized until super (this) class gets run.</p> 
	 */
    protected void init(boolean createIfNoUser) throws UserFileException {
        if (!getUserpathFile().exists() && !getUserpathFile().mkdirs()) {
            throw new UserFileException(new IOException(
                    "Error creating folders: " + getUserpathFile()));
        }
        if (createIfNoUser) {
            String[] userfilenames = getUserpathFile().list();
            boolean hasUsers = false;

            for (int i = 0; i < userfilenames.length; i++) {
                String string = userfilenames[i];

                if (string.endsWith(".xml")) {
                    hasUsers = true;

                    break;
                }
            }

            if (!hasUsers) {
                createSiteopUser();
            }
        }
    }

	protected abstract File getUserpathFile();

	protected void createSiteopUser() throws UserFileException {
        User user = createUser("drftpd");
        user.setGroup("drftpd");
        user.setPassword("drftpd");
        user.getKeyedMap().setObject(UserManagment.RATIO, new Float(0));

        try {
            user.addIPMask("*@127.0.0.1");
            user.addIPMask("*@0:0:0:0:0:0:0:1");
        } catch (DuplicateElementException e) {
        }

        try {
            user.addSecondaryGroup("siteop");
        } catch (DuplicateElementException e1) {
        }

        user.commit();
    }

    public User create(String username) throws UserFileException {
        try {
            getUserByName(username);

            //bad
            throw new FileExistsException("User " + username +
                " already exists");
        } catch (IOException e) {
            //bad
            throw new UserFileException(e);
        } catch (NoSuchUserException e) {
            //good
        }

        //User user = _connManager.getGlobalContext().getUserManager().createUser(username);
        User user = createUser(username);
        user.commit();

        return user;
    }

    protected abstract User createUser(String username);

    /**
     * final for now to remove duplicate implementations
     */
    public void delete(String username) {
        if(!getUserFile(username).delete())
        	throw new RuntimeException(new PermissionDeniedException());
        _users.remove(username);
    }

	protected abstract File getUserFile(String username);

	public Collection getAllGroups() throws UserFileException {
        Collection users = getAllUsers();
        ArrayList<String> ret = new ArrayList<String>();

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User myUser = (User) iter.next();
            Collection myGroups = myUser.getGroups();

            for (Iterator iterator = myGroups.iterator(); iterator.hasNext();) {
                String myGroup = (String) iterator.next();

                if (!ret.contains(myGroup)) {
                    ret.add(myGroup);
                }
            }

            if (!ret.contains(myUser.getGroup())) {
                ret.add(myUser.getGroup());
            }
        }

        return ret;
    }

    /**
     * Get all user names in the system.
     */
    public Collection getAllUsers() throws UserFileException {
        ArrayList<AbstractUser> users = new ArrayList<AbstractUser>();
        String[] userpaths = getUserpathFile().list();

        for (int i = 0; i < userpaths.length; i++) {
            String userpath = userpaths[i];

            if (!userpath.endsWith(".xml")) {
                continue;
            }

            String username = userpath.substring(0,
                    userpath.length() - ".xml".length());

            try {
                users.add((AbstractUser) getUserByNameUnchecked(username));

                // throws IOException
            } catch (NoSuchUserException e) {
            } // continue
        }

        return users;
    }

    public Collection getAllUsersByGroup(String group)
        throws UserFileException {
        Collection<User> c = new ArrayList<User>();

        for (Iterator iter = getAllUsers().iterator(); iter.hasNext();) {
            User user = (User) iter.next();

            if (user.isMemberOf(group)) {
                c.add(user);
            }
        }

        return c;
    }

    //TODO garbage collected Map of users.
    public User getUserByNameIncludeDeleted(String username)
    	throws NoSuchUserException, UserFileException {
        User user = getUserByNameUnchecked(username);
        user.reset(_connManager);
        return user;
    }

    public User getUserByName(String username)
        throws NoSuchUserException, UserFileException {
        User user = getUserByNameIncludeDeleted(username);

        if (user.isDeleted()) {
            throw new NoSuchUserException(user.getName() + " is deleted");
        }

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
        _users.remove(user.getName());
    }

    protected void rename(User oldUser, String newUsername)
        throws UserExistsException, UserFileException {
        if (!_users.contains(newUsername)) {
            try {
                getUserByNameUnchecked(newUsername);
            } catch (NoSuchUserException e) {
                _users.remove(oldUser.getName());
                _users.put(newUsername, oldUser);
                return;
            }
        }

        throw new UserExistsException("user " + newUsername + " exists");
    }


    public void saveAll() throws UserFileException {
        logger.info("Saving userfiles");
        for(User user : _users.values()) {
            user.commit();
        }
    }
}
