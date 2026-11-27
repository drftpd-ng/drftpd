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
package org.drftpd.master;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the exact output of every shared date formatter (LIST/MLST, MDTM, xferlog and index backup names).
 * <p>
 * The formatters are static, so they capture the default Locale and TimeZone when their class is
 * initialised. To test several defaults, the classes under test are loaded in a fresh class loader for
 * each (locale, zone) combination. The formatter fields are read reflectively and may be either a
 * {@link DateFormat} or a {@link DateTimeFormatter}, so this test does not depend on the implementation.
 * The expected values come from a copy of the legacy SimpleDateFormat code (the oracle) plus literal samples.
 */
public class DateFormatPinningTest {

    private static final String LIST = "org.drftpd.master.commands.list.ListHandler";
    private static final String DIR = "org.drftpd.master.commands.dir.Dir";
    private static final String XFER = "org.drftpd.master.vfs.XferLog";
    private static final String LUCENE = "org.drftpd.master.indexation.LuceneBackupThread";

    private static final long DAY = 24L * 60 * 60 * 1000;

    private static final List<Locale> LOCALES = List.of(
            Locale.ENGLISH, Locale.FRANCE, Locale.GERMANY, Locale.ROOT,
            Locale.forLanguageTag("th-TH"),
            Locale.forLanguageTag("th-TH-u-nu-thai"),
            Locale.forLanguageTag("ar-EG"),
            Locale.forLanguageTag("ja-JP-u-ca-japanese"));

    private static final List<String> ZONES = List.of("UTC", "Europe/Paris", "America/New_York");

    private static final String[] MONTHS = {"Jan", "Feb", "Mar", "Apr", "May",
            "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"};

    private static long utc(String iso) {
        return Instant.parse(iso).toEpochMilli();
    }

    /** Fixed instants: year boundaries, leap day, single digit days, DST transitions, milliseconds. */
    private static List<Long> fixedInstants() {
        return List.of(
                utc("1969-12-31T23:59:59.999Z"),
                0L,
                utc("1970-01-01T00:00:00.001Z"),
                utc("1999-12-31T23:59:59.999Z"),
                utc("2000-01-01T00:00:00.000Z"),
                utc("2023-12-31T23:30:00.000Z"),
                utc("2024-01-01T00:00:00.000Z"),
                utc("2024-01-01T04:59:59.999Z"),
                utc("2024-02-29T12:34:56.789Z"),
                utc("2024-03-01T00:00:00.000Z"),
                utc("2024-03-10T06:59:59.999Z"),   // New York: just before spring forward
                utc("2024-03-10T07:00:00.000Z"),   // New York: spring forward
                utc("2024-03-31T00:59:59.999Z"),   // Paris: just before spring forward
                utc("2024-03-31T01:00:00.000Z"),   // Paris: spring forward
                utc("2024-05-03T04:05:06.007Z"),   // single digit day, hour, minute, second, ms
                utc("2024-09-09T09:09:09.009Z"),
                utc("2024-10-27T00:30:00.000Z"),   // Paris: before fall back
                utc("2024-10-27T01:30:00.000Z"),   // Paris: after fall back
                utc("2024-11-03T05:30:00.000Z"),   // New York: before fall back
                utc("2024-11-03T06:30:00.000Z"),   // New York: after fall back
                utc("2024-12-31T23:59:59.999Z"),
                utc("2025-12-31T23:59:59.999Z"),
                utc("2038-01-19T03:14:08.000Z"),
                utc("2100-02-28T23:59:59.999Z"),
                utc("9999-01-01T00:00:00.000Z"));
    }

    private static List<Long> relativeInstants(long now) {
        return List.of(now, now - DAY, now - 3 * DAY, now - 100 * DAY, now - 182 * DAY, now - 184 * DAY,
                now - 300 * DAY, now + DAY, now + 100 * DAY, now + 182 * DAY, now + 184 * DAY, now + 400 * DAY);
    }

    // ---------------------------------------------------------------- helpers

    private static ClassLoader isolatedLoader() throws Exception {
        List<URL> urls = new ArrayList<>();
        for (String entry : System.getProperty("java.class.path").split(File.pathSeparator)) {
            urls.add(new File(entry).toURI().toURL());
        }
        // Parent is the platform loader so that application classes are loaded (and initialised) afresh.
        return new URLClassLoader(urls.toArray(new URL[0]), ClassLoader.getPlatformClassLoader());
    }

    private static Object field(ClassLoader cl, String className, String... candidateNames) throws Exception {
        Class<?> c = Class.forName(className, true, cl);
        for (String name : candidateNames) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(null);
            } catch (NoSuchFieldException ignored) {
                // try the next name
            }
        }
        throw new NoSuchFieldException(className + " " + Arrays.toString(candidateNames));
    }

    private static String format(Object formatter, long millis) {
        if (formatter instanceof DateFormat df) {
            return df.format(new Date(millis));
        }
        if (formatter instanceof DateTimeFormatter dtf) {
            return dtf.format(Instant.ofEpochMilli(millis));
        }
        throw new IllegalArgumentException("Unexpected formatter " + formatter);
    }

    private static Method unixDateMethod(Class<?> c) throws Exception {
        Method m = c.getDeclaredMethod("getUnixDate", long.class, boolean.class);
        m.setAccessible(true);
        return m;
    }

    private static String unixDate(Object handler, Method m, long millis, boolean full) throws Exception {
        return (String) m.invoke(handler, millis, full);
    }

    /** Copy of the legacy ListHandler.getUnixDate, with locally built formatters. */
    private static String legacyUnixDate(long date, boolean fulldate) {
        DateFormat afterSix = new SimpleDateFormat(" yyyy");
        DateFormat beforeSix = new SimpleDateFormat("HH:mm");
        DateFormat full = new SimpleDateFormat("HH:mm:ss yyyy");
        Date date1 = new Date(date);
        long dateTime = date1.getTime();
        if (dateTime < 0) {
            return "------------";
        }
        Calendar cal = new GregorianCalendar();
        cal.setTime(date1);
        String firstPart = MONTHS[cal.get(Calendar.MONTH)] + ' ';
        String dateStr = String.valueOf(cal.get(Calendar.DATE));
        if (dateStr.length() == 1) {
            dateStr = ' ' + dateStr;
        }
        firstPart += (dateStr + ' ');
        long nowTime = System.currentTimeMillis();
        if (fulldate) {
            return firstPart + full.format(date1);
        } else if (Math.abs(nowTime - dateTime) > (183L * 24L * 60L * 60L * 1000L)) {
            return firstPart + afterSix.format(date1);
        } else {
            return firstPart + beforeSix.format(date1);
        }
    }

    private interface Scenario {
        void run(ClassLoader cl) throws Exception;
    }

    private static void withDefaults(Locale locale, String zone, Scenario s) throws Exception {
        Locale saved = Locale.getDefault();
        Locale savedFormat = Locale.getDefault(Locale.Category.FORMAT);
        Locale savedDisplay = Locale.getDefault(Locale.Category.DISPLAY);
        TimeZone savedZone = TimeZone.getDefault();
        try {
            Locale.setDefault(locale);
            TimeZone.setDefault(TimeZone.getTimeZone(zone));
            try {
                s.run(isolatedLoader());
            } catch (AssertionError | Exception e) {
                throw new AssertionError("locale=" + locale.toLanguageTag() + " zone=" + zone + ": " + e, e);
            }
        } finally {
            Locale.setDefault(saved);
            Locale.setDefault(Locale.Category.FORMAT, savedFormat);
            Locale.setDefault(Locale.Category.DISPLAY, savedDisplay);
            TimeZone.setDefault(savedZone);
        }
    }

    private static void forEachScenario(Scenario s) throws Exception {
        for (Locale locale : LOCALES) {
            for (String zone : ZONES) {
                withDefaults(locale, zone, s);
            }
        }
    }

    // ---------------------------------------------------------------- oracle comparisons

    @Test
    void listUnixDateMatchesLegacy() throws Exception {
        forEachScenario(cl -> {
            Class<?> c = Class.forName(LIST, true, cl);
            Object handler = c.getDeclaredConstructor().newInstance();
            Method m = unixDateMethod(c);
            List<Long> all = new ArrayList<>(fixedInstants());
            all.addAll(relativeInstants(System.currentTimeMillis()));
            for (long t : all) {
                for (boolean full : new boolean[]{true, false}) {
                    assertEquals(legacyUnixDate(t, full), unixDate(handler, m, t, full), "t=" + t + " full=" + full);
                }
            }
        });
    }

    @Test
    void mlstTimeMatchesLegacy() throws Exception {
        forEachScenario(cl -> {
            Object f = field(cl, LIST, "MLSTTIME");
            SimpleDateFormat legacy = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
            legacy.setTimeZone(TimeZone.getTimeZone("GMT"));
            for (long t : fixedInstants()) {
                assertEquals(legacy.format(new Date(t)), format(f, t), "t=" + t);
            }
        });
    }

    @Test
    void mdtmMatchesLegacy() throws Exception {
        forEachScenario(cl -> {
            Object f = field(cl, DIR, "DATE_FMT");
            SimpleDateFormat legacy = new SimpleDateFormat("yyyyMMddHHmmss.SSS");
            for (long t : fixedInstants()) {
                assertEquals(legacy.format(new Date(t)), format(f, t), "t=" + t);
            }
        });
    }

    @Test
    void xferlogMatchesLegacy() throws Exception {
        forEachScenario(cl -> {
            Object f = field(cl, XFER, "DATE_FMT");
            SimpleDateFormat legacy = new SimpleDateFormat("EEE MMM d HH:mm:ss yyyy", Locale.ENGLISH);
            for (long t : fixedInstants()) {
                assertEquals(legacy.format(new Date(t)), format(f, t), "t=" + t);
            }
        });
    }

    @Test
    void luceneBackupNameMatchesLegacy() throws Exception {
        forEachScenario(cl -> {
            Object f = field(cl, LUCENE, "sdf", "BACKUP_DATE_FMT");
            SimpleDateFormat legacy = new SimpleDateFormat("yyyyMMdd-HHmmss");
            for (long t : fixedInstants()) {
                assertEquals(legacy.format(new Date(t)), format(f, t), "t=" + t);
            }
        });
    }

    // ---------------------------------------------------------------- literal samples

    private static void literals(ClassLoader cl, long t, String mlst, String mdtm, String xfer, String lucene,
                                 String unixFull) throws Exception {
        assertEquals(mlst, format(field(cl, LIST, "MLSTTIME"), t));
        assertEquals(mdtm, format(field(cl, DIR, "DATE_FMT"), t));
        assertEquals(xfer, format(field(cl, XFER, "DATE_FMT"), t));
        assertEquals(lucene, format(field(cl, LUCENE, "sdf", "BACKUP_DATE_FMT"), t));
        Class<?> c = Class.forName(LIST, true, cl);
        assertEquals(unixFull, unixDate(c.getDeclaredConstructor().newInstance(), unixDateMethod(c), t, true));
    }

    @Test
    void literalSamplesUtc() throws Exception {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRANCE, Locale.GERMANY, Locale.ROOT)) {
            withDefaults(locale, "UTC", cl -> {
                // leap day with milliseconds
                literals(cl, utc("2024-02-29T12:34:56.789Z"), "20240229123456.789", "20240229123456.789",
                        "Thu Feb 29 12:34:56 2024", "20240229-123456", "Feb 29 12:34:56 2024");
                // single digit everything
                literals(cl, utc("2024-05-03T04:05:06.007Z"), "20240503040506.007", "20240503040506.007",
                        "Fri May 3 04:05:06 2024", "20240503-040506", "May  3 04:05:06 2024");
                // end of year
                literals(cl, utc("2024-12-31T23:59:59.999Z"), "20241231235959.999", "20241231235959.999",
                        "Tue Dec 31 23:59:59 2024", "20241231-235959", "Dec 31 23:59:59 2024");
                // start of year
                literals(cl, utc("2024-01-01T00:00:00.000Z"), "20240101000000.000", "20240101000000.000",
                        "Mon Jan 1 00:00:00 2024", "20240101-000000", "Jan  1 00:00:00 2024");
            });
        }
    }

    @Test
    void literalSamplesParisAndNewYork() throws Exception {
        for (Locale locale : List.of(Locale.ENGLISH, Locale.FRANCE, Locale.GERMANY, Locale.ROOT)) {
            // MLST is always GMT, everything else uses the default zone
            withDefaults(locale, "Europe/Paris", cl -> {
                literals(cl, utc("2023-12-31T23:30:00.000Z"), "20231231233000.000", "20240101003000.000",
                        "Mon Jan 1 00:30:00 2024", "20240101-003000", "Jan  1 00:30:00 2024");
                // Paris spring forward: 01:00Z becomes 03:00 local
                literals(cl, utc("2024-03-31T01:00:00.000Z"), "20240331010000.000", "20240331030000.000",
                        "Sun Mar 31 03:00:00 2024", "20240331-030000", "Mar 31 03:00:00 2024");
                // Paris fall back: 01:30Z is 02:30 local (second occurrence)
                literals(cl, utc("2024-10-27T01:30:00.000Z"), "20241027013000.000", "20241027023000.000",
                        "Sun Oct 27 02:30:00 2024", "20241027-023000", "Oct 27 02:30:00 2024");
            });
            withDefaults(locale, "America/New_York", cl -> {
                literals(cl, utc("2024-01-01T04:59:59.999Z"), "20240101045959.999", "20231231235959.999",
                        "Sun Dec 31 23:59:59 2023", "20231231-235959", "Dec 31 23:59:59 2023");
                // New York spring forward: 07:00Z becomes 03:00 EDT
                literals(cl, utc("2024-03-10T07:00:00.000Z"), "20240310070000.000", "20240310030000.000",
                        "Sun Mar 10 03:00:00 2024", "20240310-030000", "Mar 10 03:00:00 2024");
                // New York fall back: 06:30Z is 01:30 EST (second occurrence)
                literals(cl, utc("2024-11-03T06:30:00.000Z"), "20241103063000.000", "20241103013000.000",
                        "Sun Nov 3 01:30:00 2024", "20241103-013000", "Nov  3 01:30:00 2024");
            });
        }
    }

    @Test
    void listBoundaryAroundSixMonths() throws Exception {
        withDefaults(Locale.ENGLISH, "UTC", cl -> {
            Class<?> c = Class.forName(LIST, true, cl);
            Method m = unixDateMethod(c);
            Object h = c.getDeclaredConstructor().newInstance();
            long now = System.currentTimeMillis();
            // within 183 days: time of day; older or further in the future: year
            assertTrue(unixDate(h, m, now - 182 * DAY, false).matches("[A-Z][a-z]{2} [ \\d]\\d \\d\\d:\\d\\d"));
            assertTrue(unixDate(h, m, now + 182 * DAY, false).matches("[A-Z][a-z]{2} [ \\d]\\d \\d\\d:\\d\\d"));
            assertTrue(unixDate(h, m, now - 184 * DAY, false).matches("[A-Z][a-z]{2} [ \\d]\\d  \\d{4}"));
            assertTrue(unixDate(h, m, now + 184 * DAY, false).matches("[A-Z][a-z]{2} [ \\d]\\d  \\d{4}"));
            assertEquals("------------", unixDate(h, m, -1L, false));
            assertEquals("------------", unixDate(h, m, -1L, true));
        });
    }
}
