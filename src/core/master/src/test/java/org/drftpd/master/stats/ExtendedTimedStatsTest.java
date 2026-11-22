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

import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class ExtendedTimedStatsTest {

    /** ExtendedTimedStats is abstract only because of the period resets, which don't matter here. */
    static class Stats extends ExtendedTimedStats {
        @Override public void resetHour(Date date) { }
        @Override public void resetDay(Date date) { }
        @Override public void resetWeek(Date date) { }
        @Override public void resetMonth(Date date) { }
        @Override public void resetYear(Date date) { }
    }

    @Test
    public void testRemoveUploadSubtracts() {
        ExtendedTimedStats stats = new Stats();
        stats.updateUploadedBytes(5000L);
        stats.updateUploadedFiles(2);
        stats.updateUploadedTime(4000L);

        stats.removeUploadForPeriod(ExtendedTimedStats.P_ALL, 1000L, 1500L);

        assertEquals(4000L, stats.getUploadedBytesForPeriod(ExtendedTimedStats.P_ALL));
        assertEquals(1, stats.getUploadedFilesForPeriod(ExtendedTimedStats.P_ALL));
        assertEquals(2500L, stats.getUploadedTimeForPeriod(ExtendedTimedStats.P_ALL));
        // Other periods are left alone
        assertEquals(5000L, stats.getUploadedBytesForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(2, stats.getUploadedFilesForPeriod(ExtendedTimedStats.P_DAY));
    }

    @Test
    public void testRemoveUploadNeverGoesBelowZero() {
        // e.g. the day stats were reset since the file was uploaded (#345)
        ExtendedTimedStats stats = new Stats();

        stats.removeUploadForPeriod(ExtendedTimedStats.P_DAY, 125538012L, 1224L);

        assertEquals(0L, stats.getUploadedBytesForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(0, stats.getUploadedFilesForPeriod(ExtendedTimedStats.P_DAY));
        assertEquals(0L, stats.getUploadedTimeForPeriod(ExtendedTimedStats.P_DAY));
    }

    @Test
    public void testRemoveUploadRejectsInvalidPeriod() {
        ExtendedTimedStats stats = new Stats();
        assertThrows(IllegalArgumentException.class, () -> stats.removeUploadForPeriod(ExtendedTimedStats.P_SIZE, 1L, 1L));
    }
}
