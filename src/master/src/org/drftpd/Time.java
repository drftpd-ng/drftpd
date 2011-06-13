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

/**
 * @author zubov
 * @version $Id$
 */
public class Time {
	/**
	 * @return human readable string for time.
	 */
	public static String formatTime(long time) {
		if (time == 0) {
			return "0m 0s";
		}

		// Less than a minute...
		if (time < (60 * 1000)) {
			float s = time / 1000F;
			
			return s + "s";
		}
		
		// Less than an hour...
		if (time < (60 * 60 * 1000)) {
			long min = time / 60000;
			long s = (time - (min * 60000)) / 1000;

			return min + "m " + ((s > 9) ? ("" + s) : (" " + s)) + "s";
		}

		// Less than a day...
		if (time < (24 * 60 * 60 * 1000)) {
			long h = time / (60 * 60000);
			long min = (time - (h * 60 * 60000)) / 60000;

			return h + "h " + ((min > 9) ? ("" + min) : (" " + min)) + "m";
		}

		// Over a day...
		long d = time / (24 * 60 * 60000);
		long h = (time - (d * 24 * 60 * 60000)) / (60 * 60000);

		return d + "d " + ((h > 9) ? ("" + h) : (" " + h)) + "h";
	}

	public static long parseTime(String s) {
		s = s.toLowerCase();

		if (s.endsWith("ms")) {
			return Long.parseLong(s.substring(0, s.length() - 2));
		}

		if (s.endsWith("s")) {
			return Long.parseLong(s.substring(0, s.length() - 1)) * 1000;
		}

		if (s.endsWith("m")) {
			return Long.parseLong(s.substring(0, s.length() - 1)) * 60000;
		}

		return Long.parseLong(s);
	}
}
