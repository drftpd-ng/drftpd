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
package org.drftpd;

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
	private static final DecimalFormat FORMAT;

	static {
		DecimalFormatSymbols formatsymbols = new DecimalFormatSymbols();
		formatsymbols.setDecimalSeparator('.');
		FORMAT = new DecimalFormat("0.0", formatsymbols);
		FORMAT.setDecimalSeparatorAlwaysShown(true);
	}

	public static final long GIBI = 1073741824L;

	public static final long GIGA = 1000000000L;

	public static final long KIBI = 1024L;

	public static final long KILO = 1000L;

	public static final long MEBI = 1048576L;

	public static final long MEGA = 1000000L;

	private static final Multiple[] MULTIPLES = new Multiple[] {
			new Multiple('E', 1000000000000000000L, 1152921504606846976L),
			new Multiple('P', 1000000000000000L, 1125899906842624L),
			new Multiple('T', 1000000000000L, 1099511627776L),
			new Multiple('G', 1000000000L, 1073741824L),
			new Multiple('M', 1000000L, 1048576L),
			new Multiple('K', 1000L, 1024L) };

	public static final long PETA = 1000000000000000L;

	public static final long TEBI = 1099511627776L;

	public static final long TERRA = 1000000000000L;

	public static String formatBytes(long bytes) {
		return formatBytes(bytes, System.getProperty("bytes.binary", "false")
				.equals("true"));
	}

	public static String formatBytes(long bytes, boolean binary) {
		long absbytes = Math.abs(bytes);

        for (Multiple multiple : MULTIPLES) {
            long multipleVal = binary ? multiple.getBinaryMultiple() : multiple
                    .getMultiple();

            if (absbytes >= multipleVal) {
                return Bytes.FORMAT.format((float) bytes / multipleVal)
                        + multiple.getSuffix() + (binary ? "i" : "") + "B";
            }
        }

		return bytes + "B";
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
		private long _binaryMultiple;

		private long _multiple;

		private char _suffix;

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
