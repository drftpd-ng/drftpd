/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.usermanager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.commands.Nuke;
import org.drftpd.commands.Request;
import org.drftpd.commands.UserManagment;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.RaceStatistics;
import org.drftpd.plugins.Statistics;
import org.drftpd.plugins.Trial;
import org.drftpd.usermanager.HostMaskCollection;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * Implements basic functionality for the User interface.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @author mog
 * @version $Id$
 */
public abstract class AbstractUser extends User {
	private static final Logger logger = Logger.getLogger(AbstractUser.class);

	private KeyedMap _data;

	protected long _downloadedMilliseconds;

	protected long _downloadedMillisecondsDay;

	protected long _downloadedMillisecondsMonth;

	protected long _downloadedMillisecondsWeek;

	/**
	 * Should problably be named group for consistency, this would reset group
	 * for JSXUser though.
	 */
	private String _group = "nogroup";

	protected long _uploadedMilliseconds;

	protected long _uploadedMillisecondsDay;

	protected long _uploadedMillisecondsMonth;

	protected long _uploadedMillisecondsWeek;

	protected String comment;

	protected long created;

	protected long credits;

	protected long downloadedBytes;

	protected long downloadedBytesDay;

	protected long downloadedBytesMonth;

	protected long downloadedBytesWeek;

	protected int downloadedFiles;

	protected int downloadedFilesDay;

	protected int downloadedFilesMonth;

	protected int downloadedFilesWeek;

	protected short groupLeechSlots;

	protected ArrayList groups = new ArrayList();

	protected short groupSlots;

	protected int idleTime = 0; // no limit

	protected ArrayList<String> ipMasks = new ArrayList<String>();

	protected long lastAccessTime = 0;

	protected long lastNuked;

	protected long lastReset;

	//action counters
	protected int logins;

	//login limits
	protected int maxLogins;

	protected int maxLoginsPerIP;

	protected int maxSimDownloads;

	protected int maxSimUploads;

	protected long nukedBytes;

	protected int racesLost;

	protected int racesParticipated;

	protected int racesWon;

	protected float ratio = 3.0F;

	protected int requests;

	protected int requestsFilled;

	protected String tagline = "";

	protected int timesNuked;

	protected long uploadedBytes;

	protected long uploadedBytesDay;

	protected long uploadedBytesMonth;

	protected long uploadedBytesWeek;

	protected int uploadedFiles;

	protected int uploadedFilesDay;

	protected int uploadedFilesMonth;

	protected int uploadedFilesWeek;

	protected String username;

	protected long weeklyAllotment;

	public AbstractUser(String username) {
		this.username = username;
	}

	public void addAllMasks(HostMaskCollection hostMaskCollection) {
		throw new UnsupportedOperationException();
	}

	public void addIPMask(String mask) throws DuplicateElementException {
		if (ipMasks.contains(mask)) {
			throw new DuplicateElementException("IP mask already added");
		}

		ipMasks.add(mask);
	}

	public void addSecondaryGroup(String group)
			throws DuplicateElementException {
		if (groups.contains(group)) {
			throw new DuplicateElementException(
					"User is already a member of that group");
		}

		if (groups.contains(group)) {
			return;
		}

		groups.add(group);
	}

	public boolean equals(Object obj) {
		return (obj instanceof User) ? ((User) obj).getName().equals(
				getName()) : false;
	}

	public long getCredits() {
		return credits;
	}

	public long getDownloadedBytes() {
		return downloadedBytes;
	}

	public long getDownloadedBytesDay() {
		return downloadedBytesDay;
	}

	public long getDownloadedBytesForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return this.downloadedBytesDay;

		case Trial.PERIOD_MONTHLY:
			return this.downloadedBytesMonth;

		case Trial.PERIOD_WEEKLY:
			return this.downloadedBytesWeek;

		case Trial.PERIOD_ALL:
			return this.downloadedBytes;
		}

		throw new RuntimeException();
	}

	public long getDownloadedBytesMonth() {
		return downloadedBytesMonth;
	}

	public long getDownloadedBytesWeek() {
		return downloadedBytesWeek;
	}

	public int getDownloadedFiles() {
		return downloadedFiles;
	}

	public int getDownloadedFilesDay() {
		return downloadedFilesDay;
	}

	public int getDownloadedFilesForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return this.downloadedFilesDay;

		case Trial.PERIOD_MONTHLY:
			return this.downloadedFilesMonth;

		case Trial.PERIOD_WEEKLY:
			return this.downloadedFilesWeek;

		case Trial.PERIOD_ALL:
			return this.downloadedFiles;
		}

		throw new RuntimeException();
	}

	public int getDownloadedFilesMonth() {
		return downloadedFilesMonth;
	}

	public int getDownloadedFilesWeek() {
		return downloadedFilesWeek;
	}

	public long getDownloadedTime() {
		return _downloadedMilliseconds;
	}

	public long getDownloadedTimeForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return _downloadedMillisecondsDay;

		case Trial.PERIOD_MONTHLY:
			return _downloadedMillisecondsMonth;

		case Trial.PERIOD_WEEKLY:
			return _downloadedMillisecondsWeek;

		case Trial.PERIOD_ALL:
			return _downloadedMilliseconds;
		}

		throw new RuntimeException();
	}

	public short getGroupLeechSlots() {
		return groupLeechSlots;
	}

	public String getGroup() {
		if (_group == null) {
			return "nogroup";
		}

		return _group;
	}

	public List getGroups() {
		return groups;
	}

	public short getGroupSlots() {
		return groupSlots;
	}

	public HostMaskCollection getHostMaskCollection() {
		return new HostMaskCollection(ipMasks);
	}

	public int getIdleTime() {
		return idleTime;
	}

	public KeyedMap getKeyedMap() {
		_data = new KeyedMap();

		_data.setObject(Nuke.LASTNUKED, lastNuked);
		_data.setObject(Nuke.NUKED, timesNuked);
		_data.setObject(Nuke.NUKEDBYTES, nukedBytes);

		_data.setObject(Request.REQUESTS, requests);
		_data.setObject(Request.REQUESTSFILLED, requestsFilled);

		_data.setObject(UserManagment.COMMENT, comment);
		_data.setObject(UserManagment.CREATED, new Date(created));
		_data.setObject(UserManagment.RATIO, new Float(ratio));
		_data.setObject(UserManagment.TAGLINE, tagline);

		_data.setObject(RaceStatistics.RACES, racesParticipated);
		_data.setObject(RaceStatistics.RACESLOST, racesLost);
		_data.setObject(RaceStatistics.RACESWON, racesWon);

		_data.setObject(Statistics.LOGINS, logins);
		return _data;
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}

	public long getLastReset() {
		return lastReset;
	}

	public int getLogins() {
		return logins;
	}

	public int getMaxLogins() {
		return maxLogins;
	}

	public int getMaxLoginsPerIP() {
		return maxLoginsPerIP;
	}

	public long getUploadedBytes() {
		return uploadedBytes;
	}

	public long getUploadedBytesDay() {
		return uploadedBytesDay;
	}

	public long getUploadedBytesForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return this.uploadedBytesDay;

		case Trial.PERIOD_MONTHLY:
			return this.uploadedBytesMonth;

		case Trial.PERIOD_WEEKLY:
			return this.uploadedBytesWeek;

		case Trial.PERIOD_ALL:
			return this.uploadedBytes;
		}

		throw new RuntimeException();
	}

	public long getUploadedBytesMonth() {
		return uploadedBytesMonth;
	}

	public long getUploadedBytesWeek() {
		return uploadedBytesWeek;
	}

	public int getUploadedFiles() {
		return uploadedFiles;
	}

	public int getUploadedFilesDay() {
		return uploadedFilesDay;
	}

	public int getUploadedFilesForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return this.uploadedFilesDay;

		case Trial.PERIOD_MONTHLY:
			return this.uploadedFilesMonth;

		case Trial.PERIOD_WEEKLY:
			return this.uploadedFilesWeek;

		case Trial.PERIOD_ALL:
			return this.uploadedFiles;
		}

		throw new RuntimeException();
	}

	public int getUploadedFilesMonth() {
		return uploadedFilesMonth;
	}

	public int getUploadedFilesWeek() {
		return uploadedFilesWeek;
	}

	public long getUploadedTime() {
		return _uploadedMilliseconds;
	}

	public long getUploadedTimeForTrialPeriod(int period) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			return _uploadedMillisecondsDay;

		case Trial.PERIOD_MONTHLY:
			return _uploadedMillisecondsMonth;

		case Trial.PERIOD_WEEKLY:
			return _uploadedMillisecondsWeek;

		case Trial.PERIOD_ALL:
			return _uploadedMilliseconds;
		}

		throw new RuntimeException();
	}

	public String getName() {
		return username;
	}

	public long getWeeklyAllotment() {
		return this.weeklyAllotment;
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

		for (Iterator iter = getGroups().iterator(); iter.hasNext();) {
			if (group.equals((String) iter.next())) {
				return true;
			}
		}

		return false;
	}

	public boolean isNuker() {
		return isMemberOf("nuke");
	}

	public void login() {
		logins += 1;
	}

	public void logout() {
	}

	public void putAllObjects(Map m) {
		throw new UnsupportedOperationException();
	}


	public void removeIpMask(String mask) throws NoSuchFieldException {
		if (!ipMasks.remove(mask)) {
			throw new NoSuchFieldException("User has no such ip mask");
		}
	}

	public void removeSecondaryGroup(String group) throws NoSuchFieldException {
		if (!groups.remove(group)) {
			throw new NoSuchFieldException("User is not a member of that group");
		}
	}

	public void reset(ConnectionManager cmgr) throws UserFileException {
		reset(cmgr, Calendar.getInstance());
	}

	protected void reset(ConnectionManager cmgr, Calendar cal)
			throws UserFileException {
		//ignore reset() if we are called from userfileconverter or the like
		if (cmgr == null) {
			return;
		}

		Date lastResetDate = new Date(this.lastReset);
		this.lastReset = cal.getTimeInMillis();

		//cal = Calendar.getInstance();
		Calendar calTmp = (Calendar) cal.clone();
		calTmp.add(Calendar.DAY_OF_MONTH, -1);
		CalendarUtils.ceilAllLessThanDay(calTmp);

		//has not been reset since midnight
		if (lastResetDate.before(calTmp.getTime())) {
			resetDay(cmgr, cal.getTime());

			//floorDayOfWeek could go into the previous month
			calTmp = (Calendar) cal.clone();

			//calTmp.add(Calendar.WEEK_OF_YEAR, 1);
			CalendarUtils.floorDayOfWeek(calTmp);

			if (lastResetDate.before(calTmp.getTime())) {
				resetWeek(cmgr, calTmp.getTime());
			}

			CalendarUtils.floorDayOfMonth(cal);

			if (lastResetDate.before(cal.getTime())) {
				resetMonth(cmgr, cal.getTime());
			}

			commit(); //throws UserFileException
		}
	}

	private void resetDay(ConnectionManager cm, Date resetDate) {
		cm
				.dispatchFtpEvent(new UserEvent(this, "RESETDAY", resetDate
						.getTime()));

		this.downloadedFilesDay = 0;
		this.uploadedFilesDay = 0;

		_downloadedMillisecondsDay = 0;
		_uploadedMillisecondsDay = 0;

		this.downloadedBytesDay = 0;
		this.uploadedBytesDay = 0;
		logger.info("Reset daily stats for " + getName());
	}

	private void resetMonth(ConnectionManager cm, Date resetDate) {
		cm.dispatchFtpEvent(new UserEvent(this, "RESETMONTH", resetDate
				.getTime()));

		this.downloadedFilesMonth = 0;
		this.uploadedFilesMonth = 0;

		_downloadedMillisecondsMonth = 0;
		_uploadedMillisecondsMonth = 0;

		this.downloadedBytesMonth = 0;
		this.uploadedBytesMonth = 0;
		logger.info("Reset monthly stats for " + getName());
	}

	private void resetWeek(ConnectionManager cm, Date resetDate) {
		logger.info("Reset weekly stats for " + getName() + "(was "
				+ Bytes.formatBytes(uploadedBytesWeek) + " UP and "
				+ Bytes.formatBytes(downloadedBytesWeek) + " DOWN");

		cm.dispatchFtpEvent(new UserEvent(this, "RESETWEEK", resetDate
				.getTime()));

		this.downloadedFilesWeek = 0;
		this.uploadedFilesWeek = 0;

		_downloadedMillisecondsWeek = 0;
		_uploadedMillisecondsWeek = 0;

		this.downloadedBytesWeek = 0;
		this.uploadedBytesWeek = 0;

		if (getWeeklyAllotment() > 0) {
			setCredits(getWeeklyAllotment());
		}
	}

	public void setCredits(long credits) {
		this.credits = credits;
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

	public void setDownloadedBytes(long bytes) {
		this.downloadedBytes = bytes;
	}

	public void setDownloadedBytesDay(long bytes) {
		this.downloadedBytesDay = bytes;
	}

	public void setDownloadedBytesForTrialPeriod(int period, long bytes) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			this.downloadedBytesDay = bytes;

			return;

		case Trial.PERIOD_MONTHLY:
			this.downloadedBytesMonth = bytes;

			return;

		case Trial.PERIOD_WEEKLY:
			this.downloadedBytesWeek = bytes;

			return;

		case Trial.PERIOD_ALL:
			this.downloadedBytes = bytes;

			return;
		}

		throw new RuntimeException();
	}

	public void setDownloadedBytesMonth(long bytes) {
		this.downloadedBytesMonth = bytes;
	}

	public void setDownloadedBytesWeek(long bytes) {
		this.downloadedBytesWeek = bytes;
	}

	public void setDownloadedFiles(int files) {
		this.downloadedFiles = files;
	}

	public void setDownloadedFilesDay(int files) {
		this.downloadedFilesDay = files;
	}

	public void setDownloadedFilesForTrialPeriod(int period, int files) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			this.downloadedFilesDay = files;

			return;

		case Trial.PERIOD_MONTHLY:
			this.downloadedFilesMonth = files;

			return;

		case Trial.PERIOD_WEEKLY:
			this.downloadedFilesWeek = files;

			return;

		case Trial.PERIOD_ALL:
			this.downloadedFiles = files;

			return;
		}

		throw new RuntimeException();
	}

	public void setDownloadedFilesMonth(int files) {
		this.downloadedFilesMonth = files;
	}

	public void setDownloadedFilesWeek(int files) {
		this.downloadedFilesWeek = files;
	}

	public void setDownloadedTime(int millis) {
		_downloadedMilliseconds = millis;
	}

	public void setDownloadedTime(long millis) {
		_downloadedMilliseconds = millis;
	}

	public void setDownloadedTimeDay(int millis) {
		_downloadedMillisecondsDay = millis;
	}

	public void setDownloadedTimeDay(long millis) {
		_downloadedMillisecondsDay = millis;
	}

	public void setDownloadedTimeForTrialPeriod(int period, long millis) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			_downloadedMillisecondsDay = millis;

			return;

		case Trial.PERIOD_MONTHLY:
			_downloadedMillisecondsMonth = millis;

			return;

		case Trial.PERIOD_WEEKLY:
			_downloadedMillisecondsWeek = millis;

			return;

		case Trial.PERIOD_ALL:
			_downloadedMilliseconds = millis;

			return;
		}

		throw new RuntimeException();
	}

	public void setDownloadedTimeMonth(long millis) {
		_downloadedMillisecondsMonth = millis;
	}

	public void setDownloadedTimeWeek(long millis) {
		_downloadedMillisecondsWeek = millis;
	}

	public void setGroup(String group) {
		_group = group;
	}

	public void setGroupLeechSlots(short s) {
		groupLeechSlots = s;
	}

	public void setGroupSlots(short s) {
		groupSlots = s;
	}

	public void setIdleTime(int idleTime) {
		this.idleTime = idleTime;
	}

	public void setLastAccessTime(long lastAccessTime) {
		this.lastAccessTime = lastAccessTime;
	}

	public void setLogins(int logins) {
		this.logins = logins;
	}

	public void setMaxLogins(int maxLogins) {
		this.maxLogins = maxLogins;
	}

	public void setMaxLoginsPerIP(int maxLoginsPerIP) {
		this.maxLoginsPerIP = maxLoginsPerIP;
	}

	public void setNukedBytes(long nukedBytes) {
		this.nukedBytes = nukedBytes;
	}

	public void setUploadedBytes(long bytes) {
		this.uploadedBytes = bytes;
	}

	public void setUploadedBytesDay(long bytes) {
		this.uploadedBytesDay = bytes;
	}

	public void setUploadedBytesForTrialPeriod(int period, long bytes) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			this.uploadedBytesDay = bytes;

			return;

		case Trial.PERIOD_MONTHLY:
			this.uploadedBytesMonth = bytes;

			return;

		case Trial.PERIOD_WEEKLY:
			this.uploadedBytesWeek = bytes;

			return;

		case Trial.PERIOD_ALL:
			this.uploadedBytes = bytes;

			return;
		}

		throw new RuntimeException();
	}

	public void setUploadedBytesMonth(long bytes) {
		this.uploadedBytesMonth = bytes;
	}

	public void setUploadedBytesWeek(long bytes) {
		this.uploadedBytesWeek = bytes;
	}

	public void setUploadedFiles(int files) {
		this.uploadedFiles = files;
	}

	public void setUploadedFilesDay(int files) {
		this.uploadedFilesDay = files;
	}

	public void setUploadedFilesForTrialPeriod(int period, int files) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			this.uploadedFilesDay = files;

			return;

		case Trial.PERIOD_MONTHLY:
			this.uploadedFilesMonth = files;

			return;

		case Trial.PERIOD_WEEKLY:
			this.uploadedFilesWeek = files;

			return;

		case Trial.PERIOD_ALL:
			this.uploadedFiles = files;

			return;
		}

		throw new RuntimeException();
	}

	public void setUploadedFilesMonth(int files) {
		this.uploadedFilesMonth = files;
	}

	public void setUploadedFilesWeek(int files) {
		this.uploadedFilesWeek = files;
	}

	public void setUploadedTime(int millis) {
		_uploadedMilliseconds = millis;
	}

	public void setUploadedTime(long millis) {
		_uploadedMilliseconds = millis;
	}

	public void setUploadedTimeDay(int millis) {
		_uploadedMillisecondsDay = millis;
	}

	public void setUploadedTimeDay(long millis) {
		_uploadedMillisecondsDay = millis;
	}

	public void setUploadedTimeForTrialPeriod(int period, long millis) {
		switch (period) {
		case Trial.PERIOD_DAILY:
			_uploadedMillisecondsDay = millis;

			return;

		case Trial.PERIOD_MONTHLY:
			_uploadedMillisecondsMonth = millis;

			return;

		case Trial.PERIOD_WEEKLY:
			_uploadedMillisecondsWeek = millis;

			return;

		case Trial.PERIOD_ALL:
			_uploadedMilliseconds = millis;

			return;
		}

		throw new RuntimeException();
	}

	public void setUploadedTimeMonth(int millis) {
		_uploadedMillisecondsMonth = millis;
	}

	public void setUploadedTimeMonth(long millis) {
		_uploadedMillisecondsMonth = millis;
	}

	public void setUploadedTimeWeek(int millis) {
		_uploadedMillisecondsWeek = millis;
	}

	public void setUploadedTimeWeek(long millis) {
		_uploadedMillisecondsWeek = millis;
	}

	public void setWeeklyAllotment(long weeklyAllotment) {
		this.weeklyAllotment = weeklyAllotment;
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
		return username;
	}

	public void updateCredits(long credits) {
		this.credits += credits;
	}

	public void updateDownloadedBytes(long bytes) {
		this.downloadedBytes += bytes;
		this.downloadedBytesDay += bytes;
		this.downloadedBytesWeek += bytes;
		this.downloadedBytesMonth += bytes;
	}

	public void updateDownloadedFiles(int i) {
		this.downloadedFiles += i;
		this.downloadedFilesDay += i;
		this.downloadedFilesWeek += i;
		this.downloadedFilesMonth += i;
	}

	public void updateDownloadedTime(long millis) {
		_downloadedMilliseconds += millis;
		_downloadedMillisecondsDay += millis;
		_downloadedMillisecondsWeek += millis;
		_downloadedMillisecondsMonth += millis;
	}

	/**
	 * Hit user - update last access time
	 */
	public void updateLastAccessTime() {
		lastAccessTime = System.currentTimeMillis();
	}

	public void updateUploadedBytes(long bytes) {
		this.uploadedBytes += bytes;
		this.uploadedBytesDay += bytes;
		this.uploadedBytesWeek += bytes;
		this.uploadedBytesMonth += bytes;
	}

	public void updateUploadedFiles(int i) {
		this.uploadedFiles += i;
		this.uploadedFilesDay += i;
		this.uploadedFilesWeek += i;
		this.uploadedFilesMonth += i;
	}

	public void updateUploadedTime(long millis) {
		_uploadedMilliseconds += millis;
		_uploadedMillisecondsDay += millis;
		_uploadedMillisecondsWeek += millis;
		_uploadedMillisecondsMonth += millis;
	}
}
