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
package org.drftpd.master.tests;

import org.drftpd.master.usermanager.*;

import java.io.File;
import java.lang.ref.SoftReference;
import java.util.Collection;
import java.util.Collections;


/**
 * @author mog
 * @version $Id$
 */
public class DummyUserManager extends AbstractUserManager {
    private User _user;
    private Group _group;

    public DummyUserManager() {
        super();
    }

    public User createUserImpl(String username) {
        throw new UnsupportedOperationException();
    }

    public Group createGroupImpl(String groupname) {
        throw new UnsupportedOperationException();
    }

    public Group createGroup(String groupname) throws GroupFileException {
        DummyGroup g = new DummyGroup(groupname, this);
        addGroup(g);

        return g;
    }

    public User createUser(String username) throws UserFileException {
        DummyUser u = new DummyUser(username, this);
        addUser(u);

        return u;
    }

    public Collection<Group> getAllGroups() {
        throw new UnsupportedOperationException();
    }

    public synchronized void addUser(User user) {
        _users.put(user.getName(), new SoftReference<>(user));
    }

    public synchronized void addGroup(Group group) {
        _groups.put(group.getName(), new SoftReference<>(group));
    }

    public User getUserByNameUnchecked(String username)
            throws NoSuchUserException, UserFileException {
        return _user;
    }

    public Group getGroupByNameUnchecked(String groupname)
            throws NoSuchGroupException, GroupFileException {
        return _group;
    }

    public User getUserByName(String username) {
        return _user;
    }

    public void saveAll() {
        throw new UnsupportedOperationException();
    }

    public void setUser(User user) {
        _user = user;
    }

    public Collection<User> getAllUsers() {
        return Collections.singletonList(_user);
    }

    protected File getGrouppathFile() {
        throw new UnsupportedOperationException();
    }

    protected File getGroupFile(String groupname) {
        throw new UnsupportedOperationException();
    }

    protected File getUserpathFile() {
        throw new UnsupportedOperationException();
    }

    protected File getUserFile(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void init() throws UserFileException {

    }
}
