package net.sf.drftpd.master.usermanager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * Generic user class. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public abstract class AbstractUser implements User {
	private static Logger logger = Logger.getLogger(AbstractUser.class);
	/**
	 * Should problably be named group for consistency,
	 * this would reset group for JSXUser though. 
	 */
	private String _group = "nogroup";

	protected boolean anonymous;
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

	protected int downloadedSeconds;
	protected int downloadedSecondsDay;
	protected int downloadedSecondsMonth;
	protected int downloadedSecondsWeek;
	protected short groupLeechSlots;

	protected ArrayList groups = new ArrayList();

	protected short groupSlots;
	protected String home;

	protected int idleTime = 0; // no limit
	protected ArrayList ipMasks = new ArrayList();
	protected long lastAccessTime = 0;

	/**
	 * Time last nuked specified in seconds since 1970
	 */
	protected long lastNuked;
	protected long lastReset;

	//action counters
	protected int logins;
	protected int maxDownloadRate;

	//login limits
	protected int maxLogins;
	protected int maxLoginsPerIP;
	protected int maxSimDownloads;
	protected int maxSimUploads;

	protected int maxUploadRate;

	protected long nukedBytes;

	protected float ratio = 3.0F;
	protected String tagline;
	protected int timelimit;
	protected int timesNuked;

	protected long timeToday;

	protected long uploadedBytes;
	protected long uploadedBytesDay;
	protected long uploadedBytesMonth;
	protected long uploadedBytesWeek;

	protected int uploadedFiles;
	protected int uploadedFilesDay;
	protected int uploadedFilesMonth;
	protected int uploadedFilesWeek;

	protected int uploadedSeconds;
	protected int uploadedSecondsDay;
	protected int uploadedSecondsMonth;
	protected int uploadedSecondsWeek;

	protected String username;

	private long weeklyAllotment;

	public AbstractUser(String username) {
		this.username = username;
	}

	public void addGroup(String group) throws DuplicateElementException {
		if (groups.contains(group))
			throw new DuplicateElementException("User is already a member of that group");
		if (groups.contains(group))
			return;
		groups.add(group);
	}

	//////////////////////////////// generic getters & setters ///////////////////////

	public void addIPMask(String mask) throws DuplicateElementException {
		if (ipMasks.contains(mask))
			throw new DuplicateElementException("IP mask already added");
		ipMasks.add(mask);
	}
	public boolean checkIP(String masks[]) {

		Perl5Matcher m = new Perl5Matcher();

		for (Iterator e2 = ipMasks.iterator(); e2.hasNext();) {
			String mask = (String) e2.next();

			Pattern p;
			try {
				p = new GlobCompiler().compile(mask);
			} catch (MalformedPatternException ex) {
				ex.printStackTrace();
				return false;
			}

			for (int i = 0; i < masks.length; i++) {
				String subject = masks[i];

				if (m.matches(subject, p)) {
					return true;
				}
			}
		}
		System.out.println(getClass().getName() + ".checkIP(): no match");
		return false;
	}

	public String getComment() {
		return comment;
	}

	public long getCreated() {
		return created;
	}

	/**
	 * Returns the credits.
	 * @return long
	 */
	public long getCredits() {
		return credits;
	}

	/**
	 * Returns the downloadedBytes.
	 * @return long
	 */
	public long getDownloadedBytes() {
		return downloadedBytes;
	}

	/**
	 * Returns the downloadedBytesDay.
	 * @return long
	 */
	public long getDownloadedBytesDay() {
		return downloadedBytesDay;
	}

	/**
	 * Returns the downloadedBytesMonth.
	 * @return long
	 */
	public long getDownloadedBytesMonth() {
		return downloadedBytesMonth;
	}

	/**
	 * Returns the downloadedBytesWeek.
	 * @return long
	 */
	public long getDownloadedBytesWeek() {
		return downloadedBytesWeek;
	}

	/**
	 * Returns the downloadedFiles.
	 * @return int
	 */
	public int getDownloadedFiles() {
		return downloadedFiles;
	}

	/**
	 * Returns the downloadedFilesDay.
	 * @return int
	 */
	public int getDownloadedFilesDay() {
		return downloadedFilesDay;
	}

	/**
	 * Returns the downloadedFilesMonth.
	 * @return int
	 */
	public int getDownloadedFilesMonth() {
		return downloadedFilesMonth;
	}

	/**
	 * Returns the downloadedFilesWeek.
	 * @return int
	 */
	public int getDownloadedFilesWeek() {
		return downloadedFilesWeek;
	}

	/**
	 * Returns the downloadedSeconds.
	 * @return int
	 */
	public int getDownloadedSeconds() {
		return downloadedSeconds;
	}

	/**
	 * Returns the downloadedSecondsDay.
	 * @return int
	 */
	public int getDownloadedSecondsDay() {
		return downloadedSecondsDay;
	}

	/**
	 * Returns the downloadedSecondsMonth.
	 * @return int
	 */
	public int getDownloadedSecondsMonth() {
		return downloadedSecondsMonth;
	}

	/**
	 * Returns the downloadedSecondsWeek.
	 * @return int
	 */
	public int getDownloadedSecondsWeek() {
		return downloadedSecondsWeek;
	}

	/**
	 * @return
	 */
	public short getGroupLeechSlots() {
		return groupLeechSlots;
	}

	public String getGroupName() {
		if (_group == null)
			return "nogroup";
		return _group;
	}

	/**
	 * Returns the groups.
	 * @return Vector
	 */
	public Collection getGroups() {
		return groups;
	}

	/**
	 * @return
	 */
	public short getGroupSlots() {
		return groupSlots;
	}

	/**
	 * Returns the homedir(chroot).
	 * @return String
	 */
	public String getHomeDirectory() {
		return home;
	}

	/**
	 * Returns the idleTime.
	 * @return long
	 */
	public int getIdleTime() {
		return idleTime;
	}

	/**
	 * Returns the ipMasks.
	 * @return Vector
	 */
	public Collection getIpMasks() {
		return ipMasks;
	}

	/**
	 * Get last access time
	 */
	public long getLastAccessTime() {
		return lastAccessTime;
	}

	/**
	 * Returns the lastNuked.
	 * @return long
	 */
	public long getLastNuked() {
		return lastNuked;
	}

	/**
	 * Returns the logins.
	 * @return int
	 */
	public int getLogins() {
		return logins;
	}

	/**
	 * Get maximum user download rate in bytes/sec
	 */
	public int getMaxDownloadRate() {
		return maxDownloadRate;
	}

	/**
	 * Returns the maxLogins.
	 * @return int
	 */
	public int getMaxLogins() {
		return maxLogins;
	}

	/**
	 * Returns the maxLoginsPerIP.
	 * @return int
	 */
	public int getMaxLoginsPerIP() {
		return maxLoginsPerIP;
	}

	public int getMaxSimDownloads() {
		return maxSimDownloads;
	}

	/**
	 * Returns the maxSimUploads.
	 * @return int
	 */
	public int getMaxSimUploads() {
		return maxSimUploads;
	}

	/**
	 * Get maximum user upload rate in bytes/sec.
	 */
	public int getMaxUploadRate() {
		return maxUploadRate;
	}

	/**
	 * Returns the nukedBytes.
	 * @return long
	 */
	public long getNukedBytes() {
		return nukedBytes;
	}

	/**
	 * Returns the ratio.
	 * @return float
	 */
	public float getRatio() {
		return ratio;
	}

	/**
	 * Returns the tagline.
	 * @return String
	 */
	public String getTagline() {
		return tagline;
	}

	/**
	 * Returns the timelimit.
	 * @return int
	 */
	public int getTimelimit() {
		return timelimit;
	}

	/**
	 * Returns the nuked.
	 * @return int
	 */
	public int getTimesNuked() {
		return timesNuked;
	}

	/**
	 * Returns the timeToday.
	 * @return long
	 */
	public long getTimeToday() {
		return timeToday;
	}

	/**
	 * Returns the uploadedBytes.
	 * @return long
	 */
	public long getUploadedBytes() {
		return uploadedBytes;
	}

	/**
	 * Returns the uploadedBytesDay.
	 * @return long
	 */
	public long getUploadedBytesDay() {
		return uploadedBytesDay;
	}

	/**
	 * Returns the uploadedBytesMonth.
	 * @return long
	 */
	public long getUploadedBytesMonth() {
		return uploadedBytesMonth;
	}

	/**
	 * Returns the uploadedBytesWeek.
	 * @return long
	 */
	public long getUploadedBytesWeek() {
		return uploadedBytesWeek;
	}

	/**
	 * Returns the uploadedFiles.
	 * @return int
	 */
	public int getUploadedFiles() {
		return uploadedFiles;
	}

	/**
	 * Returns the uploadedFilesDay.
	 * @return int
	 */
	public int getUploadedFilesDay() {
		return uploadedFilesDay;
	}

	/**
	 * Returns the uploadedFilesMonth.
	 * @return int
	 */
	public int getUploadedFilesMonth() {
		return uploadedFilesMonth;
	}

	/**
	 * Returns the uploadedFilesWeek.
	 * @return int
	 */
	public int getUploadedFilesWeek() {
		return uploadedFilesWeek;
	}

	/**
	 * Returns the uploadedSeconds.
	 * @return int
	 */
	public int getUploadedSeconds() {
		return uploadedSeconds;
	}

	/**
	 * Returns the uploadedSecondsDay.
	 * @return int
	 */
	public int getUploadedSecondsDay() {
		return uploadedSecondsDay;
	}

	/**
	 * Returns the uploadedSecondsMonth.
	 * @return int
	 */
	public int getUploadedSecondsMonth() {
		return uploadedSecondsMonth;
	}

	/**
	 * Returns the uploadedSecondsWeek.
	 * @return int
	 */
	public int getUploadedSecondsWeek() {
		return uploadedSecondsWeek;
	}

	////////////////////////////////// autogenerated getters & setters below /////////////////////////////
	/**
	 * Get the user name.
	 */
	public String getUsername() {
		return username;
	}

	public long getWeeklyAllotment() {
		return this.weeklyAllotment;
	}
	public int hashCode() {
		return getUsername().hashCode();
	}

	public boolean isAdmin() {
		return isMemberOf("siteop");
	}
	/**
	 * Returns the anonymous.
	 * @return boolean
	 */
	public boolean isAnonymous() {
		return anonymous;
	}

	/**
	 * Returns the deleted.
	 * @return boolean
	 */
	public boolean isDeleted() {
		return isMemberOf("deleted");
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#isGroupAdmin()
	 */
	public boolean isGroupAdmin() {
		return isMemberOf("gadmin");
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#isMemberOf(java.lang.String)
	 */
	public boolean isExempt() {
		return isMemberOf("exempt");
	}

	public boolean isMemberOf(String group) {
		if (getGroupName().equals(group))
			return true;
		for (Iterator iter = getGroups().iterator(); iter.hasNext();) {
			if (group.equals((String) iter.next()))
				return true;
		}
		return false;
	}
	public boolean isNuker() {
		return isMemberOf("nuke");
	}

	/**
	 * User login.
	 */
	public void login() {
		updateLogins(1);
	}

	/**
	 * User logout
	 */
	public void logout() {
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#removeGroup()
	 */
	public void removeGroup(String group) throws NoSuchFieldException {
		if (!groups.remove(group))
			throw new NoSuchFieldException("User is not a member of that group");
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#removeIpMask(java.lang.String)
	 */
	public void removeIpMask(String mask) throws NoSuchFieldException {
		if (!ipMasks.remove(mask))
			throw new NoSuchFieldException("User has no such ip mask");
	}

	public void reset(ConnectionManager cmgr) {
		//handle if we are called from userfileconverter or the like
		if (cmgr == null ) return;
		
		Date lastResetDate = new Date(this.lastReset);

		Calendar cal = Calendar.getInstance();

		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 0);

		//has not been reset since midnight
		if (lastResetDate.before(cal.getTime()))
			resetDay(cmgr);

		//week could go into the previous month
		Calendar cal2 = (Calendar)cal.clone();
		cal2.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
		if (lastResetDate.before(cal2.getTime()))
			resetWeek(cmgr);

		cal.set(Calendar.DAY_OF_MONTH, 1);
		if (lastResetDate.before(cal.getTime()))
			resetMonth(cmgr);

		lastReset = System.currentTimeMillis();
	}
	/**
	 * 
	 */
	private void resetDay(ConnectionManager cm) {
		logger.info("Reset daily stats for " + getUsername());
		cm.dispatchFtpEvent(new UserEvent(this, "RESETDAY"));

		this.downloadedFilesDay = 0;
		this.uploadedBytesDay = 0;

		this.downloadedSecondsDay = 0;
		this.uploadedSecondsDay = 0;

		this.downloadedBytesDay = 0;
		this.uploadedBytesDay = 0;
		this.timeToday = 0;
	}
	/**
	 * 
	 */
	private void resetMonth(ConnectionManager cm) {
		cm.dispatchFtpEvent(new UserEvent(this, "RESETMONTH"));
		logger.info("Reset monthly stats for " + getUsername());

		this.downloadedFilesMonth = 0;
		this.uploadedBytesMonth = 0;

		this.downloadedSecondsMonth = 0;
		this.uploadedSecondsMonth = 0;

		this.downloadedBytesMonth = 0;
		this.uploadedBytesMonth = 0;
	}
	/**
	 * 
	 */
	private void resetWeek(ConnectionManager cm) {
		cm.dispatchFtpEvent(new UserEvent(this, "RESETWEEK"));
		logger.info("Reset weekly stats for " + getUsername());

		this.downloadedFilesWeek = 0;
		this.uploadedBytesWeek = 0;

		this.downloadedSecondsWeek = 0;
		this.uploadedSecondsWeek = 0;

		this.downloadedBytesWeek = 0;
		this.uploadedBytesWeek = 0;
		if (getWeeklyAllotment() > 0) {
			setCredits(getWeeklyAllotment());
		}

	}

	public void setComment(String comment) {
		this.comment = comment;
	}

	/**
	 * Sets the credits.
	 * @param credits The credits to set
	 */
	public void setCredits(long credits) {
		this.credits = credits;
	}

	/**
	 * Sets the deleted.
	 * @param deleted The deleted to set
	 */
	public void setDeleted(boolean deleted) {
		if (deleted) {
			try {
				addGroup("deleted");
			} catch (DuplicateElementException e) {
			}
		} else {
			try {
				removeGroup("deleted");
			} catch (NoSuchFieldException e) {
			}
		}
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#setGroup(java.lang.String)
	 */
	public void setGroup(String group) {
		_group = group;
	}

	/**
	 * @param s
	 */
	public void setGroupLeechSlots(short s) {
		groupLeechSlots = s;
	}

	/**
	 * @param s
	 */
	public void setGroupSlots(short s) {
		groupSlots = s;
	}

	/**
	 * Sets the homedir(chroot).
	 * @param home The homedir to set
	 */
	public void setHomeDirectory(String home) {
		this.home = home;
	}

	/**
	 * Sets the idleTime.
	 * @param idleTime The idleTime to set
	 */
	public void setIdleTime(int idleTime) {
		this.idleTime = idleTime;
	}

	/**
	 * Sets the lastAccessTime.
	 * @param lastAccessTime The lastAccessTime to set
	 */
	public void setLastAccessTime(long lastAccessTime) {
		this.lastAccessTime = lastAccessTime;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#setLastNuked(long)
	 */
	public void setLastNuked(long lastNuked) {
		this.lastNuked = lastNuked;
	}

	/**
	 * Sets the logins.
	 * @param logins The logins to set
	 */
	public void setLogins(int logins) {
		this.logins = logins;
	}

	/**
	 * Set user maximum download rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public void setMaxDownloadRate(int rate) {
		maxDownloadRate = rate;
	}

	/**
	 * Sets the maxLogins.
	 * @param maxLogins The maxLogins to set
	 */
	public void setMaxLogins(int maxLogins) {
		this.maxLogins = maxLogins;
	}

	/**
	 * Sets the maxLoginsPerIP.
	 * @param maxLoginsPerIP The maxLoginsPerIP to set
	 */
	public void setMaxLoginsPerIP(int maxLoginsPerIP) {
		this.maxLoginsPerIP = maxLoginsPerIP;
	}
	public void setMaxSimDownloads(int maxSimDownloads) {
		this.maxSimDownloads = maxSimDownloads;
	}

	/**
	 * Sets the maxSimUploads.
	 * @param maxSimUploads The maxSimUploads to set
	 */
	public void setMaxSimUploads(int maxSimUploads) {
		this.maxSimUploads = maxSimUploads;
	}

	/**
	 * Set user maximum upload rate limit.
	 * Less than or equal to zero means no limit.
	 */
	public void setMaxUploadRate(int rate) {
		maxUploadRate = rate;
	}

	/**
	 * Sets the nukedBytes.
	 * @param nukedBytes The nukedBytes to set
	 */
	public void setNukedBytes(long nukedBytes) {
		this.nukedBytes = nukedBytes;
	}

	/**
	 * Sets the ratio.
	 * @param ratio The ratio to set
	 */
	public void setRatio(float ratio) {
		this.ratio = ratio;
	}

	/**
	 * Sets the tagline.
	 * @param tagline The tagline to set
	 */
	public void setTagline(String tagline) {
		this.tagline = tagline;
	}

	/**
	 * Sets the timelimit.
	 * @param timelimit The timelimit to set
	 */
	public void setTimelimit(int timelimit) {
		this.timelimit = timelimit;
	}

	/**
	 * Sets the nuked.
	 * @param nuked The nuked to set
	 */
	public void setTimesNuked(int nuked) {
		this.timesNuked = nuked;
	}

	/**
	 * Sets the timeToday.
	 * @param timeToday The timeToday to set
	 */
	public void setTimeToday(long timeToday) {
		this.timeToday = timeToday;
	}

	public void setWeeklyAllotment(long weeklyAllotment) {
		this.weeklyAllotment = weeklyAllotment;
	}

	/** 
	 * String representation
	 */
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
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#updateDownloadedFiles(int)
	 */
	public void updateDownloadedFiles(int i) {
		this.downloadedFiles += i;
		this.downloadedFilesDay += i;
		this.downloadedFilesWeek += i;
		this.downloadedFilesMonth += i;
	}

	/**
	 * Hit user - update last access time
	 */
	public void updateLastAccessTime() {
		lastAccessTime = System.currentTimeMillis();
	}

	/**
	 * @param i
	 */
	private void updateLogins(int i) {
		logins += 1;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#updateNukedBytes(long)
	 */
	public void updateNukedBytes(long bytes) {
		this.nukedBytes += bytes;
	}
	public void updateTimesNuked(int timesNuked) {
		this.timesNuked += timesNuked;
	}

	public void updateUploadedBytes(long bytes) {
		this.uploadedBytes += bytes;
		this.uploadedBytesDay += bytes;
		this.uploadedBytesWeek += bytes;
		this.uploadedBytesMonth += bytes;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.usermanager.User#updateUploadedFiles(int)
	 */
	public void updateUploadedFiles(int i) {
		this.uploadedFiles += i;
		this.uploadedFilesDay += i;
		this.uploadedFilesWeek += i;
		this.uploadedFilesMonth += i;
	}
}
