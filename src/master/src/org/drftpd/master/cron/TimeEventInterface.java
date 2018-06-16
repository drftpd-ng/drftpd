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
package org.drftpd.master.cron;

import java.util.Date;

/**
 * There are 5 methods defined in this interface.
 * 4 of the methods are tied together: hour, day, month, and year
 * Week is excluded
 * When processing these methods, only the largest change method will be called
 * E.g., Jan 3rd, 12:00 AM, ONLY resetDay() is called
 * E.g., Jan 1st, 12:00 AM, ONLY resetYear() is called
 * E.g., Jan 2nd, 2:00 PM, ONLY resetHour() is called
 * On top of this, when a week resets, it will be called as well
 * So in resetMonth() in your interface, most will make sure it calls resetDay()
 * @author zubov
 */
public interface TimeEventInterface {

	void resetDay(Date d);
	
	void resetWeek(Date d);
	
	void resetMonth(Date d);
	
	void resetYear(Date d);
	
	void resetHour(Date d);

}
