package net.sf.drftpd.master.usermanager;

import java.util.List;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.ObjectExistsException;

/**
 * @author mog
 * @version $Id: User.java,v 1.33 2004/01/27 10:31:31 flowman Exp $
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
	public long getDownloadedBytes();
	public long getDownloadedBytesDay();
	public long getDownloadedBytesForPeriod(int period);
	public long getDownloadedBytesMonth();
	public long getDownloadedBytesWeek();

	public int getDownloadedFiles();
	public int getDownloadedFilesDay();
	public int getDownloadedFilesForPeriod(int i);
	public int getDownloadedFilesMonth();
	public int getDownloadedFilesWeek();

	public long getDownloadedMillisecondsForPeriod(int i);
	public short getGroupLeechSlots();
	public String getGroupName();
	public List getGroups();

	public short getGroupSlots();
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
	 * Returns the nuked.
	 * @return int
	 */
	public int getTimesNuked();

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
	public long getUploadedBytesForPeriod(int period);
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

	public int getUploadedFilesForPeriod(int period);
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
	
	public long getDownloadedMilliseconds();
	public long getUploadedMilliseconds();

	public long getUploadedMillisecondsForPeriod(int i);
	
	public void setUploadedFiles(int files);
	public void setUploadedBytes(long bytes);
	public void setUploadedSeconds(int millis);
		
	public void setUploadedFilesMonth(int files);
	public void setUploadedBytesMonth(long bytes);
	public void setUploadedSecondsMonth(int millis);
	
	public void setUploadedFilesWeek(int files);
	public void setUploadedBytesWeek(long bytes);
	public void setUploadedSecondsWeek(int millis);
	
	public void setUploadedFilesDay(int files);
	public void setUploadedBytesDay(long bytes);
	public void setUploadedSecondsDay(int millis);

	public void setDownloadedFiles(int files);
	public void setDownloadedBytes(long bytes);
	public void setDownloadedSeconds(int millis);

	public void setDownloadedFilesMonth(int files);
	public void setDownloadedBytesMonth(long bytes);
	public void setDownloadedSecondsMonth(int millis);
	
	public void setDownloadedFilesWeek(int files);
	public void setDownloadedBytesWeek(long bytes);
	public void setDownloadedSecondsWeek(int millis);
	
	public void setDownloadedFilesDay(int files);
	public void setDownloadedBytesDay(long bytes);
	public void setDownloadedSecondsDay(int millis);

	public String getUsername();
	public long getWeeklyAllotment();
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

	public void setDownloadedBytesForPeriod(int period, long bytes);

	public void setDownloadedFilesForPeriod(int period, int files);

	public void setDownloadedMilliseconds(long millis);
	public void setDownloadedMillisecondsDay(long millis);

	public void setDownloadedMillisecondsForPeriod(int i, long millis);
	public void setDownloadedMillisecondsMonth(long millis);
	public void setDownloadedMillisecondsWeek(long millis);
	public void setGroup(String group);
	public void setGroupLeechSlots(short s);

	public void setGroupSlots(short s);

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
	public void setTimesNuked(int nuked);
	public void setUploadedBytesForPeriod(int i, long l);

	public void setUploadedFilesForPeriod(int period, int files);

	public void setUploadedMilliseconds(long millis);
	public void setUploadedMillisecondsDay(long millis);

	public void setUploadedMillisecondsForPeriod(int i, long millis);
	public void setUploadedMillisecondsMonth(long millis);
	public void setUploadedMillisecondsWeek(long millis);

	public void setWeeklyAllotment(long weeklyAllotment);

	public void toggleGroup(String string);
	public void updateCredits(long credits);
	public void updateDownloadedBytes(long bytes);
	public void updateDownloadedFiles(int i);
	public void updateDownloadedMilliseconds(long millis);
	/**
	 * Hit user - update last access time
	 */
	public void updateLastAccessTime();
	public void updateNukedBytes(long bytes);
	public void updateTimesNuked(int timesNuked);
	public void updateUploadedBytes(long bytes);
	public void updateUploadedFiles(int i);
	public void updateUploadedMilliseconds(long millis);
}
