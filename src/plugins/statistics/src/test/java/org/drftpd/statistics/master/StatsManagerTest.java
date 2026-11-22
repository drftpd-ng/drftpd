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
package org.drftpd.statistics.master;

import org.drftpd.master.stats.ExtendedTimedStats;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class StatsManagerTest {

    @Test
    public void testCreditsForFractionalRatios() {
        // Before the fix, (long) ratio * bytes turned 0.5 into 0 and 1.5 into 1
        assertEquals(500L, StatsManager.creditsForBytes(0.5f, 1000L));
        assertEquals(1500L, StatsManager.creditsForBytes(1.5f, 1000L));
        assertEquals(0L, StatsManager.creditsForBytes(0f, 1000L));
        assertEquals(3000L, StatsManager.creditsForBytes(3f, 1000L));
    }

    @Test
    public void testCreditsForLargeFilesAreExact() {
        // float multiplication would be off by hundreds of bytes here
        long size = 10_000_000_123L;
        assertEquals(size, StatsManager.creditsForBytes(1f, size));
        assertEquals(30_000_000_369L, StatsManager.creditsForBytes(3f, size));
    }

    private static Calendar at(int year, int month, int day, int hour, int firstDayOfWeek) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.setFirstDayOfWeek(firstDayOfWeek);
        cal.set(year, month, day, hour, 0, 0);
        return cal;
    }

    private static long time(int year, int month, int day, int hour) {
        return at(year, month, day, hour, Calendar.SUNDAY).getTimeInMillis();
    }

    @Test
    public void testUploadTodayCountsInAllPeriods() {
        Calendar now = at(2026, Calendar.NOVEMBER, 19, 15, Calendar.SUNDAY); // Thursday
        assertEquals(List.of(ExtendedTimedStats.P_ALL, ExtendedTimedStats.P_MONTH,
                        ExtendedTimedStats.P_WEEK, ExtendedTimedStats.P_DAY),
                StatsManager.getPeriodsCountingUpload(time(2026, Calendar.NOVEMBER, 19, 1), now));
    }

    @Test
    public void testUploadEarlierThisWeek() {
        Calendar now = at(2026, Calendar.NOVEMBER, 19, 15, Calendar.SUNDAY);
        assertEquals(List.of(ExtendedTimedStats.P_ALL, ExtendedTimedStats.P_MONTH, ExtendedTimedStats.P_WEEK),
                StatsManager.getPeriodsCountingUpload(time(2026, Calendar.NOVEMBER, 16, 12), now));
    }

    @Test
    public void testUploadEarlierThisMonth() {
        Calendar now = at(2026, Calendar.NOVEMBER, 19, 15, Calendar.SUNDAY);
        assertEquals(List.of(ExtendedTimedStats.P_ALL, ExtendedTimedStats.P_MONTH),
                StatsManager.getPeriodsCountingUpload(time(2026, Calendar.NOVEMBER, 3, 12), now));
    }

    @Test
    public void testUploadLastMonthOnlyCountsInAll() {
        Calendar now = at(2026, Calendar.NOVEMBER, 19, 15, Calendar.SUNDAY);
        assertEquals(List.of(ExtendedTimedStats.P_ALL),
                StatsManager.getPeriodsCountingUpload(time(2026, Calendar.OCTOBER, 30, 12), now));
    }

    @Test
    public void testWeekStartFollowsFirstDayOfWeek() {
        // Upload on Sunday 2026-11-15; "now" is Monday 2026-11-16
        long sunday = time(2026, Calendar.NOVEMBER, 15, 12);
        // US weeks start on Sunday: the upload is in this week
        assertEquals(List.of(ExtendedTimedStats.P_ALL, ExtendedTimedStats.P_MONTH, ExtendedTimedStats.P_WEEK),
                StatsManager.getPeriodsCountingUpload(sunday, at(2026, Calendar.NOVEMBER, 16, 9, Calendar.SUNDAY)));
        // european.cal: the week started on Monday, so the upload was in last week
        assertEquals(List.of(ExtendedTimedStats.P_ALL, ExtendedTimedStats.P_MONTH),
                StatsManager.getPeriodsCountingUpload(sunday, at(2026, Calendar.NOVEMBER, 16, 9, Calendar.MONDAY)));
    }
}
