package org.drftpd.master.sections.conf;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;

public class DatedSectionTest {

    @Test
    public void testRollingCalendarLogic() {
        TimeZone gmt = TimeZone.getTimeZone("GMT");
        RollingCalendar rc = new RollingCalendar(gmt, Locale.ENGLISH);
        rc.setType(DatedSection.TOP_OF_WEEK);

        Calendar cal = Calendar.getInstance(gmt);
        cal.set(2024, Calendar.FEBRUARY, 18, 12, 0, 0); // Sunday Feb 18 2024
        cal.set(Calendar.MILLISECOND, 0);

        // Test US behavior (Sunday start) - Default for Locale.ENGLISH
        rc.setFirstDayOfWeek(Calendar.SUNDAY);
        long nextUs = rc.getNextCheckMillis(cal.getTime());

        Calendar expectedUs = Calendar.getInstance(gmt);
        // With Sunday start (Feb 18 is Sun), start of week is Feb 18.
        // Next check = start + 1 week = Feb 25.
        expectedUs.set(2024, Calendar.FEBRUARY, 25, 0, 0, 0);
        expectedUs.set(Calendar.MILLISECOND, 0);

        assertEquals(expectedUs.getTimeInMillis(), nextUs,
                "US Calendar (Sunday start) should roll to next Sunday (Feb 25)");

        // Test EU behavior (Monday start) - What our fix forces via european.cal=true
        rc.setFirstDayOfWeek(Calendar.MONDAY);
        long nextEu = rc.getNextCheckMillis(cal.getTime());

        Calendar expectedEu = Calendar.getInstance(gmt);
        // With Monday start (Feb 12 was Mon), start of week is Feb 12.
        // Next check = start + 1 week = Feb 19.
        expectedEu.set(2024, Calendar.FEBRUARY, 19, 0, 0, 0);
        expectedEu.set(Calendar.MILLISECOND, 0);

        assertEquals(expectedEu.getTimeInMillis(), nextEu,
                "European Calendar (Monday start) should roll to next Monday (Feb 19)");
    }
}
