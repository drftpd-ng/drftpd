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

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.stats.ExtendedTimedStats;
import org.drftpd.util.HostMaskCollection;

import java.util.List;

/**
 * @author mog
 * @version $Id$
 */
public abstract class User extends ExtendedTimedStats implements Entity {
	public abstract UserManager getUserManager();

	public abstract KeyedMap<Key<?>, Object> getKeyedMap();

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
	public abstract void commit();

	public abstract long getCredits();

	public abstract String getGroup();

	public abstract List<String> getGroups();

	public abstract HostMaskCollection getHostMaskCollection();

	/**
	 * Returns the idleTime.
	 * 
	 * @return int
	 */
	public abstract int getIdleTime();

	// /**
	// * Returns the nukedBytes.
	// *
	// * @return long
	// */
	// public abstract long getNukedBytes();
	// public abstract int getRacesLost();
	//
	// public abstract int getRacesParticipated();
	// /**
	// * Returns the downloadedSecondsWeek.
	// *
	// * @return int
	// */
	// public abstract int getRacesWon();

	/**
	 * Returns the ratio.
	 * 
	 * @return float
	 */

	// public abstract float getRatio();
	// public abstract int getRequests();
	//
	// public abstract int getRequestsFilled();
	// /**
	// * Returns the nuked.
	// *
	// * @return int
	// */
	// public abstract int getTimesNuked();

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
	 * User logout
	 */
	public abstract void logout();

	public abstract void purge();

	public abstract void removeIpMask(String mask) throws NoSuchFieldException;

	public abstract void removeSecondaryGroup(String group)
			throws NoSuchFieldException;

	public abstract void rename(String username) throws UserExistsException,
			UserFileException;

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

	public abstract void setGroup(String group);
	
	public abstract void toggleGroup(String string);

	public abstract void updateCredits(long credits);

	/**
	 * Sets the idleTime.
	 * 
	 * @param idleTime
	 *            The idleTime to set
	 */
	public abstract void setIdleTime(int idleTime);

	public abstract void setPassword(String password);

	// public abstract void setTimesNuked(int nuked);
	
	/**
	 * Hit user - update last access time
	 */
	public abstract void updateLastAccessTime();

	public abstract void setLastReset(long lastReset);

	public abstract long getLastReset();

	public abstract void setMaxSimUp(int maxup);

	public abstract void setMaxSimDown(int maxdown);

	public abstract int getMaxSimDown();

	public abstract int getMaxSimUp();

	public abstract float getMinRatio();

	public abstract float getMaxRatio();

	public abstract void setMinRatio(float minRatio);

	public abstract void setMaxRatio(float MaxRatio);
}
