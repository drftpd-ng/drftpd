package net.sf.drftpd.master.usermanager;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * Implements basic functionality for the User interface. 
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author mog
 * @version $Id: AbstractUser.java,v 1.22 2003/11/13 22:55:06 mog Exp $
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
		return false;
	}

	public String getComment() {
		return comment;
	}

	public long getCreated() {
		return created;
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

	public int getDownloadedFilesMonth() {
		return downloadedFilesMonth;
	}

	public int getDownloadedFilesWeek() {
		return downloadedFilesWeek;
	}

	public int getDownloadedSeconds() {
		return downloadedSeconds;
	}

	public int getDownloadedSecondsDay() {
		return downloadedSecondsDay;
	}

	public int getDownloadedSecondsMonth() {
		return downloadedSecondsMonth;
	}

	public int getDownloadedSecondsWeek() {
		return downloadedSecondsWeek;
	}

	public short getGroupLeechSlots() {
		return groupLeechSlots;
	}

	public String getGroupName() {
		if (_group == null)
			return "nogroup";
		return _group;
	}

	public Collection getGroups() {
		return groups;
	}

	public short getGroupSlots() {
		return groupSlots;
	}

	public String getHomeDirectory() {
		return home;
	}

	public int getIdleTime() {
		return idleTime;
	}

	public Collection getIpMasks() {
		return ipMasks;
	}

	public long getLastAccessTime() {
		return lastAccessTime;
	}

	public long getLastNuked() {
		return lastNuked;
	}

	public long getLastReset() {
		return lastReset;
	}

	public int getLogins() {
		return logins;
	}

	public int getMaxDownloadRate() {
		return maxDownloadRate;
	}

	public int getMaxLogins() {
		return maxLogins;
	}

	public int getMaxLoginsPerIP() {
		return maxLoginsPerIP;
	}

	public int getMaxSimDownloads() {
		return maxSimDownloads;
	}

	public int getMaxSimUploads() {
		return maxSimUploads;
	}

	public int getMaxUploadRate() {
		return maxUploadRate;
	}

	public long getNukedBytes() {
		return nukedBytes;
	}

	public float getRatio() {
		return ratio;
	}

	public String getTagline() {
		return tagline;
	}

	public int getTimelimit() {
		return timelimit;
	}

	public int getTimesNuked() {
		return timesNuked;
	}

	public long getTimeToday() {
		return timeToday;
	}

	public long getUploadedBytes() {
		return uploadedBytes;
	}

	public long getUploadedBytesDay() {
		return uploadedBytesDay;
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

	public int getUploadedFilesMonth() {
		return uploadedFilesMonth;
	}

	public int getUploadedFilesWeek() {
		return uploadedFilesWeek;
	}

	public int getUploadedSeconds() {
		return uploadedSeconds;
	}

	public int getUploadedSecondsDay() {
		return uploadedSecondsDay;
	}

	public int getUploadedSecondsMonth() {
		return uploadedSecondsMonth;
	}

	public int getUploadedSecondsWeek() {
		return uploadedSecondsWeek;
	}

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
	
	public boolean isAnonymous() {
		return anonymous;
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

	public void login() {
		updateLogins(1);
	}

	public void logout() {
	}

	public void removeGroup(String group) throws NoSuchFieldException {
		if (!groups.remove(group))
			throw new NoSuchFieldException("User is not a member of that group");
	}

	public void removeIpMask(String mask) throws NoSuchFieldException {
		if (!ipMasks.remove(mask))
			throw new NoSuchFieldException("User has no such ip mask");
	}

	public void reset(ConnectionManager cmgr) {
		//handle if we are called from userfileconverter or the like
		if (cmgr == null ) return;
		
		Date lastResetDate = new Date(this.lastReset);

		Calendar cal = Calendar.getInstance();
		
		CalendarUtils.floorAllLessThanDay(cal);

		//has not been reset since midnight
		if (lastResetDate.before(cal.getTime()))
			resetDay(cmgr, cal.getTime());

		//floorDayOfWeek could go into the previous month
		Calendar cal2 = (Calendar)cal.clone();
		CalendarUtils.floorDayOfWeek(cal2);
		if (lastResetDate.before(cal2.getTime()))
			resetWeek(cmgr, cal.getTime());
			
		CalendarUtils.floorDayOfMonth(cal);
		if (lastResetDate.before(cal.getTime()))
			resetMonth(cmgr, cal.getTime());

		lastReset = System.currentTimeMillis();
	}

	private void resetDay(ConnectionManager cm, Date resetDate) {
		logger.info("Reset daily stats for " + getUsername());
		cm.dispatchFtpEvent(new UserEvent(this, "RESETDAY", resetDate.getTime()));

		this.downloadedFilesDay = 0;
		this.uploadedBytesDay = 0;

		this.downloadedSecondsDay = 0;
		this.uploadedSecondsDay = 0;

		this.downloadedBytesDay = 0;
		this.uploadedBytesDay = 0;
		this.timeToday = 0;
	}

	private void resetMonth(ConnectionManager cm, Date resetDate) {
		cm.dispatchFtpEvent(new UserEvent(this, "RESETMONTH", resetDate.getTime()));
		logger.info("Reset monthly stats for " + getUsername());

		this.downloadedFilesMonth = 0;
		this.uploadedBytesMonth = 0;

		this.downloadedSecondsMonth = 0;
		this.uploadedSecondsMonth = 0;

		this.downloadedBytesMonth = 0;
		this.uploadedBytesMonth = 0;
	}

	private void resetWeek(ConnectionManager cm, Date resetDate) {
		cm.dispatchFtpEvent(new UserEvent(this, "RESETWEEK", resetDate.getTime()));
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

	public void setCredits(long credits) {
		this.credits = credits;
	}

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

	public void setGroup(String group) {
		_group = group;
	}

	public void setGroupLeechSlots(short s) {
		groupLeechSlots = s;
	}

	public void setGroupSlots(short s) {
		groupSlots = s;
	}

	public void setHomeDirectory(String home) {
		this.home = home;
	}

	public void setIdleTime(int idleTime) {
		this.idleTime = idleTime;
	}

	public void setLastAccessTime(long lastAccessTime) {
		this.lastAccessTime = lastAccessTime;
	}

	public void setLastNuked(long lastNuked) {
		this.lastNuked = lastNuked;
	}

	public void setLogins(int logins) {
		this.logins = logins;
	}

	/**
	 * Set user maximum download rate limit.
	 * Less than or equal to zero means no limit.
	 * @deprecated not implemented
	 */
	public void setMaxDownloadRate(int rate) {
		maxDownloadRate = rate;
	}

	public void setMaxLogins(int maxLogins) {
		this.maxLogins = maxLogins;
	}

	public void setMaxLoginsPerIP(int maxLoginsPerIP) {
		this.maxLoginsPerIP = maxLoginsPerIP;
	}
	
	public void setMaxSimDownloads(int maxSimDownloads) {
		this.maxSimDownloads = maxSimDownloads;
	}

	public void setMaxSimUploads(int maxSimUploads) {
		this.maxSimUploads = maxSimUploads;
	}

	public void setMaxUploadRate(int rate) {
		maxUploadRate = rate;
	}

	public void setNukedBytes(long nukedBytes) {
		this.nukedBytes = nukedBytes;
	}

	public void setRatio(float ratio) {
		this.ratio = ratio;
	}

	public void setTagline(String tagline) {
		this.tagline = tagline;
	}

	public void setTimelimit(int timelimit) {
		this.timelimit = timelimit;
	}

	public void setTimesNuked(int nuked) {
		this.timesNuked = nuked;
	}

	public void setTimeToday(long timeToday) {
		this.timeToday = timeToday;
	}

	public void setWeeklyAllotment(long weeklyAllotment) {
		this.weeklyAllotment = weeklyAllotment;
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

	/**
	 * Hit user - update last access time
	 */
	public void updateLastAccessTime() {
		lastAccessTime = System.currentTimeMillis();
	}

	private void updateLogins(int i) {
		logins += 1;
	}

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

	public void updateUploadedFiles(int i) {
		this.uploadedFiles += i;
		this.uploadedFilesDay += i;
		this.uploadedFilesWeek += i;
		this.uploadedFilesMonth += i;
	}
}
