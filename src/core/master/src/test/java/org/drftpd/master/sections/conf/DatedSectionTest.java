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
package org.drftpd.master.sections.conf;

import org.junit.jupiter.api.Test;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for #325: with european.cal=true, week directories must change on Monday.
 */
public class DatedSectionTest {

    private static Date noon(int year, int month, int day) {
        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(year, month, day, 12, 0, 0);
        return cal.getTime();
    }

    @Test
    public void testEuropeanWeeksChangeOnMonday() {
        SimpleDateFormat format = DatedSection.createDateFormat("YYYY-ww", Locale.US, true);

        // Sunday still belongs to the week that started the Monday before
        assertEquals("2024-07", format.format(noon(2024, Calendar.FEBRUARY, 18)));
        assertEquals("2024-08", format.format(noon(2024, Calendar.FEBRUARY, 19)));
    }

    @Test
    public void testEuropeanWeeksAroundNewYear() {
        SimpleDateFormat format = DatedSection.createDateFormat("YYYY-ww", Locale.US, true);

        // ISO 8601: Sun 2025-12-28 is the last day of 2025-W52, Mon 2025-12-29 starts 2026-W01
        assertEquals("2025-52", format.format(noon(2025, Calendar.DECEMBER, 28)));
        assertEquals("2026-01", format.format(noon(2025, Calendar.DECEMBER, 29)));
        assertEquals("2026-01", format.format(noon(2026, Calendar.JANUARY, 4)));
        assertEquals("2026-02", format.format(noon(2026, Calendar.JANUARY, 5)));
    }

    @Test
    public void testDefaultCalendarIsUnchanged() {
        SimpleDateFormat format = DatedSection.createDateFormat("YYYY-ww", Locale.US, false);

        // US weeks start on Sunday; without european.cal the behaviour stays as it was
        assertEquals("2024-08", format.format(noon(2024, Calendar.FEBRUARY, 18)));
        assertEquals("2024-08", format.format(noon(2024, Calendar.FEBRUARY, 19)));
    }

    @Test
    public void testWeekPatternIsDetectedAsWeekly() {
        // Before the fix, a "YYYY-ww" section with Monday-start weeks was detected as monthly
        assertEquals(DatedSection.TOP_OF_WEEK,
                DatedSection.computeCheckPeriod(DatedSection.createDateFormat("YYYY-ww", Locale.US, true)));
        assertEquals(DatedSection.TOP_OF_WEEK,
                DatedSection.computeCheckPeriod(DatedSection.createDateFormat("YYYY-ww", Locale.US, false)));
        // A default locale with Monday-start weeks, without european.cal
        assertEquals(DatedSection.TOP_OF_WEEK,
                DatedSection.computeCheckPeriod(DatedSection.createDateFormat("YYYY-ww", Locale.GERMANY, false)));
    }

    @Test
    public void testOtherPatternsDetectSamePeriodAsBefore() {
        for (boolean european : new boolean[]{false, true}) {
            assertEquals(DatedSection.TOP_OF_DAY,
                    DatedSection.computeCheckPeriod(DatedSection.createDateFormat("yyyy-MM-dd", Locale.US, european)));
            assertEquals(DatedSection.TOP_OF_DAY,
                    DatedSection.computeCheckPeriod(DatedSection.createDateFormat("MMdd", Locale.US, european)));
            assertEquals(DatedSection.TOP_OF_MONTH,
                    DatedSection.computeCheckPeriod(DatedSection.createDateFormat("yyyy-MM", Locale.US, european)));
            assertEquals(DatedSection.TOP_OF_YEAR,
                    DatedSection.computeCheckPeriod(DatedSection.createDateFormat("yyyy", Locale.US, european)));
        }
    }

    @Test
    public void testNonWeekPatternsAreNotAffected() {
        SimpleDateFormat european = DatedSection.createDateFormat("yyyy-MM-dd", Locale.US, true);
        SimpleDateFormat standard = DatedSection.createDateFormat("yyyy-MM-dd", Locale.US, false);
        Date sunday = noon(2024, Calendar.FEBRUARY, 18);

        assertEquals("2024-02-18", european.format(sunday));
        assertEquals(standard.format(sunday), european.format(sunday));
    }
}
