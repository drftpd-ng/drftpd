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
package net.sf.drftpd.util;

/**
 * @author zubov
 * @version $Id: Time.java,v 1.6 2004/07/12 20:37:29 mog Exp $
 */
public class Time {
	/**
	 * @return human readable string for time.
	 */
	public static String formatTime(long time) {
		long now = System.currentTimeMillis();
		if (time >= now) {
			return "0m  0s";
		}

		// Less than an hour...  
		if (now - time < 60 * 60 * 1000) {
			long min = (now - time) / 60000;
			long s = ((now - time) - min * 60000) / 1000;
			return min + "m " + (s > 9 ? "" + s : " " + s) + "s";
		}

		// Less than a day... 
		if (now - time < 24 * 60 * 60 * 1000) {
			long h = (now - time) / (60 * 60000);
			long min = ((now - time) - h * 60 * 60000) / 60000;
			return h + "h " + (min > 9 ? "" + min : " " + min) + "m";
		}

		// Over a day...
		long d = (now - time) / (24 * 60 * 60000);
		long h = ((now - time) - d * 24 * 60 * 60000) / (60 * 60000);
		return d + "d " + (h > 9 ? "" + h : " " + h) + "h";
//		long days = 0, hours = 0, mins = 0, secs = 0;
//		String time = "";
// 
//		secs = millis / 1000;
// 
//		while ( secs >=  86400) { 
//			days++;
//			secs -= 86400; 
//		} 
//		while ( secs >= 3600 ) { 
//			hours++;
//			secs -= 3600; 
//		}
//		while ( secs >= 60 ) {
//			mins++;
//			secs -= 60; 
//		}
//		if ( days != 0 ) time = days + "days ";
//		if ( hours != 0 ) time = hours + "h ";
//		if ( mins != 0 ) time += mins + "m ";
//		time += secs + "s";
//		
//		return time;
	}
	public static long parseTime(String s) {
		s=s.toLowerCase();
		if(s.endsWith("ms")) {
			return Long.parseLong(s.substring(0, s.length()-2));
		}
		if (s.endsWith("s")) {
			return Long.parseLong(s.substring(0, s.length()-1))*1000;
		}
		if(s.endsWith("m")) {
			return Long.parseLong(s.substring(0, s.length()-1))*60000;
		}
		return Long.parseLong(s);
	}
}