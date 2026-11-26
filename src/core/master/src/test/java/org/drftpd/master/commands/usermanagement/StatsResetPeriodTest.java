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
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;

public class StatsResetPeriodTest {

    static class Stats extends ExtendedTimedStats {
        @Override public void resetHour(Date date) { }
        @Override public void resetDay(Date date) { }
        @Override public void resetWeek(Date date) { }
        @Override public void resetMonth(Date date) { }
        @Override public void resetYear(Date date) { }
    }

    private static Stats filled() {
        Stats stats = new Stats();
        stats.updateUploadedBytes(1000L);
        stats.updateUploadedFiles(2);
        stats.updateUploadedTime(300L);
        stats.updateDownloadedBytes(4000L);
        stats.updateDownloadedFiles(5);
        stats.updateDownloadedTime(600L);
        return stats;
    }

    private static void assertZero(Stats stats, int p) {
        assertEquals(0L, stats.getUploadedBytesForPeriod(p));
        assertEquals(0, stats.getUploadedFilesForPeriod(p));
        assertEquals(0L, stats.getUploadedTimeForPeriod(p));
        assertEquals(0L, stats.getDownloadedBytesForPeriod(p));
        assertEquals(0, stats.getDownloadedFilesForPeriod(p));
        assertEquals(0L, stats.getDownloadedTimeForPeriod(p));
    }

    private static void assertUntouched(Stats stats, int p) {
        assertEquals(1000L, stats.getUploadedBytesForPeriod(p));
        assertEquals(2, stats.getUploadedFilesForPeriod(p));
        assertEquals(300L, stats.getUploadedTimeForPeriod(p));
        assertEquals(4000L, stats.getDownloadedBytesForPeriod(p));
        assertEquals(5, stats.getDownloadedFilesForPeriod(p));
        assertEquals(600L, stats.getDownloadedTimeForPeriod(p));
    }

    @Test
    public void parseIsCaseInsensitive() {
        assertEquals(StatsResetPeriod.DAY, StatsResetPeriod.parse("day"));
        assertEquals(StatsResetPeriod.WEEK, StatsResetPeriod.parse("WEEK"));
        assertEquals(StatsResetPeriod.MONTH, StatsResetPeriod.parse("Month"));
        assertEquals(StatsResetPeriod.ALL, StatsResetPeriod.parse(" all "));
    }

    @Test
    public void parseRejectsUnknown() {
        assertNull(StatsResetPeriod.parse("year"));
        assertNull(StatsResetPeriod.parse("hour"));
        assertNull(StatsResetPeriod.parse(""));
        assertNull(StatsResetPeriod.parse(null));
    }

    @Test
    public void dayOnlyResetsDay() {
        Stats stats = filled();
        StatsResetPeriod.DAY.reset(stats);
        assertZero(stats, ExtendedTimedStats.P_DAY);
        assertUntouched(stats, ExtendedTimedStats.P_WEEK);
        assertUntouched(stats, ExtendedTimedStats.P_MONTH);
        assertUntouched(stats, ExtendedTimedStats.P_ALL);
    }

    @Test
    public void weekOnlyResetsWeek() {
        Stats stats = filled();
        StatsResetPeriod.WEEK.reset(stats);
        assertZero(stats, ExtendedTimedStats.P_WEEK);
        assertUntouched(stats, ExtendedTimedStats.P_DAY);
        assertUntouched(stats, ExtendedTimedStats.P_MONTH);
        assertUntouched(stats, ExtendedTimedStats.P_ALL);
    }

    @Test
    public void monthOnlyResetsMonth() {
        Stats stats = filled();
        StatsResetPeriod.MONTH.reset(stats);
        assertZero(stats, ExtendedTimedStats.P_MONTH);
        assertUntouched(stats, ExtendedTimedStats.P_DAY);
        assertUntouched(stats, ExtendedTimedStats.P_WEEK);
        assertUntouched(stats, ExtendedTimedStats.P_ALL);
    }

    @Test
    public void allResetsEverythingIncludingAllTime() {
        Stats stats = filled();
        StatsResetPeriod.ALL.reset(stats);
        for (int p = 0; p < ExtendedTimedStats.P_SIZE; p++) {
            assertZero(stats, p);
        }
    }

    @Test
    public void invalidPeriodIsRejected() {
        Stats stats = filled();
        assertThrows(IllegalArgumentException.class, () -> stats.resetStatsForPeriod(ExtendedTimedStats.P_SIZE));
        assertThrows(IllegalArgumentException.class, () -> stats.resetStatsForPeriod(-1));
    }
}
