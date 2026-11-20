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
package org.drftpd.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * See http://physics.nist.gov/cuu/Units/binary.html for an explanation of
 * binary multiples.
 *
 * @author mog
 * @version $Id$
 */
public class Bytes {
    private static final Logger logger = LogManager.getLogger(Bytes.class);
    private static boolean maxUnitWarned = false;

    public static final long GIBI = 1073741824L;
    public static final long GIGA = 1000000000L;
    public static final long KIBI = 1024L;
    public static final long KILO = 1000L;
    public static final long MEBI = 1048576L;
    public static final long MEGA = 1000000L;
    public static final long PETA = 1000000000000000L;
    public static final long TEBI = 1099511627776L;
    public static final long TERRA = 1000000000000L;
    private static final DecimalFormat FORMAT;
    private static final Multiple[] MULTIPLES = new Multiple[]{
            new Multiple('E', 1000000000000000000L, 1152921504606846976L),
            new Multiple('P', 1000000000000000L, 1125899906842624L),
            new Multiple('T', 1000000000000L, 1099511627776L),
            new Multiple('G', 1000000000L, 1073741824L),
            new Multiple('M', 1000000L, 1048576L),
            new Multiple('K', 1000L, 1024L)};

    static {
        DecimalFormatSymbols formatsymbols = new DecimalFormatSymbols();
        formatsymbols.setDecimalSeparator('.');
        FORMAT = new DecimalFormat("0.0", formatsymbols);
        FORMAT.setDecimalSeparatorAlwaysShown(true);
    }

    /**
     * Formats a byte count using the system properties {@code bytes.binary}
     * (true = KiB/MiB/... instead of KB/MB/...) and {@code bytes.maxunit}
     * (K, M, G, T, P or E: largest unit that will be used, unset = no cap).
     */
    public static String formatBytes(long bytes) {
        return formatBytes(bytes, System.getProperty("bytes.binary", "false")
                .equals("true"), getConfiguredMaxUnit());
    }

    public static String formatBytes(long bytes, boolean binary) {
        return formatBytes(bytes, binary, null);
    }

    /**
     * @param maxUnit the largest unit suffix to use (K, M, G, T, P or E, case
     *                insensitive). Null or an invalid value means no cap.
     */
    public static String formatBytes(long bytes, boolean binary, String maxUnit) {
        long absbytes = Math.abs(bytes);
        int first = indexOfMaxUnit(maxUnit);

        for (int i = first; i < MULTIPLES.length; i++) {
            Multiple multiple = MULTIPLES[i];
            long multipleVal = binary ? multiple.getBinaryMultiple() : multiple
                    .getMultiple();

            if (absbytes >= multipleVal) {
                return Bytes.FORMAT.format((float) bytes / multipleVal)
                        + multiple.getSuffix() + (binary ? "i" : "") + "B";
            }
        }

        return bytes + "B";
    }

    private static String getConfiguredMaxUnit() {
        String value = System.getProperty("bytes.maxunit");
        if (value != null && !isValidUnit(value) && !maxUnitWarned) {
            maxUnitWarned = true;
            logger.warn("Ignoring invalid bytes.maxunit value '{}', expected one of K, M, G, T, P, E", value);
        }
        return value;
    }

    private static boolean isValidUnit(String unit) {
        return unit != null && unit.trim().length() == 1
                && "KMGTPE".indexOf(Character.toUpperCase(unit.trim().charAt(0))) >= 0;
    }

    /** Index in MULTIPLES of the largest allowed unit, 0 (no cap) if invalid. */
    private static int indexOfMaxUnit(String maxUnit) {
        if (!isValidUnit(maxUnit)) {
            return 0;
        }
        char suffix = Character.toUpperCase(maxUnit.trim().charAt(0));
        for (int i = 0; i < MULTIPLES.length; i++) {
            if (MULTIPLES[i].getSuffix() == suffix) {
                return i;
            }
        }
        return 0;
    }

    /**
     * Parse a string representation of an amount of bytes. The suffix b is
     * optional and makes no different, this method is case insensitive.
     * <p>
     * For example: 1000 = 1000 bytes 1000b = 1000 bytes 1000B = 1000 bytes 1k =
     * 1000 bytes 1kb = 1000 bytes 1t = 1 terrabyte 1tib = 1 tebibyte
     */
    public static long parseBytes(String str) throws NumberFormatException {
        str = str.toUpperCase();

        if (str.endsWith("B")) {
            str = str.substring(0, str.length() - 1);
        }

        boolean binary = false;

        if (str.endsWith("I")) {
            str = str.substring(0, str.length() - 1);
            binary = true;
        }

        char suffix = Character.toUpperCase(str.charAt(str.length() - 1));

        if (Character.isDigit(suffix)) {
            return Math.round(Double.parseDouble(str));
        }

        str = str.substring(0, str.length() - 1);

        for (Multiple multiple : MULTIPLES) {
            if (suffix == multiple.getSuffix()) {
                return Math.round(Double.parseDouble(str)
                        * (binary ? multiple.getBinaryMultiple() : multiple
                        .getMultiple()));
            }
        }

        throw new IllegalArgumentException("Unknown suffix " + suffix);
    }

    private static class Multiple {
        private final long _binaryMultiple;

        private final long _multiple;

        private final char _suffix;

        public Multiple(char suffix, long multiple, long binarymultiple) {
            _suffix = suffix;
            _multiple = multiple;
            _binaryMultiple = binarymultiple;
        }

        public long getBinaryMultiple() {
            return _binaryMultiple;
        }

        public long getMultiple() {
            return _multiple;
        }

        public char getSuffix() {
            return _suffix;
        }
    }
}
