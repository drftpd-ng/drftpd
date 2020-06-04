/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.stats;

import org.drftpd.master.cron.TimeEventInterface;

import java.util.Date;

/**
 * Implementation of the Stats interface that is also capable
 * of storing resetable stats.<br>
 * Extending this class allow an easy creation of any kinds of stats
 * releated to download/upload, for example a 'WeeklyStats' where you only
 * need to implement an actual code to resetWeek().
 *
 * @author fr0w
 * @see TimeEventInterface
 */
public abstract class AbstractTimedStats implements StatsInterface {
    /**
     * Reset the hour stats.
     *
     * @param date
     */
    public abstract void resetHour(Date date);

    /**
     * Reset the day stats.
     *
     * @param date
     */
    public abstract void resetDay(Date date);

    /**
     * Reset the week stats.
     *
     * @param date
     */
    public abstract void resetWeek(Date date);

    /**
     * Reset the month stats.
     *
     * @param date
     */
    public abstract void resetMonth(Date date);

    /**
     * Reset the year stats.
     *
     * @param date
     */
    public abstract void resetYear(Date date);
}
