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

import java.util.List;

import net.sf.drftpd.DuplicateElementException;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;


/**
 * @author mog
 * @version $Id$
 */
public abstract class User implements Entity {
    public abstract UserManager getUserManager();

    public abstract KeyedMap<Key, Object> getKeyedMap();
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

    public abstract String getGroup();

    public abstract List getGroups();

    public abstract HostMaskCollection getHostMaskCollection();

    /**
     * Returns the idleTime.
     *
     * @return int
     */
    public abstract int getIdleTime();

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
     * User logout
     */
    public abstract void logout();

    public abstract void purge();

    public abstract void removeIpMask(String mask) throws NoSuchFieldException;

    public abstract void removeSecondaryGroup(String group)
        throws NoSuchFieldException;

    public abstract void rename(String username)
        throws UserExistsException, UserFileException;

    public abstract void reset(GlobalContext manager)
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

    /**
     * Sets the idleTime.
     *
     * @param idleTime
     *            The idleTime to set
     */
    public abstract void setIdleTime(int idleTime);

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

	public abstract void setLastReset(long lastReset);

	public abstract long getLastReset();

	public abstract void setMaxSimUp(int maxup);
	
	public abstract void setMaxSimDown(int maxdown);

	public abstract int getMaxSimDown();

	public abstract int getMaxSimUp();
}
