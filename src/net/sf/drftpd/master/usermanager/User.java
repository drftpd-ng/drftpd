package net.sf.drftpd.master.usermanager;

import java.util.List;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.ObjectExistsException;

/**
 * @author mog
 * @version $Id: User.java,v 1.31 2004/01/13 21:36:31 mog Exp $
 */
public interface User {

	public void addGroup(String group) throws DuplicateElementException;

	public void addIPMask(String mask) throws DuplicateElementException;
	public void addRacesLost();
	public void addRacesParticipated();

	public void addRacesWon();
	public void addRequests();
	public void addRequestsFilled();
	public boolean checkIP(String masks[], boolean useIdent);
	public boolean checkPassword(String password);

	/**
	 * Commit changes.
	 * @throws UserFileException if an error saving the userfile occured.
	 */
	public void commit() throws UserFileException;

	public String getComment();
	/**
	 * authenticates and logs in the user.
	 * @param user given password
	 */
	public long getCreated();
	/**
	 * Returns the credits.
	 * @return long
	 */
	public long getCredits();
	/**
	 * Returns the downloadedBytes.
	 * @return long
	 */
	public long getDownloadedBytes();
	/**
	 * Returns the downloadedBytesDay.
	 * @return long
	 */
	public long getDownloadedBytesDay();
	/**
	 * Returns the downloadedBytesMonth.
	 * @return long
	 */
	public long getDownloadedBytesMonth();
	/**
	 * Returns the downloadedBytesWeek.
	 * @return long
	 */
	public long getDownloadedBytesWeek();

	/**
	 * Returns the downloadedFiles.
	 * @return int
	 */
	public int getDownloadedFiles();
	/**
	 * Returns the downloadedFilesDay.
	 * @return int
	 */
	public int getDownloadedFilesDay();
	/**
	 * Returns the downloadedFilesMonth.
	 * @return int
	 */
	public int getDownloadedFilesMonth();
	/**
	 * Returns the downloadedFilesWeek.
	 * @return int
	 */
	public int getDownloadedFilesWeek();
	/**
	 * Returns the downloadedSeconds.
	 * @return int
	 */
	public int getDownloadedSeconds();
	/**
	 * Returns the downloadedSecondsDay.
	 * @return int
	 */
	public int getDownloadedSecondsDay();
	/**
	 * Returns the downloadedSecondsMonth.
	 * @return int
	 */
	public int getDownloadedSecondsMonth();

	public int getDownloadedSecondsWeek();
	public short getGroupLeechSlots();
	public String getGroupName();
	public List getGroups();

	public short getGroupSlots();
	/**
	 * Returns the homedir(chroot).
	 * @return String
	 */
	public String getHomeDirectory();
	/**
	 * Returns the idleTime.
	 * @return long
	 */
	public int getIdleTime();
	public List getIpMasks();
	public List getIpMasks2();
	/**
	 * Get last access time
	 */
	public long getLastAccessTime();
	/**
	 * Returns the lastNuked.
	 * @return long
	 */
	public long getLastNuked();
	public long getLastReset();
	/**
	 * Returns the logins.
	 * @return int
	 */
	public int getLogins();
	/**
	 * Get maximum user download rate in bytes/sec
	 */
	public int getMaxDownloadRate();
	/**
	 * Returns the maxLogins.
	 * @return int
	 */
	public int getMaxLogins();
	/**
	 * Returns the maxLoginsPerIP.
	 * @return int
	 */
	public int getMaxLoginsPerIP();
	public int getMaxSimDownloads();
	/**
	 * Returns the maxSimUploads.
	 * @return int
	 */
	public int getMaxSimUploads();
	/**
	 * Get maximum user upload rate in bytes/sec.
	 */
	public int getMaxUploadRate();

	/**
	 * Returns the nukedBytes.
	 * @return long
	 */
	public long getNukedBytes();
	public int getRacesLost();
	public int getRacesParticipated();
	/**
	 * Returns the downloadedSecondsWeek.
	 * @return int
	 */

	public int getRacesWon();
	/**
	 * Returns the ratio.
	 * @return float
	 */
	public float getRatio();
	public int getRequests();
	public int getRequestsFilled();
	/**
	 * Returns the tagline.
	 * @return String
	 */
	public String getTagline();
	/**
	 * Returns the timelimit.
	 * @return int
	 */
	public int getTimelimit();
	/**
	 * Returns the nuked.
	 * @return int
	 */
	public int getTimesNuked();
	/**
	 * Returns the timeToday.
	 * @return long
	 */
	public long getTimeToday();
	/**
	 * Returns the uploadedBytes.
	 * @return long
	 */
	public long getUploadedBytes();
	/**
	 * Returns the uploadedBytesDay.
	 * @return long
	 */
	public long getUploadedBytesDay();
	/**
	 * Returns the uploadedBytesMonth.
	 * @return long
	 */
	public long getUploadedBytesMonth();
	/**
	 * Returns the uploadedBytesWeek.
	 * @return long
	 */
	public long getUploadedBytesWeek();
	/**
	 * Returns the uploadedFiles.
	 * @return int
	 */
	public int getUploadedFiles();
	/**
	 * Returns the uploadedFilesDay.
	 * @return int
	 */
	public int getUploadedFilesDay();
	/**
	 * Returns the uploadedFilesMonth.
	 * @return int
	 */
	public int getUploadedFilesMonth();
	/**
	 * Returns the uploadedFilesWeek.
	 * @return int
	 */
	public int getUploadedFilesWeek();
	/**
	 * Returns the uploadedSeconds.
	 * @return int
	 */
	public int getUploadedSeconds();
	/**
	 * Returns the uploadedSecondsDay.
	 * @return int
	 */
	public int getUploadedSecondsDay();
	/**
	 * Returns the uploadedSecondsMonth.
	 * @return int
	 */
	public int getUploadedSecondsMonth();
	/**
	 * Returns the uploadedSecondsWeek.
	 * @return int
	 */
	public int getUploadedSecondsWeek();

	////////////////////////////////// autogenerated getters & setters below /////////////////////////////
	public String getUsername();
	public long getWeeklyAllotment();
	/**
	 * Returns the admin.
	 * @return boolean
	 */
	public boolean isAdmin();

	/**
	 * Check if user is a member of the "anonymous" group.
	 * 
	 * If your implementation of User does not use "anonymous"
	 * group for indicating an anonymous user you must still return
	 * "anonymous" in getGroups() and isMemberOf()
	 */
	public boolean isAnonymous();
	/**
	 * Returns the deleted.
	 * @return boolean
	 */
	public boolean isDeleted();
	public boolean isExempt();
	public boolean isGroupAdmin();
	public boolean isMemberOf(String group);
	/**
	 * Returns the nuker.
	 * @return boolean
	 */
	public boolean isNuker();
	/**
	 * User login.
	 */
	public void login();
	/**
	 * User logout
	 */
	public void logout();

	public void purge();
	public void removeGroup(String group) throws NoSuchFieldException;
	public void removeIpMask(String mask) throws NoSuchFieldException;
	public void rename(String username)
		throws ObjectExistsException, UserFileException;

	public void setComment(String comment);
	/**
	 * Sets the credits.
	 * @param credits The credits to set
	 */
	public void setCredits(long credits);
	/**
	 * Sets the deleted.
	 * @param deleted The deleted to set
	 */
	public void setDeleted(boolean deleted);
	public void setGroup(String group);
	public void setGroupLeechSlots(short s);

	public void setGroupSlots(short s);

	/**
	 * Sets the homedir(chroot).
	 * @param home The homedir to set
	 */
	public void setHomeDirectory(String home);
	/**
	 * Sets the idleTime.
	 * @param idleTime The idleTime to set
	 */
	public void setIdleTime(int idleTime);

	/**
	 * Sets the lastAccessTime.
	 * @param lastAccessTime The lastAccessTime to set
	 */
	public void setLastAccessTime(long lastAccessTime);
	/**
	 * Sets the lastNuked.
	 * @param lastNuked The lastNuked to set
	 */
	public void setLastNuked(long lastNuked);
	/**
	 * Sets the logins.
	 * @param logins The logins to set
	 */
	public void setLogins(int logins);
	/**
	 * Set user maximum download rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public void setMaxDownloadRate(int rate);
	/**
	 * Sets the maxLogins.
	 * @param maxLogins The maxLogins to set
	 */
	public void setMaxLogins(int maxLogins);
	/**
	 * Sets the maxLoginsPerIP.
	 * @param maxLoginsPerIP The maxLoginsPerIP to set
	 */
	public void setMaxLoginsPerIP(int maxLoginsPerIP);
	public void setMaxSimDownloads(int maxSimDownloads);
	/**
	 * Sets the maxSimUploads.
	 * @param maxSimUploads The maxSimUploads to set
	 */
	public void setMaxSimUploads(int maxSimUploads);
	/**
	 * Set user maximum upload rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public void setMaxUploadRate(int rate);
	/**
	 * Sets the nukedBytes.
	 * @param nukedBytes The nukedBytes to set
	 */
	public void setNukedBytes(long nukedBytes);
	public void setPassword(String password);
	/**
	 * Sets the ratio.
	 * @param ratio The ratio to set
	 */
	public void setRatio(float ratio);

	/**
	 * Sets the tagline.
	 * @param tagline The tagline to set
	 */

	public void setTagline(String tagline);
	/**
	 * Sets the timelimit.
	 * @param timelimit The timelimit to set
	 */
	public void setTimelimit(int timelimit);
	public void setTimesNuked(int nuked);
	/**
	 * Time that the user has been online today in milliseconds.
	 * Updated on logout.
	 */
	public void setTimeToday(long timeToday);
	public void setWeeklyAllotment(long weeklyAllotment);

	public void toggleGroup(String string);
	public void updateCredits(long credits);
	public void updateDownloadedBytes(long bytes);
	public void updateDownloadedFiles(int i);
	/**
	 * Hit user - update last access time
	 */
	public void updateLastAccessTime();
	public void updateNukedBytes(long bytes);
	public void updateTimesNuked(int timesNuked);
	public void updateUploadedBytes(long bytes);
	public void updateUploadedFiles(int i);
	public void updateDownloadedMilliseconds(long millis);
	public void updateUploadedMilliseconds(long millis);
}
