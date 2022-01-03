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

import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.util.HostMaskCollection;
import org.drftpd.common.dynamicdata.DynamicConfigHelper;
import org.drftpd.master.stats.ExtendedTimedStats;

import java.util.List;

/**
 * @author mog
 * @version $Id$
 */
public abstract class User extends ExtendedTimedStats implements Entity {

    public abstract UserManager getUserManager();

    public abstract DynamicConfigHelper getConfigHelper();

    public abstract void addAllMasks(HostMaskCollection hostMaskCollection);

    public abstract void addIPMask(String mask) throws DuplicateElementException;

    public abstract void addSecondaryGroup(Group g) throws DuplicateElementException;

    public abstract boolean checkPassword(String password);

    /**
     * Commit changes.
     */
    public abstract void commit();

    public abstract double getCredits();

    /**
     * Sets the credits.
     *
     * @param credits The credits to set
     */
    public abstract void setCredits(double credits);

    public abstract Group getGroup();

    public abstract void setGroup(Group g);

    public abstract List<Group> getGroups();

    public abstract HostMaskCollection getHostMaskCollection();

    /**
     * Returns the idleTime.
     *
     * @return int
     */
    public abstract int getIdleTime();

    /**
     * Sets the idleTime.
     *
     * @param idleTime The idleTime to set
     */
    public abstract void setIdleTime(int idleTime);

    public abstract String getName();

    public abstract boolean isAdmin();

    /**
     * Returns the deleted.
     *
     * @return boolean
     */
    public abstract boolean isDeleted();

    /**
     * Sets the deleted.
     *
     * @param deleted The deleted to set
     */
    public abstract void setDeleted(boolean deleted);

    public abstract boolean isMemberOf(String group);

    public abstract void purge();

    public abstract void removeIpMask(String mask) throws NoSuchFieldException;

    public abstract void removeSecondaryGroup(Group g) throws NoSuchFieldException;

    public abstract void rename(String username) throws UserExistsException, UserFileException;

    public abstract void toggleGroup(Group g);

    public abstract void updateCredits(long credits);

    public abstract void setPassword(String password);

    public abstract void changePassword(String password);

    /**
     * Hit user - update last access time
     */
    public abstract void updateLastAccessTime();

    public abstract long getLastReset();

    public abstract void setLastReset(long lastReset);

    public abstract double getMaxSimDown();

    public abstract void setMaxSimDown(int maxdown);

    public abstract double getMaxSimUp();

    public abstract void setMaxSimUp(int maxup);
}
