/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.usermanager.javabeans;

import com.cedarsoftware.util.io.JsonReader;
import com.google.gson.Gson;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.usermanager.*;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.*;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * This is a new usermanager that after recommendation from captain- serializes
 * user data using javabeans XMLSerialize.
 *
 * @author mog
 * @author mikevg
 * @version $Id$
 */
public class BeanUserManager extends AbstractUserManager {

    protected static final Logger logger = LogManager.getLogger(BeanUserManager.class);
    private static final String _userpath = "userdata/users/javabeans/";
    private static final String _grouppath = "userdata/groups/javabeans/";
    private static final File _userpathFile = new File(_userpath);
    private static final File _grouppathFile = new File(_grouppath);

    /**
     * Testing routine.
     *
     * @param args Command line arguments
     * @throws UserFileException   There is a problem with the user file
     * @throws GroupFileException  There is a problem with the user file
     * @throws FileExistsException The files already exist
     */
    public static void main(String[] args) throws UserFileException, GroupFileException, FileExistsException {
        BeanUserManager bu = new BeanUserManager();
        Group g = bu.createGroup("drftpd");
        g.commit();
        User u = bu.createUser("drftpd");
        u.setGroup(g);
        u.commit();
    }

    /**
     * Creates a user named 'username' and adds it to the users map.
     */
    protected synchronized User createUserImpl(String username) {
        BeanUser buser = new BeanUser(this, username);
        _users.put(username, new SoftReference<>(buser));
        return buser;
    }

    /**
     * Creates a group named 'groupname' and adds it to the groups map.
     */
    protected synchronized Group createGroupImpl(String groupname) {
        BeanGroup bgroup = new BeanGroup(this, groupname);
        _groups.put(groupname, new SoftReference<>(bgroup));
        return bgroup;
    }

    /**
     * UserManager initializer.
     *
     * @throws UserFileException  If the user files locations has an error
     * @throws GroupFileException If the group files locations has an error
     */
    public void init() throws UserFileException, GroupFileException {
        super.init();

        File userpath = getUserpathFile();
        File grouppath = getGrouppathFile();

        if (!userpath.exists() && !userpath.mkdirs()) {
            throw new UserFileException(new IOException("Error creating directories: " + getUserpathFile()));
        }
        if (!grouppath.exists() && !grouppath.mkdirs()) {
            throw new GroupFileException(new IOException("Error creating directories: " + getGrouppathFile()));
        }

        _users = new HashMap<>();
        _groups = new HashMap<>();

        logger.debug("Creating users map...");
        for (String filename : Objects.requireNonNull(userpath.list())) {
            if (filename.endsWith(".xml") || filename.endsWith(".json")) {
                String username = filename.substring(0, filename.lastIndexOf('.'));
                _users.put(username, null); // the user exists, loading it is useless right now.
            }
        }

        logger.debug("Creating groups map...");
        for (String filename : Objects.requireNonNull(grouppath.list())) {
            if (filename.endsWith(".xml") || filename.endsWith(".json")) {
                String groupname = filename.substring(0, filename.lastIndexOf('.'));
                _groups.put(groupname, null); // the group exists, loading it is useless right now.
            }
        }

        // This also creates the group
        if (_users.size() == 0) {
            createSiteopUser();
        }
    }

    /**
     * Tries to find a user in the Map that matches the 'username'.
     * This method does not care about if the user exists or not,
     * it simply tries to find it.
     *
     * @throws NoSuchUserException, if there's no such user w/ this Username.
     * @throws UserFileException,   if an error (i/o) occured while loading data.
     */
    public User getUserByNameUnchecked(String username) throws NoSuchUserException, UserFileException {
        try {
            return getUserFromSoftReference(username);
        } catch (Exception ex) {
            if (ex instanceof NoSuchUserException) {
                throw (NoSuchUserException) ex;
            }
            throw new UserFileException("Error loading " + username, ex);
        }
    }

    /**
     * Tries to find a group in the Map that matches the 'groupname'.
     * This method does not care about if the group exists or not,
     * it simply tries to find it.
     *
     * @throws NoSuchGroupException, if there's no such group w/ this Groupname.
     * @throws GroupFileException,   if an error (i/o) occured while loading data.
     */
    public Group getGroupByNameUnchecked(String groupname) throws NoSuchGroupException, GroupFileException {
        try {
            return getGroupFromSoftReference(groupname);
        } catch (Exception ex) {
            if (ex instanceof NoSuchGroupException) {
                throw (NoSuchGroupException) ex;
            }
            throw new GroupFileException("Error loading " + groupname, ex);
        }
    }

    /**
     * Lowest level method for loading a Group object.
     *
     * @param groupName The group name to load
     * @throws GroupFileException, if an error (i/o) occured while loading data.
     */
    protected Group loadGroup(String groupName) throws GroupFileException {
        Gson gson = new Gson();
        try {
            logger.debug("Loading '{}' Json group data from disk.", groupName);
            File userFile = getGroupFile(groupName);
            FileReader fileReader = new FileReader(userFile);
            BeanGroup group = gson.fromJson(fileReader, BeanGroup.class);
            group.setUserManager(this);
            return group;
        } catch (Exception e) {
            throw new GroupFileException("Error loading " + groupName, e);
        }
    }

    /**
     * Lowest level method for loading a User object.
     *
     * @param userName The user name to load
     * @throws UserFileException, if an error (i/o) occured while loading data.
     */
    protected User loadUser(String userName) throws UserFileException {
        Gson gson = new Gson();
        try {
            logger.debug("Loading '{}' Json user data from disk.", userName);
            File userFile = getUserFile(userName);
            FileReader fileReader = new FileReader(userFile);
            BeanUser user = gson.fromJson(fileReader, BeanUser.class);
            user.setUserManager(this);
            return user;
        } catch (Exception e) {
            throw new UserFileException("Error loading " + userName, e);
        }

    }

    /**
     * This methods fetches the SoftReference from the users map
     * and checks if it still holds a reference to a User object.<br>
     * If it does return the object, if not it tries to load the
     * User data from the disk and return it.
     *
     * @param name, the username.
     * @return a User object.
     * @throws NoSuchUserException, if no such file containing use data was found,
     *                              so the user does not exist.
     * @throws UserFileException,   if an error (i/o) occurs during the load.
     */
    private synchronized User getUserFromSoftReference(String name)
            throws NoSuchUserException, UserFileException {
        if (!_users.containsKey(name)) {
            throw new NoSuchUserException("No such user found: " + name);
        }
        SoftReference<User> sf = _users.get(name);
        User u = null;
        if (sf != null) {
            u = sf.get();
        }
        if (u == null) {
            // user object was garbage collected or was never loaded
            u = loadUser(name);
            _users.put(name, new SoftReference<>(u));
        }
        return u;
    }

    /**
     * This methods fetches the SoftReference from the groups map
     * and checks if it still holds a reference to a Group object.<br>
     * If it does return the object, if not it tries to load the
     * Group data from the disk and return it.
     *
     * @param name, the groupname.
     * @return a Group object.
     * @throws NoSuchGroupException, if no such file containing use data was found,
     *                               so the group does not exist.
     * @throws GroupFileException,   if an error (i/o) occurs during the load.
     */
    private synchronized Group getGroupFromSoftReference(String name)
            throws NoSuchGroupException, GroupFileException {
        if (!_groups.containsKey(name)) {
            throw new NoSuchGroupException("No such group found: " + name);
        }
        SoftReference<Group> sf = _groups.get(name);
        Group g = null;
        if (sf != null) {
            g = sf.get();
        }
        if (g == null) {
            // group object was garbage collected or was never loaded
            g = loadGroup(name);
            _groups.put(name, new SoftReference<>(g));
        }
        return g;
    }

    /**
     * List all users.<br>
     * If some of the User objects are not loaded, they will be loaded and
     * saved in the memory for future usage, but they still subject to
     * GarbageColector.
     */
    public synchronized Collection<User> getAllUsers() {
        ArrayList<User> users = new ArrayList<>(_users.size());
        for (Iterator<String> iter = _users.keySet().iterator(); iter.hasNext(); ) {
            String name = iter.next();
            try {
                User u = getUserFromSoftReference(name);
                users.add(u);
                _users.put(name, new SoftReference<>(u));
            } catch (NoSuchUserException e) {
                logger.error("{} data wasnt found in the disk! How come the user is in the Map and does not have a userfile?! Deleting it.", name);
                iter.remove();
                // nothing else to do, user wasnt loaded properly.
            } catch (UserFileException e) {
                logger.error("Error loading {}", name, e);
                // nothing else to do, an error ocurred while loading data.
            }
        }
        return users;
    }

    /**
     * List all groups.<br>
     * If some of the Group objects are not loaded, they will be loaded and
     * saved in the memory for future usage, but they still subject to
     * GarbageColector.
     */
    public synchronized Collection<Group> getAllGroups() {
        ArrayList<Group> groups = new ArrayList<>(_groups.size());
        for (Iterator<String> iter = _groups.keySet().iterator(); iter.hasNext(); ) {
            String name = iter.next();
            try {
                Group g = getGroupFromSoftReference(name);
                groups.add(g);
                _groups.put(name, new SoftReference<>(g));
            } catch (NoSuchGroupException e) {
                logger.error("{} data wasnt found in the disk! How come the group is in the Map and does not have a groupfile?! Deleting it.", name);
                iter.remove();
                // nothing else to do, user wasnt loaded properly.
            } catch (GroupFileException e) {
                logger.error("Error loading {}", name, e);
                // nothing else to do, an error ocurred while loading data.
            }
        }
        return groups;
    }

    protected final File getUserpathFile() {
        return _userpathFile;
    }

    protected final File getGrouppathFile() {
        return _grouppathFile;
    }

    protected final File getUserFile(String username) {
        return new File(_userpath + username + ".json");
    }

    protected final File getGroupFile(String groupname) {
        return new File(_grouppath + groupname + ".json");
    }
}
