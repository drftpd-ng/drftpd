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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mog
 * @version $Id$
 */
public class DatedSection extends PlainSection implements TimeEventInterface {
	// The code assumes that the following constants are in a increasing sequence.
	protected static final int TOP_OF_TROUBLE = -1;
	protected static final int TOP_OF_MINUTE = 0;
	protected static final int TOP_OF_HOUR = 1;
	protected static final int HALF_DAY = 2;
	protected static final int TOP_OF_DAY = 3;
	protected static final int TOP_OF_WEEK = 4;
	protected static final int TOP_OF_MONTH = 5;
	protected static final int TOP_OF_YEAR = 6;

	// The gmtTimeZone is used only in computeCheckPeriod() method.
	private static final TimeZone gmtTimeZone = TimeZone.getTimeZone("GMT");

	private static final Logger logger = LogManager.getLogger(DatedSection.class);

	private SimpleDateFormat _dateFormat;

	private String _now;

	private RollingCalendar rc = new RollingCalendar();

	public DatedSection(int i, Properties p) {
		super(i, p);
		_now = PropertyHelper.getProperty(p, i + ".now");
		_dateFormat = new SimpleDateFormat(PropertyHelper.getProperty(p, i+ ".dated"), Locale.getDefault());

		// rollingcalendar...
		int type = computeCheckPeriod();
		printPeriodicity(type);
		rc.setType(type);

		// end rollingcalendar...
        logger.debug("Configured to roll at {}", rc.getNextCheckDate(new Date()));
		getGlobalContext().addTimeEvent(this);
	}

	public DirectoryHandle getCurrentDirectory() {
		String dateDirPath = _dateFormat.format(new Date());
		return getBaseDirectory().getNonExistentDirectoryHandle(dateDirPath);
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
		/* if(datePattern != null) */{
			for (int i = TOP_OF_MINUTE; i <= TOP_OF_YEAR; i++) {
				SimpleDateFormat simpleDateFormat = (SimpleDateFormat) _dateFormat
						.clone();
				simpleDateFormat.setTimeZone(gmtTimeZone); // do all date
															// formatting in GMT

				String r0 = simpleDateFormat.format(epoch);
				rollingCalendar.setType(i);

				Date next = new Date(rollingCalendar.getNextCheckMillis(epoch));
				String r1 = simpleDateFormat.format(next);

				// System.out.println("Type = "+i+", r0 = "+r0+", r1 = "+r1);
				if ((r0 != null) && (r1 != null) && !r0.equals(r1)) {
					return i;
				}
			}
		}

		return TOP_OF_TROUBLE; // Deliberately head for trouble...
	}

	private void printPeriodicity(int type) {
		switch (type) {
		case TOP_OF_MINUTE:
            logger.debug("DatedSection [{}] to be rolled every minute.", getName());
			break;

		case TOP_OF_HOUR:
            logger.debug("DatedSection [{}] to be rolled on top of every hour.", getName());
			break;

		case HALF_DAY:
            logger.debug("DatedSection [{}] to be rolled at midday and midnight.", getName());
			break;

		case TOP_OF_DAY:
            logger.debug("DatedSection [{}] to be rolled at midnight.", getName());
			break;

		case TOP_OF_WEEK:
            logger.debug("DatedSection [{}] to be rolled at start of week.", getName());
			break;

		case TOP_OF_MONTH:
            logger.debug("DatedSection [{}] to be rolled at start of every month.", getName());
			break;

		case TOP_OF_YEAR:
            logger.debug("DatedSection [{}] to be rolled at start of new year.", getName());
			break;			

		default:
            logger.warn("Unknown periodicity for DatedSection [{}].", getName());
		}
	}
	
	public void processNewDate(Date d) {
		String dateDirName = _dateFormat.format(new Date());
		if (!getBaseDirectory().exists()) {
            logger.debug("Section directory was not found while creatingdated directory: {}, creating it.", dateDirName);
			try {
				getBaseDirectory().getParent().createDirectoryRecursive(getBaseDirectory().getName(), true);
			} catch (FileExistsException e) {
				// this is good, continue
			} catch (FileNotFoundException e) {
                logger.error("Unable to create base directory for section {}", getName(), e);
				return;
			}
		}
		
		// create the directory
		DirectoryHandle newDir = null;
		try {
			newDir = getBaseDirectory().getDirectoryUnchecked(dateDirName);
		} catch (FileNotFoundException e) {
			// this is good
		} catch (ObjectNotValidException e) {
			logger.error("There is already a non-Directory inode in the place" +
					"where the new dated directory should go. Remove it first", e);
			return;
		}
		
		if (newDir == null) { // this is good, this is the standard process
			try {
				DirectoryHandle checkDir = getBaseDirectory().getNonExistentDirectoryHandle(dateDirName);
				if ((checkDir.getParent() != getBaseDirectory()) && (!checkDir.getParent().isRoot())) {
					try {
						checkDir.getParent().getParent().createDirectoryRecursive(checkDir.getParent().getName(), true);
					} catch (FileExistsException e) {
						// Creating Dir Recursively - Ignore if it already exists
					}
				}
				newDir = getBaseDirectory().createDirectoryUnchecked(checkDir.getName(), "drftpd", "drftpd");
			} catch (FileExistsException e) {
                logger.error("{} already exists in section {}, this should not happen, we just checked it", dateDirName, getName(), e);
				return;
			} catch (FileNotFoundException e) {
                logger.error("{} base directory does not exist for section {}, this should not happen, we just verified it existed", dateDirName, getName(), e);
				return;
			}
		} else {
            logger.warn("DatedDirectory {} already exists in section {}", dateDirName, getName());
			return;
		}
		createLink(newDir);
    }

	private void createLink(DirectoryHandle targetDir) {
		// creating the symlink
		if (_now == null || _now.equals("")) {
			return;
		}
		String linkName = getName() + _now;
		DirectoryHandle root = getGlobalContext().getRoot();
		LinkHandle link = null;
		try {
			link = root.getLinkUnchecked(linkName);
		} catch (FileNotFoundException e) {
			// this is okay, the link was deleted, we will recreate it below
		} catch (ObjectNotValidException e) {
			logger.error("There is already a non-Link inode in the place" +
					"where the new dated directory should go. Remove it first", e);
			return;
		}
		if (link != null) {
			try {
				link.setTarget(targetDir.getPath());
				link.setLastModified(System.currentTimeMillis());
				return;
				// link's target path has been updated
			} catch (FileNotFoundException e) {
				// will be created below
			}
		}
		try {
			root.createLinkUnchecked(linkName, targetDir.getPath(), "drftpd", "drftpd");
		} catch (FileExistsException e) {
            logger.error("{} already exists in / for section {}, this should not happen, we just deleted it", linkName, getName(), e);
		} catch (FileNotFoundException e) {
			logger.error("Unable to find the Root DirectoryHandle, this should not happen, it's the root!", e);
		}
	}

	@Override
	public void createSectionDir() {
		super.createSectionDir();
		createLink(getCurrentDirectory());
	}

	public void resetDay(Date d) {
		if (rc._type == TOP_OF_DAY) {
			processNewDate(d);
		}
		resetHour(d);
	}

	public void resetHour(Date d) {
		if (rc._type == TOP_OF_HOUR) {
			processNewDate(d);
		} else if (rc._type == HALF_DAY) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(d);
			if (cal.get(Calendar.HOUR_OF_DAY) == 12) {
				processNewDate(d);
			}
		}
	}

	public void resetMonth(Date d) {
		if (rc._type == TOP_OF_MONTH) {
			processNewDate(d);
		}
		// must do this to conform to TimeEventInterface
		resetDay(d);
		resetHour(d);
	}

	public void resetWeek(Date d) {
		if (rc._type == TOP_OF_WEEK) {
			processNewDate(d);
		}
	}

	public void resetYear(Date d) {
		if (rc._type == TOP_OF_YEAR) {
			processNewDate(d);
		}
		// must do this to conform to TimeEventInterface
		resetMonth(d);
		resetDay(d);
		resetHour(d);
	}
	
    public SimpleDateFormat getDateFormat() {
    	return _dateFormat;
    }
}

/**
 * RollingCalendar is a helper class to DailyRollingFileAppender. Given a
 * periodicity type and the current time, it computes the start of the next
 * interval.
 */
@SuppressWarnings("serial")
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

		case DatedSection.TOP_OF_YEAR:
			this.set(Calendar.DATE, 1);
			this.set(Calendar.HOUR_OF_DAY, 0);
			this.set(Calendar.SECOND, 0);
			this.set(Calendar.MILLISECOND, 0);
			this.set(Calendar.MONTH, 1);
			this.add(Calendar.YEAR, 1);

			break;
			
		default:
			throw new IllegalStateException("Unknown periodicity type.");
		}

		return getTime();
	}
}
