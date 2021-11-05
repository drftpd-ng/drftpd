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
import org.drftpd.common.util.HostMaskCollection;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.common.dynamicdata.element.ConfigDate;
import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.common.dynamicdata.DynamicConfigHelper;
import org.drftpd.common.dynamicdata.element.ConfigString;
import org.drftpd.master.event.UserEvent;
import org.drftpd.master.vfs.Commitable;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.IOException;
import java.util.*;

import static org.drftpd.common.dynamicdata.DynamicConfigHelper.configHelper;

/**
 * Implements basic functionality for the User interface.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @author mog
 * @version $Id$
 */
public abstract class AbstractUser extends User implements Commitable {
    private static final Logger logger = LogManager.getLogger(AbstractUser.class);
    private Map<Key<?>, ConfigElement<?>> _configs = new HashMap<>();
    /**
     * Protected for DummyUser b/c TrialTest
     */
    protected long _lastReset;
    private double _credits;
    private String _group = null;
    // We keep this String based, but for the outside world this always needs to be an object of type Group
    private List<String> _groups = new ArrayList<>();
    private HostMaskCollection _hostMasks = new HostMaskCollection();
    private int _idleTime = 0; // no limit
    private String _username;

    public DynamicConfigHelper getConfigHelper() {
        return configHelper(_configs);
    }

    public AbstractUser(String username) {
        checkValidUser(username);
        _username = username;
        DynamicConfigHelper helper = getConfigHelper();
        helper.setObject(UserManagement.CREATED, new ConfigDate(new Date()));
        helper.setObject(UserManagement.TAGLINE, new ConfigString("no tagline"));
    }

    public static void checkValidUser(String user) {
        if ((user.indexOf(' ') != -1) || (user.indexOf(';') != -1) || user.indexOf('!') != -1) {
            throw new IllegalArgumentException("Users cannot contain illegal characters");
        }
    }

    public void addAllMasks(HostMaskCollection hostMaskCollection) {
        getHostMaskCollection().addAllMasks(hostMaskCollection);
    }

    public void addIPMask(String mask) throws DuplicateElementException {
        getHostMaskCollection().addMask(mask);
    }

    public void addSecondaryGroup(Group g) throws DuplicateElementException {
        if (_groups.contains(g.getName())) {
            throw new DuplicateElementException("User is already a member of that group");
        }

        _groups.add(g.getName());
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof User))
            return false;

        return ((User) obj).getName().equals(getName());
    }

    /**
     * To avoid casting to AbstractUserManager
     */
    public abstract AbstractUserManager getAbstractUserManager();

    public double getCredits() {
        return _credits;
    }

    public void setCredits(double credits) {
        _credits = credits;
    }

    public Group getGroup() {
        Group g = null;
        try {
            g = getUserManager().getGroupByName(_group);
        } catch (NoSuchGroupException | GroupFileException e) {
            logger.error("Unable to get group entity for group name {}", _group);
        }
        return g;
    }

    public void setGroup(Group g) {
        _group = g.getName();
    }

    public void setConfigurations(Map<Key<?>, ConfigElement<?>> configurations) {
        this._configs = configurations;
    }

    public Map<Key<?>, ConfigElement<?>> getConfigurations() {
        return _configs;
    }

    public List<Group> getGroups() {
        List<Group> groups = new ArrayList<>();
        for (String group : _groups) {
            try {
                groups.add(getUserManager().getGroupByName(group));
            } catch (NoSuchGroupException | GroupFileException e) {
                logger.error("Unable to get group entity for group name {}", group);
            }
        }
        return groups;
    }

    public void setGroups(List<Group> groups) {
        _groups = new ArrayList<>(groups.size());
        for (Group g : groups) {
            _groups.add(g.getName());
        }
    }

    public HostMaskCollection getHostMaskCollection() {
        return _hostMasks;
    }

    public void setHostMaskCollection(HostMaskCollection masks) {
        _hostMasks = masks;
    }

    public int getIdleTime() {
        return _idleTime;
    }

    public void setIdleTime(int idleTime) {
        _idleTime = idleTime;
    }

    public long getLastReset() {
        return _lastReset;
    }

    public void setLastReset(long lastReset) {
        _lastReset = lastReset;
    }

    public String getName() {
        return _username;
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public boolean isAdmin() {
        return isMemberOf("siteop");
    }

    public boolean isDeleted() {
        return isMemberOf("deleted");
    }

    public void setDeleted(boolean deleted) {
        Group g;
        try {
            g = getUserManager().getGroupByName("deleted");
        } catch (NoSuchGroupException e) {
            // This should normally not happen, but this part needs to be changed anyway, so we silently allow this and create the group here
            try {
                g = getUserManager().createGroup("deleted");
            } catch (GroupFileException | FileExistsException ignored) {
                // File error...
                return;
            }
        } catch (GroupFileException ignored) {
            // File error...
            return;
        }
        if (g == null) {
            // Something is wrong above and we do not continue here
            return;
        }

        if (deleted) {
            // Remove this user as a group admin
            for (Group g2 : getGroups()) {
                if (g2.isAdmin(this)) {
                    try {
                        g2.removeAdmin(this);
                    } catch (NoSuchFieldException ignored) {
                    }
                }
            }
            try {
                addSecondaryGroup(g);
            } catch (DuplicateElementException ignored) {
            }
        } else {
            try {
                removeSecondaryGroup(g);
            } catch (NoSuchFieldException ignored) {
            }
        }
    }

    public boolean isMemberOf(String group) {
        if (getGroup() == null) {
            logger.error("Something is wrong with ({}) as the primary group has an issue.", getName());
            return false;
        }
        if (getGroup().getName().equals(group)) {
            return true;
        }

        // This can never be null, it can however return an empty Collection (not a problem)
        for (Group myGroup : getGroups()) {
            if (group.equals(myGroup.getName())) {
                return true;
            }
        }

        return false;
    }

    public void removeIpMask(String mask) throws NoSuchFieldException {
        if (!_hostMasks.removeMask(mask)) {
            throw new NoSuchFieldException("User has no such ip mask");
        }
    }

    public void removeSecondaryGroup(Group group) throws NoSuchFieldException {
        if (!_groups.remove(group.getName())) {
            throw new NoSuchFieldException("User is not a member of that group");
        }
    }

    public void rename(String username) throws UserExistsException, UserFileException {
        getAbstractUserManager().renameUser(this, username); // throws ObjectExistsException
        getAbstractUserManager().deleteUser(this.getName());
        _username = username;
        commit(); // throws IOException
    }

    public void resetDay(Date resetDate) {
        GlobalContext.getEventService().publish(new UserEvent(this, "RESETDAY", resetDate.getTime()));
        super.resetDay(resetDate);
        super.resetHour(resetDate);
        logger.info("Reset daily stats for {}", getName());
    }

    public void resetMonth(Date resetDate) {
        GlobalContext.getEventService().publish(new UserEvent(this, "RESETMONTH", resetDate.getTime()));
        super.resetMonth(resetDate);
        super.resetDay(resetDate);
        super.resetHour(resetDate);
        logger.info("Reset monthly stats for {}", getName());
    }

    public void resetWeek(Date resetDate) {
        GlobalContext.getEventService().publish(new UserEvent(this, "RESETWEEK", resetDate.getTime()));
        super.resetWeek(resetDate);
        Long credits = getConfigHelper().get(UserManagement.WKLYALLOTMENT, 0L);
        if (credits > 0) {
            setCredits(credits);
        }
        logger.info("Reset weekly stats for {}", getName());
    }

    public void resetHour(Date resetDate) {
        // do nothing for now
        super.resetHour(resetDate);
    }

    public void resetYear(Date resetDate) {
        GlobalContext.getEventService().publish(new UserEvent(this, "RESETYEAR", resetDate.getTime()));
        super.resetYear(resetDate);
        super.resetMonth(resetDate);
        super.resetDay(resetDate);
        super.resetHour(resetDate);
        logger.info("Reset Yearly stats for {}", getName());
    }

    public void toggleGroup(Group g) {
        if (isMemberOf(g.getName())) {
            try {
                removeSecondaryGroup(g);
            } catch (NoSuchFieldException e) {
                logger.error("isMemberOf() said we were in the group", e);
            }
        } else {
            try {
                addSecondaryGroup(g);
            } catch (DuplicateElementException e) {
                logger.error("isMemberOf() said we weren't in the group", e);
            }
        }
    }

    public String toString() {
        return _username;
    }

    public void updateCredits(long credits) {
        _credits += credits;
    }

    /**
     * Hit user - update last access time
     */
    public void updateLastAccessTime() {
        DynamicConfigHelper helper = getConfigHelper();
        helper.setDate(UserManagement.LASTSEEN, new Date(System.currentTimeMillis()));
    }

    public double getMaxSimUp() {
        return getConfigHelper().get(UserManagement.MAXSIMUP, 1);
    }

    public void setMaxSimUp(int maxSimUp) {
        getConfigHelper().setInt(UserManagement.MAXSIMUP, maxSimUp);
    }

    public double getMaxSimDown() {
        return getConfigHelper().get(UserManagement.MAXSIMDN, 1);
    }

    public void setMaxSimDown(int maxSimDown) {
        getConfigHelper().setInt(UserManagement.MAXSIMDN, maxSimDown);
    }

    public abstract void writeToDisk() throws IOException;
}
