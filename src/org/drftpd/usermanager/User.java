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

import net.sf.drftpd.DuplicateElementException;

import java.util.List;
import java.util.Map;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.master.ConnectionManager;


/**
 * @author mog
 * @version $Id$
 */
public abstract class User implements Entity {
    public final float getObjectFloat(Key key) {
    	return getKeyedMap().getObjectFloat(key);
    }

    public abstract UserManager getUserManager();

    public final Map<Key, Object> getAllObjects() {
    	return getKeyedMap().getAllObjects();
    }

    public final void putAllObjects(KeyedMap m) {
    	getKeyedMap().setAllObjects(m);
    }

    public abstract KeyedMap getKeyedMap();
    public abstract void addAllMasks(HostMaskCollection hostMaskCollection);

    public abstract void addIPMask(String mask)
        throws DuplicateElementException;

    public abstract void addSecondaryGroup(String group)
        throws DuplicateElementException;

    public abstract boolean checkPassword(String password);

    /**
     * Commit changes.
     *
     * @throws UserFileException
     *             if an error saving the userfile occured.
     */
    public abstract void commit() throws UserFileException;

    public abstract long getCredits();

    public abstract long getDownloadedBytes();

    public abstract long getDownloadedBytesDay();

    public abstract long getDownloadedBytesForTrialPeriod(int period);

    public abstract long getDownloadedBytesMonth();

    public abstract long getDownloadedBytesWeek();

    public abstract int getDownloadedFiles();

    public abstract int getDownloadedFilesDay();

    public abstract int getDownloadedFilesForTrialPeriod(int i);

    public abstract int getDownloadedFilesMonth();

    public abstract int getDownloadedFilesWeek();

    public abstract long getDownloadedTime();

    public abstract long getDownloadedTimeForTrialPeriod(int i);

    public abstract short getGroupLeechSlots();

    public abstract String getGroup();

    public abstract List getGroups();

    public abstract short getGroupSlots();

    public abstract HostMaskCollection getHostMaskCollection();

    /**
     * Returns the idleTime.
     *
     * @return long
     */
    public abstract int getIdleTime();

    /**
     * Get last access time
     */
    public abstract long getLastAccessTime();

    public abstract long getLastReset();

    /**
     * Returns the logins.
     *
     * @return int
     */
    public abstract int getLogins();

    /**
     * Returns the maxLogins.
     *
     * @return int
     */
    public abstract int getMaxLogins();

    /**
     * Returns the maxLoginsPerIP.
     *
     * @return int
     */
    public abstract int getMaxLoginsPerIP();

    //    /**
    //     * Returns the nukedBytes.
    //     *
    //     * @return long
    //     */
    //    public abstract long getNukedBytes();
    //    public abstract int getRacesLost();
    //
    //    public abstract int getRacesParticipated();
    //    /**
    //     * Returns the downloadedSecondsWeek.
    //     *
    //     * @return int
    //     */
    //    public abstract int getRacesWon();

    /**
     * Returns the ratio.
     *
     * @return float
     */

    //public abstract float getRatio();
    //    public abstract int getRequests();
    //
    //    public abstract int getRequestsFilled();
    //    /**
    //     * Returns the nuked.
    //     *
    //     * @return int
    //     */
    //    public abstract int getTimesNuked();

    /**
     * Returns the uploadedBytes.
     *
     * @return long
     */
    public abstract long getUploadedBytes();

    /**
     * Returns the uploadedBytesDay.
     *
     * @return long
     */
    public abstract long getUploadedBytesDay();

    public abstract long getUploadedBytesForTrialPeriod(int period);

    /**
     * Returns the uploadedBytesMonth.
     *
     * @return long
     */
    public abstract long getUploadedBytesMonth();

    /**
     * Returns the uploadedBytesWeek.
     *
     * @return long
     */
    public abstract long getUploadedBytesWeek();

    /**
     * Returns the uploadedFiles.
     *
     * @return int
     */
    public abstract int getUploadedFiles();

    /**
     * Returns the uploadedFilesDay.
     *
     * @return int
     */
    public abstract int getUploadedFilesDay();

    public abstract int getUploadedFilesForTrialPeriod(int period);

    /**
     * Returns the uploadedFilesMonth.
     *
     * @return int
     */
    public abstract int getUploadedFilesMonth();

    /**
     * Returns the uploadedFilesWeek.
     *
     * @return int
     */
    public abstract int getUploadedFilesWeek();

    public abstract long getUploadedTime();

    public abstract long getUploadedTimeForTrialPeriod(int i);

    public abstract String getName();

    public abstract long getWeeklyAllotment();

    public abstract boolean isAdmin();

    /**
     * Returns the deleted.
     *
     * @return boolean
     */
    public abstract boolean isDeleted();

    public abstract boolean isExempt();

    public abstract boolean isGroupAdmin();

    public abstract boolean isMemberOf(String group);

    /**
     * Returns the nuker.
     *
     * @return boolean
     */
    public abstract boolean isNuker();

    /**
     * User login.
     */
    public abstract void login();

    /**
     * User logout
     */
    public abstract void logout();

    public abstract void purge();

    public abstract void removeIpMask(String mask) throws NoSuchFieldException;

    public abstract void removeSecondaryGroup(String group)
        throws NoSuchFieldException;

    public abstract void rename(String username)
        throws UserExistsException, UserFileException;

    public abstract void reset(ConnectionManager manager)
        throws UserFileException;

    /**
     * Sets the credits.
     *
     * @param credits
     *            The credits to set
     */
    public abstract void setCredits(long credits);

    /**
     * Sets the deleted.
     *
     * @param deleted
     *            The deleted to set
     */
    public abstract void setDeleted(boolean deleted);

    public abstract void setDownloadedBytes(long bytes);

    public abstract void setDownloadedBytesDay(long bytes);

    public abstract void setDownloadedBytesForTrialPeriod(int period, long bytes);

    public abstract void setDownloadedBytesMonth(long bytes);

    public abstract void setDownloadedBytesWeek(long bytes);

    public abstract void setDownloadedFiles(int files);

    public abstract void setDownloadedFilesDay(int files);

    public abstract void setDownloadedFilesForTrialPeriod(int period, int files);

    public abstract void setDownloadedFilesMonth(int files);

    public abstract void setDownloadedFilesWeek(int files);

    public abstract void setDownloadedTime(long millis);

    public abstract void setDownloadedTimeDay(long millis);

    public abstract void setDownloadedTimeForTrialPeriod(int i, long millis);

    public abstract void setDownloadedTimeMonth(long millis);

    public abstract void setDownloadedTimeWeek(long millis);

    public abstract void setGroup(String group);

    public abstract void setGroupLeechSlots(short s);

    public abstract void setGroupSlots(short s);

    /**
     * Sets the idleTime.
     *
     * @param idleTime
     *            The idleTime to set
     */
    public abstract void setIdleTime(int idleTime);

    /**
     * Sets the lastAccessTime.
     *
     * @param lastAccessTime
     *            The lastAccessTime to set
     */
    public abstract void setLastAccessTime(long lastAccessTime);

    /**
     * Sets the logins.
     *
     * @param logins
     *            The logins to set
     */
    public abstract void setLogins(int logins);

    /**
     * Sets the maxLogins.
     *
     * @param maxLogins
     *            The maxLogins to set
     */
    public abstract void setMaxLogins(int maxLogins);

    /**
     * Sets the maxLoginsPerIP.
     *
     * @param maxLoginsPerIP
     *            The maxLoginsPerIP to set
     */
    public abstract void setMaxLoginsPerIP(int maxLoginsPerIP);

    //    /**
    //     * Sets the nukedBytes.
    //     *
    //     * @param nukedBytes
    //     *            The nukedBytes to set
    //     */
    //    public abstract void setNukedBytes(long nukedBytes);
    public abstract void setPassword(String password);

    //    public abstract void setTimesNuked(int nuked);
    public abstract void setUploadedBytes(long bytes);

    public abstract void setUploadedBytesDay(long bytes);

    public abstract void setUploadedBytesForTrialPeriod(int i, long l);

    public abstract void setUploadedBytesMonth(long bytes);

    public abstract void setUploadedBytesWeek(long bytes);

    public abstract void setUploadedFiles(int files);

    public abstract void setUploadedFilesDay(int files);

    public abstract void setUploadedFilesForTrialPeriod(int period, int files);

    public abstract void setUploadedFilesMonth(int files);

    public abstract void setUploadedFilesWeek(int files);

    public abstract void setUploadedTime(long millis);

    public abstract void setUploadedTimeDay(long millis);

    public abstract void setUploadedTimeForTrialPeriod(int i, long millis);

    public abstract void setUploadedTimeMonth(long millis);

    public abstract void setUploadedTimeWeek(long millis);

    public abstract void setUploadedTime(int millis);

    public abstract void setUploadedTimeDay(int millis);

    public abstract void setUploadedTimeMonth(int millis);

    public abstract void setUploadedTimeWeek(int millis);

    public abstract void setWeeklyAllotment(long weeklyAllotment);

    public abstract void toggleGroup(String string);

    public abstract void updateCredits(long credits);

    public abstract void updateDownloadedBytes(long bytes);

    public abstract void updateDownloadedFiles(int i);

    public abstract void updateDownloadedTime(long millis);

    /**
     * Hit user - update last access time
     */
    public abstract void updateLastAccessTime();

    public abstract void updateUploadedBytes(long bytes);

    public abstract void updateUploadedFiles(int i);

    public abstract void updateUploadedTime(long millis);

    public final Object getObject(Key key) throws KeyNotFoundException {
    	return getKeyedMap().getObject(key);
    }

    public final Object getObject(Key key, Object def) {
    	return getKeyedMap().getObject(key, def);
    }

    public final void incrementObjectLong(Key key, long value) {
    	getKeyedMap().incrementObjectLong(key, value);
    }

    public final void incrementObjectLong(Key nuked) {
		getKeyedMap().incrementObjectLong(nuked);
	}

    public final String getObjectString(Key key) {
    	return getKeyedMap().getObjectString(key);
    }

    public final int getObjectInt(Key key) {
    	return getKeyedMap().getObjectInt(key);
    }

    public final void incrementObjectInt(Key key, int i) {
    	getKeyedMap().incrementObjectInt(key, i);
    }

    public final long getObjectLong(Key key) {
    	return getKeyedMap().getObjectLong(key);
    }

	public final boolean getObjectBoolean(Key key) {
		return getKeyedMap().getObjectBoolean(key);
	}
	private final void putObject(Key k, int v) {
		getKeyedMap().setObject(k, v);
	}
}