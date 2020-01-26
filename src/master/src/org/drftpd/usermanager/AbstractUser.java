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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.event.UserEvent;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.master.Commitable;
import org.drftpd.util.HostMaskCollection;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Implements basic functionality for the User interface.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @author mog
 * @version $Id$
 */
public abstract class AbstractUser extends User implements Commitable {
	private static final Logger logger = LogManager.getLogger(AbstractUser.class);

	public static void checkValidGroupName(String group) {
		if ((group.indexOf(' ') != -1) || (group.indexOf(';') != -1)) {
			throw new IllegalArgumentException(
					"Groups cannot contain space or other illegal characters");
		}
	}

	private long _credits;

	protected KeyedMap<Key<?>, Object> _data = new KeyedMap<>();

	private String _group = "nogroup";

	private ArrayList<String> _groups = new ArrayList<>();

	private HostMaskCollection _hostMasks = new HostMaskCollection();

	private int _idleTime = 0; // no limit

	// private long _lastNuked;

	/**
	 * Protected for DummyUser b/c TrialTest
	 */
	protected long _lastReset;

	// private long _nukedBytes;
	// private int _racesLost;
	// private int _racesParticipated;
	// private int _racesWon;
	// private float _ratio = 3.0F;
	// private int _requests;
	// private int _requestsFilled;

	private String _username;

	public AbstractUser(String username) {
		_username = username;
		_data.setObject(UserManagement.CREATED, new Date(System
				.currentTimeMillis()));
		_data.setObject(UserManagement.TAGLINE, "no tagline");
	}

	public void addAllMasks(HostMaskCollection hostMaskCollection) {
		getHostMaskCollection().addAllMasks(hostMaskCollection);
	}

	public void addIPMask(String mask) throws DuplicateElementException {
		getHostMaskCollection().addMask(mask);
	}

	// public void addRacesLost() {
	// _racesLost++;
	// }
	//
	// public void addRacesParticipated() {
	// _racesParticipated++;
	// }
	//
	// public void addRacesWon() {
	// _racesWon++;
	// }
	//
	// public void addRequests() {
	// _requests++;
	// }
	//
	// public void addRequestsFilled() {
	// _requestsFilled++;
	// }
	public void addSecondaryGroup(String group)
			throws DuplicateElementException {
		if (_groups.contains(group)) {
			throw new DuplicateElementException(
					"User is already a member of that group");
		}

		checkValidGroupName(group);
		_groups.add(group);
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

	public long getCredits() {
		return _credits;
	}

	public String getGroup() {
		if (_group == null) {
			return "nogroup";
		}

		return _group;
	}

	public List<String> getGroups() {
		return _groups;
	}

	public void setGroups(List<String> groups) {
		_groups = new ArrayList<>(groups);
	}

	public void setHostMaskCollection(HostMaskCollection masks) {
		_hostMasks = masks;
	}

	public HostMaskCollection getHostMaskCollection() {
		return _hostMasks;
	}

	public int getIdleTime() {
		return _idleTime;
	}

	public KeyedMap<Key<?>, Object> getKeyedMap() {
		return _data;
	}

	public void setKeyedMap(KeyedMap<Key<?>, Object> data) {
		_data = data;
	}

	public long getLastReset() {
		return _lastReset;
	}

	public void setLastReset(long lastReset) {
		_lastReset = lastReset;
	}

	// public int getRequests() {
	// return _requests;
	// }
	//
	// public int getRequestsFilled() {
	// return _requestsFilled;
	// }
	
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

	public boolean isExempt() {
		return isMemberOf("exempt");
	}

	public boolean isGroupAdmin() {
		return isMemberOf("gadmin");
	}

	public boolean isMemberOf(String group) {
		if (getGroup().equals(group)) {
			return true;
		}

		for (String myGroup : getGroups()) {
			if (group.equals(myGroup)) {
				return true;
			}
		}

		return false;
	}

	public void logout() {
	}

	public void removeIpMask(String mask) throws NoSuchFieldException {
		if (!_hostMasks.removeMask(mask)) {
			throw new NoSuchFieldException("User has no such ip mask");
		}
	}

	public void removeSecondaryGroup(String group) throws NoSuchFieldException {
		if (!_groups.remove(group)) {
			throw new NoSuchFieldException("User is not a member of that group");
		}
	}

	public void rename(String username) throws UserExistsException,
			UserFileException {
		getAbstractUserManager().rename(this, username); // throws
															// ObjectExistsException
		getAbstractUserManager().delete(this.getName());
		_username = username;
		commit(); // throws IOException
	}

	public void resetDay(Date resetDate) {
		GlobalContext.getEventService().publish(new UserEvent(this, "RESETDAY", resetDate
				.getTime()));
		super.resetDay(resetDate);
		super.resetHour(resetDate);
        logger.info("Reset daily stats for {}", getName());
	}

	public void resetMonth(Date resetDate) {
		GlobalContext.getEventService().publish(new UserEvent(this, "RESETMONTH", resetDate
				.getTime()));
		super.resetMonth(resetDate);
		super.resetDay(resetDate);
		super.resetHour(resetDate);
        logger.info("Reset monthly stats for {}", getName());
	}

	public void resetWeek(Date resetDate) {
		GlobalContext.getEventService().publish(new UserEvent(this, "RESETWEEK", resetDate
				.getTime()));
		super.resetWeek(resetDate);
		if (getKeyedMap().getObjectLong(UserManagement.WKLY_ALLOTMENT) > 0) {
			setCredits(getKeyedMap().getObjectLong(UserManagement.WKLY_ALLOTMENT));
		}
        logger.info("Reset weekly stats for {}", getName());
	}	

	public void resetHour(Date resetDate) {
		// do nothing for now
		super.resetHour(resetDate);
	}

	public void resetYear(Date resetDate) {
		GlobalContext.getEventService().publish(new UserEvent(this, "RESETYEAR", resetDate
				.getTime()));
		super.resetYear(resetDate);
		super.resetMonth(resetDate);
		super.resetDay(resetDate);	
		super.resetHour(resetDate);
        logger.info("Reset Yearly stats for {}", getName());
	}

	public void setCredits(long credits) {
		_credits = credits;
	}

	public void setDeleted(boolean deleted) {
		if (deleted) {
			try {
				addSecondaryGroup("deleted");
			} catch (DuplicateElementException e) {
			}
		} else {
			try {
				removeSecondaryGroup("deleted");
			} catch (NoSuchFieldException e) {
			}
		}
	}

	public void setGroup(String g) {
		checkValidGroupName(g);
		_group = g;
	}

	public void setIdleTime(int idleTime) {
		_idleTime = idleTime;
	}

	public void toggleGroup(String string) {
		if (isMemberOf(string)) {
			try {
				removeSecondaryGroup(string);
			} catch (NoSuchFieldException e) {
				logger.error("isMemberOf() said we were in the group", e);
			}
		} else {
			try {
				addSecondaryGroup(string);
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
		_data.setObject(UserManagement.LASTSEEN, new Date(System
				.currentTimeMillis()));
	}

	// public void updateNukedBytes(long bytes) {
	// _nukedBytes += bytes;
	// }
	//
	// public void updateTimesNuked(int timesNuked) {
	// _timesNuked += timesNuked;
	// }
	
	public int getMaxSimUp() {
		return getKeyedMap().getObjectInteger(UserManagement.MAXSIMUP);
	}

	public void setMaxSimUp(int maxSimUp) {
		getKeyedMap().setObject(UserManagement.MAXSIMUP, maxSimUp);
	}

	public float getMinRatio() {
		return getKeyedMap().getObject(UserManagement.MINRATIO, 3F);
	}

	public void setMinRatio(float minRatio) {
		getKeyedMap().setObject(UserManagement.MINRATIO, minRatio);
	}

	public float getMaxRatio() {
		return getKeyedMap().getObject(UserManagement.MAXRATIO, 3F);
	}

	public void setMaxRatio(float maxRatio) {
		getKeyedMap().setObject(UserManagement.MAXRATIO, maxRatio);
	}

	public int getMaxSimDown() {
		return getKeyedMap().getObjectInteger(UserManagement.MAXSIMDN);
	}

	public void setMaxSimDown(int maxSimDown) {
		getKeyedMap().setObject(UserManagement.MAXSIMDN, maxSimDown);
	}
	public abstract void writeToDisk() throws IOException;
}
