package net.sf.drftpd.util;

import java.util.Calendar;

/**
 * @author mog
 * @version $Id: CalendarUtils.java,v 1.4 2004/01/08 05:37:47 mog Exp $
 */
public class CalendarUtils {
	private CalendarUtils() {
	}

	public static void floorAllLessThanDay(Calendar cal) {
		cal.set(Calendar.MILLISECOND, 0);
		cal.set(Calendar.SECOND, 0);
		cal.set(Calendar.MINUTE, 0);
		cal.set(Calendar.HOUR_OF_DAY, 0);
	}

	public static void ceilAllLessThanDay(Calendar cal) {
		cal.set(
			Calendar.MILLISECOND,
			cal.getActualMaximum(Calendar.MILLISECOND));
		cal.set(Calendar.SECOND, cal.getActualMaximum(Calendar.SECOND));
		cal.set(Calendar.MINUTE, cal.getActualMaximum(Calendar.MINUTE));
		cal.set(
			Calendar.HOUR_OF_DAY,
			cal.getActualMaximum(Calendar.HOUR_OF_DAY));
	}

	public static void floorDayOfWeek(Calendar cal) {
		cal.set(Calendar.DAY_OF_WEEK, cal.getFirstDayOfWeek());
	}

	public static void floorDayOfMonth(Calendar cal) {
		cal.set(
			Calendar.DAY_OF_MONTH,
			cal.getActualMinimum(Calendar.DAY_OF_MONTH));
	}

	/**
	 * Increments month of Calendar by one month.
	 * 
	 * If the following month does not have enough days,
	 * the first day in the month after the following month is used.
	 */
	public static void incrementMonth(Calendar cal) {
		Calendar cal2 = (Calendar) cal.clone();
		floorDayOfMonth(cal2);
		cal2.add(Calendar.MONTH, 1);
		if (cal.get(Calendar.DAY_OF_MONTH)
			> cal2.getActualMaximum(Calendar.DAY_OF_MONTH)) {
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
		int dow = cal.getFirstDayOfWeek()-1;
		if(dow == 0) dow = Calendar.SATURDAY;
		return dow;
	}
}
