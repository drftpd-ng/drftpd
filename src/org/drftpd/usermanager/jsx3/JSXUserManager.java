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
package org.drftpd.usermanager.jsx3;

import JSX.ObjIn;

import net.sf.drftpd.FatalException;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.master.ConnectionManager;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: JSXUserManager.java 806 2004-11-17 22:29:13Z mog $
 */
public class JSXUserManager extends AbstractUserManager {
    private static final Logger logger = Logger.getLogger(JSXUserManager.class.getName());
    private ConnectionManager _connManager;
    private String _userpath = "users/jsx2/";
    private File _userpathFile = new File(_userpath);

    public JSXUserManager() throws UserFileException {
        this(true);
    }

    public JSXUserManager(boolean createIfNoUser) throws UserFileException {
        if (!_userpathFile.exists() && !_userpathFile.mkdirs()) {
            throw new UserFileException(new IOException(
                    "Error creating folders: " + _userpathFile));
        }

        if (createIfNoUser) {
            String[] userfilenames = _userpathFile.list();
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

    public User createUser(String username) {
        JSXUser user = new JSXUser(this, username);

        return user;
    }

    public void delete(String username) {
        getUserFile(username).delete();
    }

    public Collection getAllUsers() throws UserFileException {
        ArrayList users = new ArrayList();
        String[] userpaths = _userpathFile.list();

        for (int i = 0; i < userpaths.length; i++) {
            String userpath = userpaths[i];

            if (!userpath.endsWith(".xml")) {
                continue;
            }

            String username = userpath.substring(0,
                    userpath.length() - ".xml".length());

            try {
                users.add((JSXUser) getUserByNameUnchecked(username));

                // throws IOException
            } catch (NoSuchUserException e) {
            } // continue
        }

        return users;
    }

    public User getUserByNameUnchecked(String username)
        throws NoSuchUserException, UserFileException {
        try {
            JSXUser user = (JSXUser) _users.get(username);

            if (user != null) {
                return user;
            }

            ObjIn in;

            try {
                in = new ObjIn(new FileReader(getUserFile(username)));
            } catch (FileNotFoundException ex) {
                throw new NoSuchUserException("No such user");
            }

            try {
                user = (JSXUser) in.readObject();

                //throws RuntimeException
                user.setUserManager(this);
                _users.put(user.getUsername(), user);
                user.reset(_connManager);

                return user;
            } catch (ClassNotFoundException e) {
                throw new FatalException(e);
            }
        } catch (Throwable ex) {
            if (ex instanceof NoSuchUserException) {
                throw (NoSuchUserException) ex;
            }

            throw new UserFileException("Error loading " + username, ex);
        }
    }

    protected File getUserFile(String username) {
        return new File(_userpath + username + ".xml");
    }

    public void saveAll() throws UserFileException {
        logger.log(Level.INFO, "Saving userfiles");

        for (Iterator iter = _users.values().iterator(); iter.hasNext();) {
            Object obj = iter.next();

            if (!(obj instanceof JSXUser)) {
                throw new ClassCastException("Only accepts JSXUser objects");
            }

            JSXUser user = (JSXUser) obj;
            user.commit();
        }
    }
}
