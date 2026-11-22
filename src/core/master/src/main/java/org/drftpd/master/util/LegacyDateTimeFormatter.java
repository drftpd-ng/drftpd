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
package org.drftpd.master.util;

import java.time.ZoneId;
import java.time.chrono.Chronology;
import java.time.chrono.IsoChronology;
import java.time.chrono.JapaneseChronology;
import java.time.chrono.ThaiBuddhistChronology;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DecimalStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Builds immutable {@link DateTimeFormatter}s that render exactly like a {@link java.text.SimpleDateFormat}
 * created at the same moment: the locale (digits and calendar system) and the time zone are captured when the
 * formatter is built, not when it is used.
 */
public final class LegacyDateTimeFormatter {

    private LegacyDateTimeFormatter() {
    }

    /**
     * Same as {@code new SimpleDateFormat(pattern)}: default FORMAT locale and default time zone, captured now.
     */
    public static DateTimeFormatter ofPattern(String pattern) {
        return ofPattern(pattern, Locale.getDefault(Locale.Category.FORMAT), TimeZone.getDefault().toZoneId());
    }

    /**
     * Same as {@code new SimpleDateFormat(pattern, locale)}: default time zone, captured now.
     */
    public static DateTimeFormatter ofPattern(String pattern, Locale locale) {
        return ofPattern(pattern, locale, TimeZone.getDefault().toZoneId());
    }

    /**
     * Same as a {@code SimpleDateFormat(pattern, locale)} with the given time zone set.
     */
    public static DateTimeFormatter ofPattern(String pattern, Locale locale, ZoneId zone) {
        // SimpleDateFormat uses the locale's calendar (e.g. Buddhist for th_TH) and the locale's digits,
        // DateTimeFormatter.ofPattern would always use ISO and ASCII digits.
        Chronology chronology = chronologyOf(locale);
        return build(pattern, chronology)
                .toFormatter(locale)
                .withChronology(chronology)
                .withDecimalStyle(DecimalStyle.of(locale))
                .withZone(zone);
    }

    /**
     * Compiles the pattern. "yyyy" is not left to DateTimeFormatter, which would print a "+" sign for years of
     * more than 4 digits (possible in the Buddhist calendar after 9999 AD); SimpleDateFormat just prints the digits.
     */
    private static DateTimeFormatterBuilder build(String pattern, Chronology chronology) {
        // SimpleDateFormat only zero-pads the year for Gregorian based calendars (ISO, Buddhist)
        int minWidth = (chronology instanceof IsoChronology || chronology instanceof ThaiBuddhistChronology) ? 4 : 1;
        DateTimeFormatterBuilder builder = new DateTimeFormatterBuilder();
        int from = 0;
        int idx;
        while ((idx = pattern.indexOf("yyyy", from)) >= 0) {
            builder.appendPattern(pattern.substring(from, idx));
            builder.appendValue(ChronoField.YEAR_OF_ERA, minWidth, 19, SignStyle.NORMAL);
            from = idx + 4;
        }
        return builder.appendPattern(pattern.substring(from));
    }

    /**
     * The calendar system that {@link java.util.Calendar#getInstance(Locale)} would pick for the locale.
     * It honours the "ca" extension like {@link Chronology#ofLocale(Locale)}, and additionally maps the legacy
     * th_TH (Buddhist) and ja_JP_JP (Japanese imperial) locales that only the legacy Calendar knows about.
     */
    private static Chronology chronologyOf(Locale locale) {
        if (locale.getUnicodeLocaleType("ca") == null) {
            if ("th".equals(locale.getLanguage()) && "TH".equals(locale.getCountry())) {
                return ThaiBuddhistChronology.INSTANCE;
            }
            if ("ja".equals(locale.getLanguage()) && "JP".equals(locale.getCountry())
                    && "JP".equals(locale.getVariant())) {
                return JapaneseChronology.INSTANCE;
            }
        }
        return Chronology.ofLocale(locale);
    }
}
