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
package org.drftpd.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.util.Calendar;

/**
 * @author mog
 * @version $Id$
 */
public class CalendarUtils {
	private static final Logger logger = LogManager.getLogger(CalendarUtils.class);

	private CalendarUtils() {
	}

	public static void floorAllLessThanDay(Calendar cal) {
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 0);
	}

	public static void ceilAllLessThanDay(Calendar cal) {
		cal.set(Calendar.MILLISECOND, cal
				.getActualMaximum(Calendar.MILLISECOND));
		cal.set(Calendar.SECOND, cal.getActualMaximum(Calendar.SECOND));
		cal.set(Calendar.MINUTE, cal.getActualMaximum(Calendar.MINUTE));
		cal.set(Calendar.HOUR_OF_DAY, cal
				.getActualMaximum(Calendar.HOUR_OF_DAY));
	}

	public static void floorDayOfWeek(Calendar cal) {
		// workaround for bug in Calendar
		// see http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=4967383
		cal.get(Calendar.WEEK_OF_MONTH);

		cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());

		if (cal.get(Calendar.DAY_OF_WEEK) != cal.getFirstDayOfWeek()) {
            logger.error("cal.set(DAY_OF_WEEK) didn't work! {}", cal.getTime());
		}
	}

	public static void floorDayOfMonth(Calendar cal) {
		cal.set(Calendar.DAY_OF_MONTH, cal
				.getActualMinimum(Calendar.DAY_OF_MONTH));
	}

	/**
	 * Increments month of Calendar by one month.
	 * 
	 * If the following month does not have enough days, the first day in the
	 * month after the following month is used.
	 */
	public static void incrementMonth(Calendar cal) {
		Calendar cal2 = (Calendar) cal.clone();
		floorDayOfMonth(cal2);
		cal2.add(Calendar.MONTH, 1);

		if (cal.get(Calendar.DAY_OF_MONTH) > cal2
				.getActualMaximum(Calendar.DAY_OF_MONTH)) {
			floorDayOfMonth(cal);
			cal.add(Calendar.MONTH, 2);
		}

		cal.add(Calendar.MONTH, 1);
	}

	public static void incrementWeek(Calendar cal) {
		cal.add(Calendar.WEEK_OF_YEAR, 1);
	}

	public static void incrementDay(Calendar cal) {
		cal.add(Calendar.DAY_OF_MONTH, 1);
	}

	public static int getLastDayOfWeek(Calendar cal) {
		int dow = cal.getFirstDayOfWeek() - 1;

		if (dow == 0) {
			dow = Calendar.SATURDAY;
		}

		return dow;
	}
}
