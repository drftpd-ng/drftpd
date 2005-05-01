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
package org.drftpd.sections.conf;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.TimerTask;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.remotefile.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.StaticRemoteFile;
import org.drftpd.sections.SectionInterface;


/**
 * @author mog
 * @version $Id$
 */
public class DatedSection implements SectionInterface {
    // The code assumes that the following constants are in a increasing
    // sequence.
    static final int TOP_OF_TROUBLE = -1;
    static final int TOP_OF_MINUTE = 0;
    static final int TOP_OF_HOUR = 1;
    static final int HALF_DAY = 2;
    static final int TOP_OF_DAY = 3;
    static final int TOP_OF_WEEK = 4;
    static final int TOP_OF_MONTH = 5;

    // The gmtTimeZone is used only in computeCheckPeriod() method.
    static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");
    private static final Logger logger = Logger.getLogger(DatedSection.class);
    private String _basePath;
    private SimpleDateFormat _dateFormat;
    private SectionManager _mgr;
    private String _name;
    private String _now;
    private RollingCalendar rc = new RollingCalendar();

    public DatedSection(SectionManager mgr, int i, Properties p) {
        _mgr = mgr;
        _name = PropertyHelper.getProperty(p, i + ".name");
        _basePath = PropertyHelper.getProperty(p, i + ".path");
        _now = PropertyHelper.getProperty(p, i + ".now");

        if (!_basePath.endsWith("/")) {
            _basePath += "/";
        }

        _dateFormat = new SimpleDateFormat(PropertyHelper.getProperty(p, i +
                    ".dated"), Locale.getDefault());

        //rollingcalendar...
        int type = computeCheckPeriod();
        printPeriodicity(type);
        rc.setType(type);

        //end rollingcalendar...
        logger.debug("Rolling at " + rc.getNextCheckDate(new Date()));
        getGlobalContext().getTimer().schedule(new TimerTask() {
                public void run() {
                	try {
                		getFile();
                	} catch (Throwable t) {
                		logger.error("Catching Throwable in DatedSection TimerTask", t);
                	}
                }
            }, rc.getNextCheckDate(new Date()));
        getFile();
    }

    private GlobalContext getGlobalContext() {
        return _mgr.getGlobalContext();
    }

    public LinkedRemoteFileInterface getBaseFile() {
        try {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .lookupFile(_basePath);
        } catch (FileNotFoundException e) {
            return _mgr.getConnectionManager().getGlobalContext().getRoot()
                       .createDirectories(_basePath);
        }
    }

    public LinkedRemoteFileInterface getFile() {
        String dateDirPath = _dateFormat.format(new Date());
        //System.out.println(new Date());
        //System.out.println(dateDirPath);
        //System.out.println(_dateFormat.getCalendar());
        //System.out.println(_dateFormat.getTimeZone());
        _dateFormat.setTimeZone(TimeZone.getDefault());
        //System.out.println(_dateFormat.getTimeZone());

        try {
            return getBaseFile().lookupFile(dateDirPath);
        } catch (FileNotFoundException e) {
            LinkedRemoteFileInterface base = getBaseFile();
            LinkedRemoteFile dateDir = base.createDirectories(dateDirPath);

            try {
                base = base.getParentFile();
            } catch (FileNotFoundException e1) {
            }

            try {
                base.getFile(getName() + _now).delete();
            } catch (FileNotFoundException e2) {
            }

            base.addFile(new StaticRemoteFile(getName() + _now, null,
                    dateDir.getPath()));
            logger.info("Created " + dateDir.getPath() +
                " and created symlink");

            return dateDir;
        }
    }

    public Collection getFiles() {
        return getBaseFile().getDirectories();
    }

    public LinkedRemoteFileInterface getFirstDirInSection(
        LinkedRemoteFileInterface dir) {
        try {
            return FileUtils.getSubdirOfDirectory(getFile(), dir);
        } catch (FileNotFoundException e) {
            return dir;
        }
    }

    public String getName() {
        return _name;
    }

    public String getPath() {
        return getFile().getPath();
    }

    // This method computes the roll over period by looping over the
    // periods, starting with the shortest, and stopping when the r0 is
    // different from from r1, where r0 is the epoch formatted according
    // the datePattern (supplied by the user) and r1 is the
    // epoch+nextMillis(i) formatted according to datePattern. All date
    // formatting is done in GMT and not local format because the test
    // logic is based on comparisons relative to 1970-01-01 00:00:00
    // GMT (the epoch).
    int computeCheckPeriod() {
        RollingCalendar rollingCalendar = new RollingCalendar(gmtTimeZone,
                Locale.ENGLISH);

        // set sate to 1970-01-01 00:00:00 GMT
        Date epoch = new Date(0);
        /*if(datePattern != null) */ {
            for (int i = TOP_OF_MINUTE; i <= TOP_OF_MONTH; i++) {
                SimpleDateFormat simpleDateFormat = (SimpleDateFormat) _dateFormat.clone();
                simpleDateFormat.setTimeZone(gmtTimeZone); // do all date formatting in GMT

                String r0 = simpleDateFormat.format(epoch);
                rollingCalendar.setType(i);

                Date next = new Date(rollingCalendar.getNextCheckMillis(epoch));
                String r1 = simpleDateFormat.format(next);

                //System.out.println("Type = "+i+", r0 = "+r0+", r1 = "+r1);
                if ((r0 != null) && (r1 != null) && !r0.equals(r1)) {
                    return i;
                }
            }
        }

        return TOP_OF_TROUBLE; // Deliberately head for trouble...
    }

    void printPeriodicity(int type) {
        switch (type) {
        case TOP_OF_MINUTE:
            logger.debug("DatedSection [" + _name +
                "] to be rolled every minute.");

            break;

        case TOP_OF_HOUR:
            logger.debug("DatedSection [" + _name +
                "] to be rolled on top of every hour.");

            break;

        case HALF_DAY:
            logger.debug("DatedSection [" + _name +
                "] to be rolled at midday and midnight.");

            break;

        case TOP_OF_DAY:
            logger.debug("DatedSection [" + _name +
                "] to be rolled at midnight.");

            break;

        case TOP_OF_WEEK:
            logger.debug("DatedSection [" + _name +
                "] to be rolled at start of week.");

            break;

        case TOP_OF_MONTH:
            logger.debug("DatedSection [" + _name +
                "] to be rolled at start of every month.");

            break;

        default:
            logger.warn("Unknown periodicity for DatedSection [" + _name +
                "].");
        }
    }

	public String getBasePath() {
		return _basePath;
	}
}


/**
 *  RollingCalendar is a helper class to DailyRollingFileAppender.
 *  Given a periodicity type and the current time, it computes the
 *  start of the next interval.
 * */
class RollingCalendar extends GregorianCalendar {
    int _type = DatedSection.TOP_OF_TROUBLE;

    RollingCalendar() {
        super();
    }

    RollingCalendar(TimeZone tz, Locale locale) {
        super(tz, locale);
    }

    void setType(int type) {
        _type = type;
    }

    public long getNextCheckMillis(Date now) {
        return getNextCheckDate(now).getTime();
    }

    public Date getNextCheckDate(Date now) {
        this.setTime(now);

        switch (_type) {
        case DatedSection.TOP_OF_MINUTE:
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);
            this.add(Calendar.MINUTE, 1);

            break;

        case DatedSection.TOP_OF_HOUR:
            this.set(Calendar.MINUTE, 0);
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);
            this.add(Calendar.HOUR_OF_DAY, 1);

            break;

        case DatedSection.HALF_DAY:
            this.set(Calendar.MINUTE, 0);
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);

            int hour = get(Calendar.HOUR_OF_DAY);

            if (hour < 12) {
                this.set(Calendar.HOUR_OF_DAY, 12);
            } else {
                this.set(Calendar.HOUR_OF_DAY, 0);
                this.add(Calendar.DAY_OF_MONTH, 1);
            }

            break;

        case DatedSection.TOP_OF_DAY:
            this.set(Calendar.HOUR_OF_DAY, 0);
            this.set(Calendar.MINUTE, 0);
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);
            this.add(Calendar.DATE, 1);

            break;

        case DatedSection.TOP_OF_WEEK:
            this.set(Calendar.DAY_OF_WEEK, getFirstDayOfWeek());
            this.set(Calendar.HOUR_OF_DAY, 0);
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);
            this.add(Calendar.WEEK_OF_YEAR, 1);

            break;

        case DatedSection.TOP_OF_MONTH:
            this.set(Calendar.DATE, 1);
            this.set(Calendar.HOUR_OF_DAY, 0);
            this.set(Calendar.SECOND, 0);
            this.set(Calendar.MILLISECOND, 0);
            this.add(Calendar.MONTH, 1);

            break;

        default:
            throw new IllegalStateException("Unknown periodicity type.");
        }

        return getTime();
    }
}
