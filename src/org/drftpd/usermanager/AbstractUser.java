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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.util.CalendarUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.plugins.Trial;


/**
 * Implements basic functionality for the User interface.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @author mog
 * @version $Id$
 */
public abstract class AbstractUser extends User {
    private static final Logger logger = Logger.getLogger(AbstractUser.class);
    public static final int P_ALL = 0;
    public static final int P_DAY = 3;
    public static final int P_MAX = 3;
    public static final int P_MONTH = 1;
    public static final int P_SIZE = 4;
    public static final int P_WEEK = 2;

    public static void checkValidGroupName(String group) {
        if ((group.indexOf(' ') != -1) || (group.indexOf(';') != -1)) {
            throw new IllegalArgumentException(
                "Groups cannot contain space or other illegal characters");
        }
    }
    private String _comment;
    protected long _created;
    private long _credits;
    protected KeyedMap<Key, Object> _data = new KeyedMap<Key, Object>();
   
    private long[] _downloadedBytes = new long[P_SIZE];

    //private int _downloadedFiles, _downloadedFiles[P_DAY],
    // _downloadedFiles[P_MONTH], _downloadedFiles[P_WEEK];
    private int[] _downloadedFiles = new int[P_SIZE];
    private long[] _downloadedMilliSeconds = new long[P_SIZE];

    //protected long _downloadedMilliSeconds,
    // _downloadedMilliSeconds[P_DAY],_downloadedMilliSeconds[P_MONTH],_downloadedMilliSeconds[P_WEEK];
    private String _group = "nogroup";
    private ArrayList<String> _groups = new ArrayList<String>();
    private HostMaskCollection _hostMasks = new HostMaskCollection();
    private int _idleTime = 0; // no limit
    private long _lastAccessTime = 0;

    //private long _lastNuked;

    /**
     * Protected for DummyUser b/c TrialTest
     */
    protected long _lastReset;

    //action counters
    private int _logins;

    //login limits
    private int _maxLogins;

    //login limits
    private int _maxLoginsPerIP;

    //private long _nukedBytes;
    //private int _racesLost;
    //private int _racesParticipated;
    //private int _racesWon;
    //private float _ratio = 3.0F;
    //private int _requests;
    //private int _requestsFilled;

    /*
     * Protected b/c of DummyUser which needs to set it for TrialTest
     */
    protected long[] _uploadedBytes = new long[P_SIZE];

    //protected long _uploadedBytes;
    //protected long _uploadedBytes[P_DAY];
    //protected long _uploadedBytes[P_MONTH];
    //protected long _uploadedBytes[P_WEEK];
    private int[] _uploadedFiles = new int[P_SIZE];

    //private long _uploadedMilliSeconds;
    //private long _uploadedMilliSeconds[P_DAY];
    //private long _uploadedMilliSeconds[P_MONTH];
    //private long _uploadedMilliSeconds[P_WEEK];
    private long[] _uploadedMilliSeconds = new long[P_SIZE];

    //    private int _uploadedFiles;
    //    private int _uploadedFiles[P_DAY];
    //    private int _uploadedFiles[P_MONTH];
    //    private int _uploadedFiles[P_WEEK];
    private String _username;
    private long _weeklyAllotment;

    public AbstractUser(String username) {
        _username = username;
        _created = System.currentTimeMillis();
    }

	public void addAllMasks(HostMaskCollection hostMaskCollection) {
        getHostMaskCollection().addAllMasks(hostMaskCollection);
    }

    public void addIPMask(String mask) throws DuplicateElementException {
        getHostMaskCollection().addMask(mask);
    }

    //    public void addRacesLost() {
    //        _racesLost++;
    //    }
    //
    //    public void addRacesParticipated() {
    //        _racesParticipated++;
    //    }
    //
    //    public void addRacesWon() {
    //        _racesWon++;
    //    }
    //
    //    public void addRequests() {
    //        _requests++;
    //    }
    //
    //    public void addRequestsFilled() {
    //        _requestsFilled++;
    //    }
    public void addSecondaryGroup(String group)
        throws DuplicateElementException {
        if (_groups.contains(group)) {
            throw new DuplicateElementException(
                "User is already a member of that group");
        }

        checkValidGroupName(group);
        _groups.add(group);
    }

    /*
     * public boolean checkIP(String[] masks, boolean useIdent) { Perl5Matcher m =
     * new Perl5Matcher();
     *
     * for (Iterator e2 = ipMasks.iterator(); e2.hasNext();) { String mask =
     * (String) e2.next();
     *
     * if (!useIdent) { mask = mask.substring(mask.indexOf('@') + 1);
     *
     * for (int i = 0; i < masks.length; i++) { masks[i] =
     * masks[i].substring(masks[i].indexOf('@') + 1); } }
     *
     * Pattern p;
     *
     * try { p = new GlobCompiler().compile(mask); } catch
     * (MalformedPatternException ex) { ex.printStackTrace();
     *
     * return false; }
     *
     * for (int i = 0; i < masks.length; i++) { if (m.matches(masks[i], p)) {
     * return true; } } }
     *
     * return false; }
     */
    public boolean equals(Object obj) {
        return (obj instanceof User)
        ? ((User) obj).getName().equals(getName()) : false;
    }

    /**
     * To avoid casting to AbstractUserManager
     */
    public abstract AbstractUserManager getAbstractUserManager();

    public long getCredits() {
        return _credits;
    }

    public long getDownloadedBytes() {
        return _downloadedBytes[P_ALL];
    }

    public long getDownloadedBytesDay() {
        return _downloadedBytes[P_DAY];
    }

    public long getDownloadedBytesForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return _downloadedBytes[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _downloadedBytes[P_MONTH];

        case Trial.PERIOD_WEEKLY:
            return _downloadedBytes[P_WEEK];

        case Trial.PERIOD_ALL:
            return _downloadedBytes[P_ALL];
        }

        throw new RuntimeException();
    }

    public long getDownloadedBytesMonth() {
        return _downloadedBytes[P_MONTH];
    }

    public long getDownloadedBytesWeek() {
        return _downloadedBytes[P_WEEK];
    }

    public int getDownloadedFiles() {
        return _downloadedFiles[P_ALL];
    }

    public int getDownloadedFilesDay() {
        return _downloadedFiles[P_DAY];
    }

    public int getDownloadedFilesForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return _downloadedFiles[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _downloadedFiles[P_MONTH];

        case Trial.PERIOD_WEEKLY:
            return _downloadedFiles[P_WEEK];

        case Trial.PERIOD_ALL:
            return _downloadedFiles[P_ALL];
        }

        throw new RuntimeException();
    }

    public int getDownloadedFilesMonth() {
        return _downloadedFiles[P_MONTH];
    }

    public int getDownloadedFilesWeek() {
        return _downloadedFiles[P_WEEK];
    }

    public long getDownloadedMillisecondsWeek() {
        return _downloadedMilliSeconds[P_WEEK];
    }

    public long getDownloadedTime() {
        return _downloadedMilliSeconds[P_ALL];
    }

    public long getDownloadedTimeForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return _downloadedMilliSeconds[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _downloadedMilliSeconds[P_MONTH];

        case Trial.PERIOD_WEEKLY:
            return _downloadedMilliSeconds[P_WEEK];

        case Trial.PERIOD_ALL:
            return _downloadedMilliSeconds[P_ALL];
        }

        throw new RuntimeException();
    }

    public String getGroup() {
        if (_group == null) {
            return "nogroup";
        }

        return _group;
    }

    public List getGroups() {
        return _groups;
    }
   
    public void setGroups(List<String> groups) {
    	_groups = new ArrayList<String>(groups);
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

    public KeyedMap<Key, Object> getKeyedMap() {
    	return _data;
    }
    public void setKeyedMap(KeyedMap<Key, Object> data) {
    	_data = data;
    }
    public long getLastAccessTime() {
        return _lastAccessTime;
    }

    public long getLastReset() {
        return _lastReset;
    }

    public void setLastReset(long lastReset) {
    	_lastReset = lastReset;
    }

    public int getLogins() {
        return _logins;
    }

    public int getMaxLogins() {
        return _maxLogins;
    }

    public int getMaxLoginsPerIP() {
        return _maxLoginsPerIP;
    }


    //    public int getRequests() {
    //        return _requests;
    //    }
    //
    //    public int getRequestsFilled() {
    //        return _requestsFilled;
    //    }
    public long getUploadedBytes() {
        return _uploadedBytes[P_ALL];
    }

    public long getUploadedBytesDay() {
        return _uploadedBytes[P_DAY];
    }

    public long getUploadedBytesForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return _uploadedBytes[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _uploadedBytes[P_MONTH];

        case Trial.PERIOD_WEEKLY:
            return _uploadedBytes[P_WEEK];

        case Trial.PERIOD_ALL:
            return _uploadedBytes[P_ALL];
        }

        throw new RuntimeException();
    }

    public long getUploadedBytesMonth() {
        return _uploadedBytes[P_MONTH];
    }

    public long getUploadedBytesWeek() {
        return _uploadedBytes[P_WEEK];
    }

    public int getUploadedFiles() {
        return _uploadedFiles[P_ALL];
    }

    public int getUploadedFilesDay() {
        return _uploadedFiles[P_DAY];
    }

    public int getUploadedFilesForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            return _uploadedFiles[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _uploadedFiles[P_MONTH];

        case Trial.PERIOD_WEEKLY:
            return _uploadedFiles[P_WEEK];

        case Trial.PERIOD_ALL:
            return _uploadedFiles[P_ALL];
        }

        throw new RuntimeException();
    }

    public int getUploadedFilesMonth() {
        return _uploadedFiles[P_MONTH];
    }

    public int getUploadedFilesWeek() {
        return _uploadedFiles[P_WEEK];
    }

    public long getUploadedTime() {
        return _uploadedMilliSeconds[P_ALL];
    }

    public long getUploadedTimeForTrialPeriod(int period) {
        switch (period) {
        case Trial.PERIOD_DAILY:

            //return _uploadedMilliSeconds[P_DAY];
            return _uploadedMilliSeconds[P_DAY];

        case Trial.PERIOD_MONTHLY:
            return _uploadedMilliSeconds[P_ALL];

        case Trial.PERIOD_WEEKLY:
            return _uploadedMilliSeconds[P_WEEK];

        case Trial.PERIOD_ALL:
            return _uploadedMilliSeconds[P_ALL];
        }

        throw new RuntimeException();
    }

    public String getName() {
        return _username;
    }

    public long getWeeklyAllotment() {
        return _weeklyAllotment;
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
            if (group.equals(iter.next())) {
                return true;
            }
        }

        return false;
    }

    public boolean isNuker() {
        return isMemberOf("nuke");
    }

    public void login() {
        _logins += 1;
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

    public void rename(String username)
        throws UserExistsException, UserFileException {
        getAbstractUserManager().rename(this, username); // throws ObjectExistsException
        getAbstractUserManager().delete(this.getName());
        _username = username;
        commit(); // throws IOException
    }

    public void reset(GlobalContext gctx) throws UserFileException {
        reset(gctx, Calendar.getInstance());
    }

    protected void reset(GlobalContext gctx, Calendar cal)
        throws UserFileException {
        //ignore reset() if we are called from userfileconverter or the like
        if (gctx == null) {
            return;
        }

        Date lastResetDate = new Date(_lastReset);
        _lastReset = cal.getTimeInMillis();

        //cal = Calendar.getInstance();
        Calendar calTmp = (Calendar) cal.clone();
        calTmp.add(Calendar.DAY_OF_MONTH, -1);
        CalendarUtils.ceilAllLessThanDay(calTmp);

        //has not been reset since midnight
        if (lastResetDate.before(calTmp.getTime())) {
            resetDay(gctx, cal.getTime());

            //floorDayOfWeek could go into the previous month
            calTmp = (Calendar) cal.clone();

            //calTmp.add(Calendar.WEEK_OF_YEAR, 1);
            CalendarUtils.floorDayOfWeek(calTmp);

            if (lastResetDate.before(calTmp.getTime())) {
                resetWeek(gctx, calTmp.getTime());
            }

            CalendarUtils.floorDayOfMonth(cal);

            if (lastResetDate.before(cal.getTime())) {
                resetMonth(gctx, cal.getTime());
            }

            commit(); //throws UserFileException
        }
    }

    private void resetDay(GlobalContext gctx, Date resetDate) {
        gctx.dispatchFtpEvent(new UserEvent(this, "RESETDAY", resetDate.getTime()));
        _downloadedFiles[P_DAY] = 0;
        _uploadedFiles[P_DAY] = 0;
        _downloadedMilliSeconds[P_DAY] = 0;
        _uploadedMilliSeconds[P_DAY] = 0;
        _downloadedBytes[P_DAY] = 0;
        _uploadedBytes[P_DAY] = 0;
        logger.info("Reset daily stats for " + getName());
    }

    private void resetMonth(GlobalContext gctx, Date resetDate) {
        gctx.dispatchFtpEvent(new UserEvent(this, "RESETMONTH",
                resetDate.getTime()));
        _downloadedFiles[P_MONTH] = 0;
        _uploadedFiles[P_MONTH] = 0;
        _downloadedMilliSeconds[P_MONTH] = 0;
        _uploadedMilliSeconds[P_MONTH] = 0;
        _downloadedBytes[P_MONTH] = 0;
        _uploadedBytes[P_MONTH] = 0;
        logger.info("Reset monthly stats for " + getName());
    }

    private void resetWeek(GlobalContext gctx, Date resetDate) {
        logger.info("Reset weekly stats for " + getName() + "(was " +
            Bytes.formatBytes(_uploadedBytes[P_WEEK]) + " UP and " +
            Bytes.formatBytes(_downloadedBytes[P_WEEK]) + " DOWN");
        gctx.dispatchFtpEvent(new UserEvent(this, "RESETWEEK", resetDate.getTime()));
        _downloadedFiles[P_WEEK] = 0;
        _uploadedFiles[P_WEEK] = 0;
        _downloadedMilliSeconds[P_WEEK] = 0;
        _uploadedMilliSeconds[P_WEEK] = 0;
        _downloadedBytes[P_WEEK] = 0;
        _uploadedBytes[P_WEEK] = 0;

        if (getWeeklyAllotment() > 0) {
            setCredits(getWeeklyAllotment());
        }
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

    public void setDownloadedBytes(long bytes) {
        _downloadedBytes[P_ALL] = bytes;
    }

    public void setDownloadedBytesDay(long bytes) {
        _downloadedBytes[P_DAY] = bytes;
    }

    public void setDownloadedBytesForTrialPeriod(int period, long bytes) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _downloadedBytes[P_DAY] = bytes;

            return;

        case Trial.PERIOD_MONTHLY:
            _downloadedBytes[P_MONTH] = bytes;

            return;

        case Trial.PERIOD_WEEKLY:
            _downloadedBytes[P_WEEK] = bytes;

            return;

        case Trial.PERIOD_ALL:
            _downloadedBytes[P_ALL] = bytes;

            return;
        }

        throw new RuntimeException();
    }

    public void setDownloadedBytesMonth(long bytes) {
        _downloadedBytes[P_MONTH] = bytes;
    }

    public void setDownloadedBytesWeek(long bytes) {
        _downloadedBytes[P_WEEK] = bytes;
    }

    public void setDownloadedFiles(int files) {
        _downloadedFiles[P_ALL] = files;
    }

    public void setDownloadedFilesDay(int files) {
        _downloadedFiles[P_DAY] = files;
    }

    public void setDownloadedFilesForTrialPeriod(int period, int files) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _downloadedFiles[P_DAY] = files;

            return;

        case Trial.PERIOD_MONTHLY:
            _downloadedFiles[P_MONTH] = files;

            return;

        case Trial.PERIOD_WEEKLY:
            _downloadedFiles[P_WEEK] = files;

            return;

        case Trial.PERIOD_ALL:
            _downloadedFiles[P_ALL] = files;

            return;
        }

        throw new RuntimeException();
    }

    public void setDownloadedFilesMonth(int files) {
        _downloadedFiles[P_MONTH] = files;
    }

    public void setDownloadedFilesWeek(int files) {
        _downloadedFiles[P_WEEK] = files;
    }

    public void setDownloadedSeconds(int millis) {
        _downloadedMilliSeconds[P_ALL] = millis;
    }

    public void setDownloadedSecondsDay(int millis) {
        _downloadedMilliSeconds[P_DAY] = millis;
    }

    public void setDownloadedSecondsMonth(int millis) {
        _downloadedMilliSeconds[P_MONTH] = millis;
    }

    public void setDownloadedSecondsWeek(int millis) {
        _downloadedMilliSeconds[P_WEEK] = millis;
    }

    public void setDownloadedTime(long millis) {
        _downloadedMilliSeconds[P_ALL] = millis;
    }

    public void setDownloadedTimeDay(long millis) {
        _downloadedMilliSeconds[P_DAY] = millis;
    }

    public void setDownloadedTimeForTrialPeriod(int period, long millis) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _downloadedMilliSeconds[P_DAY] = millis;

            return;

        case Trial.PERIOD_MONTHLY:
            _downloadedMilliSeconds[P_MONTH] = millis;

            return;

        case Trial.PERIOD_WEEKLY:
            _downloadedMilliSeconds[P_WEEK] = millis;

            return;

        case Trial.PERIOD_ALL:
            _downloadedMilliSeconds[P_ALL] = millis;

            return;
        }

        throw new RuntimeException();
    }

    public void setDownloadedTimeMonth(long millis) {
        _downloadedMilliSeconds[P_MONTH] = millis;
    }

    public void setDownloadedTimeWeek(long millis) {
        _downloadedMilliSeconds[P_WEEK] = millis;
    }

    public void setGroup(String g) {
        checkValidGroupName(g);
        _group = g;
    }

    public void setIdleTime(int idleTime) {
        _idleTime = idleTime;
    }

    public void setLastAccessTime(long lastAccessTime) {
        _lastAccessTime = lastAccessTime;
    }

    public void setLastNuked(long lastNuked) {
    }

    public void setLogins(int logins) {
        _logins = logins;
    }

    public void setMaxLogins(int maxLogins) {
        _maxLogins = maxLogins;
    }

    public void setMaxLoginsPerIP(int maxLoginsPerIP) {
        _maxLoginsPerIP = maxLoginsPerIP;
    }

    public void setUploadedBytes(long bytes) {
        _uploadedBytes[P_ALL] = bytes;
    }

    public void setUploadedBytesDay(long bytes) {
        _uploadedBytes[P_DAY] = bytes;
    }

    public void setUploadedBytesForTrialPeriod(int period, long bytes) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _uploadedBytes[P_DAY] = bytes;

            return;

        case Trial.PERIOD_MONTHLY:
            _uploadedBytes[P_MONTH] = bytes;

            return;

        case Trial.PERIOD_WEEKLY:
            _uploadedBytes[P_WEEK] = bytes;

            return;

        case Trial.PERIOD_ALL:
            _uploadedBytes[P_ALL] = bytes;

            return;
        }

        throw new RuntimeException();
    }

    public void setUploadedBytesMonth(long bytes) {
        _uploadedBytes[P_MONTH] = bytes;
    }

    public void setUploadedBytesWeek(long bytes) {
        _uploadedBytes[P_WEEK] = bytes;
    }

    public void setUploadedFiles(int files) {
        _uploadedFiles[P_ALL] = files;
    }

    public void setUploadedFilesDay(int files) {
        _uploadedFiles[P_DAY] = files;
    }

    public void setUploadedFilesForTrialPeriod(int period, int files) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _uploadedFiles[P_DAY] = files;

            return;

        case Trial.PERIOD_MONTHLY:
            _uploadedFiles[P_MONTH] = files;

            return;

        case Trial.PERIOD_WEEKLY:
            _uploadedFiles[P_WEEK] = files;

            return;

        case Trial.PERIOD_ALL:
            _uploadedFiles[P_ALL] = files;

            return;
        }

        throw new RuntimeException();
    }

    public void setUploadedFilesMonth(int files) {
        _uploadedFiles[P_MONTH] = files;
    }

    public void setUploadedFilesWeek(int files) {
        _uploadedFiles[P_WEEK] = files;
    }

    public void setUploadedTime(int millis) {
        _uploadedMilliSeconds[P_ALL] = millis;
    }

    public void setUploadedTime(long millis) {
        _uploadedMilliSeconds[P_ALL] = millis;
    }

    public void setUploadedTimeDay(int millis) {
        _uploadedMilliSeconds[P_DAY] = millis;
    }

    public void setUploadedTimeDay(long millis) {
        _uploadedMilliSeconds[P_DAY] = millis;
    }

    public void setUploadedTimeForTrialPeriod(int period, long millis) {
        switch (period) {
        case Trial.PERIOD_DAILY:
            _uploadedMilliSeconds[P_DAY] = millis;

            return;

        case Trial.PERIOD_MONTHLY:
            _uploadedMilliSeconds[P_MONTH] = millis;

            return;

        case Trial.PERIOD_WEEKLY:
            _uploadedMilliSeconds[P_WEEK] = millis;

            return;

        case Trial.PERIOD_ALL:
            _uploadedMilliSeconds[P_ALL] = millis;

            return;
        }

        throw new RuntimeException();
    }

    public void setUploadedTimeMonth(int millis) {
        _uploadedMilliSeconds[P_MONTH] = millis;
    }

    public void setUploadedTimeMonth(long millis) {
        _uploadedMilliSeconds[P_MONTH] = millis;
    }

    public void setUploadedTimeWeek(int millis) {
        _uploadedMilliSeconds[P_WEEK] = millis;
    }

    public void setUploadedTimeWeek(long millis) {
        _uploadedMilliSeconds[P_WEEK] = millis;
    }

    public void setWeeklyAllotment(long weeklyAllotment) {
        _weeklyAllotment = weeklyAllotment;
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

    public void updateDownloadedBytes(long bytes) {
        _downloadedBytes[P_ALL] += bytes;
        _downloadedBytes[P_DAY] += bytes;
        _downloadedBytes[P_WEEK] += bytes;
        _downloadedBytes[P_MONTH] += bytes;
    }

    public void updateDownloadedFiles(int i) {
        _downloadedFiles[P_ALL] += i;
        _downloadedFiles[P_DAY] += i;
        _downloadedFiles[P_WEEK] += i;
        _downloadedFiles[P_MONTH] += i;
    }

    public void updateDownloadedTime(long millis) {
        _downloadedMilliSeconds[P_ALL] += millis;
        _downloadedMilliSeconds[P_DAY] += millis;
        _downloadedMilliSeconds[P_WEEK] += millis;
        _downloadedMilliSeconds[P_MONTH] += millis;
    }

    /**
     * Hit user - update last access time
     */
    public void updateLastAccessTime() {
        _lastAccessTime = System.currentTimeMillis();
    }

    //    public void updateNukedBytes(long bytes) {
    //        _nukedBytes += bytes;
    //    }
    //
    //    public void updateTimesNuked(int timesNuked) {
    //        _timesNuked += timesNuked;
    //    }
    public void updateUploadedBytes(long bytes) {
        _uploadedBytes[P_ALL] += bytes;
        _uploadedBytes[P_DAY] += bytes;
        _uploadedBytes[P_WEEK] += bytes;
        _uploadedBytes[P_MONTH] += bytes;
    }

    public void updateUploadedFiles(int i) {
        _uploadedFiles[P_ALL] += i;
        _uploadedFiles[P_DAY] += i;
        _uploadedFiles[P_WEEK] += i;
        _uploadedFiles[P_MONTH] += i;
    }

    public void updateUploadedTime(long millis) {
        _uploadedMilliSeconds[P_ALL] += millis;
        _uploadedMilliSeconds[P_DAY] += millis;
        _uploadedMilliSeconds[P_WEEK] += millis;
        _uploadedMilliSeconds[P_MONTH] += millis;
    }

}
