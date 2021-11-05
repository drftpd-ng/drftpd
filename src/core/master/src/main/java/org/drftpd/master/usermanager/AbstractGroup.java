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
package org.drftpd.master.usermanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.master.commands.usermanagement.GroupManagement;
import org.drftpd.common.dynamicdata.DynamicConfigHelper;
import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.master.vfs.Commitable;

import java.io.IOException;
import java.util.*;

import static org.drftpd.common.dynamicdata.DynamicConfigHelper.configHelper;

/**
 * Implements basic functionality for the Group interface.
 *
 * @author mikevg
 * @version $Id$
 */
public abstract class AbstractGroup extends Group implements Commitable {
    private static final Logger logger = LogManager.getLogger(AbstractUser.class);
    private final Map<Key<?>, ConfigElement<?>> _configs = new HashMap<>();
    private ArrayList<String> _admins = new ArrayList<>();
    private String _groupname;

    public AbstractGroup(String groupname) {
        checkValidGroupName(groupname);
        _groupname = groupname;
        DynamicConfigHelper groupMap = configHelper(_configs);
        groupMap.setDate(GroupManagement.CREATED, new Date(System.currentTimeMillis()));
    }

    public static void checkValidGroupName(String group) {
        if ((group.indexOf(' ') != -1) || (group.indexOf(';') != -1)) {
            throw new IllegalArgumentException("Groups cannot contain illegal characters");
        }
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof Group))
            return false;

        return ((Group) obj).getName().equals(getName());
    }

    /**
     * To avoid casting to AbstractUserManager
     */
    public abstract AbstractUserManager getAbstractUserManager();

    public List<User> getAdmins() {
        List<User> admins = new ArrayList<>(_admins.size());
        for (String user : _admins) {
            try {
                admins.add(getUserManager().getUserByName(user));
            } catch (NoSuchUserException | UserFileException e) {
                logger.error("Unable to get user entity for user name {}", user);
            }
        }
        return admins;
    }

    public void setAdmins(List<User> admins) {
        _admins = new ArrayList<>(admins.size());
        for (User u : admins) {
            _admins.add(u.getName());
        }
    }

    public void addAdmin(User u) throws DuplicateElementException {
        if (_admins.contains(u.getName())) {
            throw new DuplicateElementException("User is already an admin for that group");
        }
        _admins.add(u.getName());
    }

    public void removeAdmin(User u) throws NoSuchFieldException {
        if (!_admins.remove(u.getName())) {
            throw new NoSuchFieldException("User is not an admin for that group");
        }
    }

    public boolean isAdmin(User u) {
        return _admins.contains(u.getName());
    }

    public Map<Key<?>, ConfigElement<?>> getConfigurations() {
        return _configs;
    }

    public String getName() {
        return _groupname;
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public void rename(String groupname) throws GroupExistsException, GroupFileException {
        getAbstractUserManager().renameGroup(this, groupname); // throws ObjectExistsException
        getAbstractUserManager().deleteGroup(this.getName());
        _groupname = groupname;
        commit(); // throws IOException
    }

    public String toString() {
        return _groupname;
    }

    public abstract void writeToDisk() throws IOException;

    public DynamicConfigHelper getConfigHelper() {
        return configHelper(_configs);
    }

    public float getMinRatio() {
        return getConfigHelper().get(GroupManagement.MINRATIO, 3F);
    }

    public void setMinRatio(float minRatio) {
        getConfigHelper().setFloat(GroupManagement.MINRATIO, minRatio);
    }

    public float getMaxRatio() {
        return getConfigHelper().get(GroupManagement.MAXRATIO, 3F);
    }

    public void setMaxRatio(float maxRatio) {
        getConfigHelper().setFloat(GroupManagement.MAXRATIO, maxRatio);
    }

    public double getGroupSlots() { return getConfigHelper().get(GroupManagement.GROUPSLOTS, 0); }

    public void setGroupSlots(int groupslots) { getConfigHelper().setInt(GroupManagement.GROUPSLOTS, groupslots); }

    public double getLeechSlots() { return getConfigHelper().get(GroupManagement.LEECHSLOTS, 0); }

    public void setLeechSlots(int leechslots) { getConfigHelper().setInt(GroupManagement.LEECHSLOTS, leechslots); }

    public Date getCreated() { return getConfigHelper().get(GroupManagement.CREATED, new Date()); }

    public void setCreated(Date created) { getConfigHelper().setDate(GroupManagement.CREATED, created); }

}
