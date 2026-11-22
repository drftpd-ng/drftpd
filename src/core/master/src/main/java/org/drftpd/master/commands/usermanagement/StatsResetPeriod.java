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
package org.drftpd.master.commands.usermanagement;

import org.drftpd.master.stats.ExtendedTimedStats;

import java.util.Locale;

/**
 * The periods that {@code SITE RESET} understands, and the stats periods each of them zeroes.
 * <p>
 * Only {@link #ALL} touches the all-time stats, the other values only reset their own period.
 */
public enum StatsResetPeriod {
    DAY(ExtendedTimedStats.P_DAY),
    WEEK(ExtendedTimedStats.P_WEEK),
    MONTH(ExtendedTimedStats.P_MONTH),
    ALL(ExtendedTimedStats.P_DAY, ExtendedTimedStats.P_WEEK, ExtendedTimedStats.P_MONTH, ExtendedTimedStats.P_ALL);

    private final int[] _periods;

    StatsResetPeriod(int... periods) {
        _periods = periods;
    }

    /**
     * @return the {@link ExtendedTimedStats} periods zeroed by this value
     */
    public int[] getPeriods() {
        return _periods.clone();
    }

    /**
     * Zeroes the upload and download stats of every period covered by this value.
     * Credits, ratio and everything else that is not a transfer stat is left untouched.
     */
    public void reset(ExtendedTimedStats stats) {
        for (int p : _periods) {
            stats.resetStatsForPeriod(p);
        }
    }

    /**
     * @param name case insensitive period name
     * @return the matching period, or null if the name is not a known period
     */
    public static StatsResetPeriod parse(String name) {
        if (name == null) {
            return null;
        }
        try {
            return valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public String lowerName() {
        return name().toLowerCase(Locale.ROOT);
    }
}
