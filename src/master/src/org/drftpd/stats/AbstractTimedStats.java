package org.drftpd.stats;

import java.util.Date;

/**
 * Implementation of the Stats interface that is also capable
 * of storing resetable stats.<br>
 * Extending this class allow an easy creation of any kinds of stats
 * releated to download/upload, for example a 'WeeklyStats' where you only
 * need to implement an actual code to resetWeek().
 * @see org.drftpd.master.cron.TimeEventInterface
 * @author fr0w
 */
public abstract class AbstractTimedStats implements StatsInterface {
	/**
	 * Reset the hour stats.
	 * @param date
	 */
	public abstract void resetHour(Date date);
	
	/**
	 * Reset the day stats.
	 * @param date
	 */
	public abstract void resetDay(Date date);
	
	/**
	 * Reset the week stats.
	 * @param date
	 */
	public abstract void resetWeek(Date date);
	
	/**
	 * Reset the month stats.
	 * @param date
	 */
	public abstract void resetMonth(Date date);
	
	/**
	 * Reset the year stats.
	 * @param date
	 */
	public abstract void resetYear(Date date);
}
