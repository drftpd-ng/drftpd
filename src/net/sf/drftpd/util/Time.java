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
 * @author Flowman
 * @version $Id: Time.java,v 1.5 2004/04/20 04:11:51 mog Exp $
 */
public class Time {
	/**
	 * @return human readable string for time.
	 */
	public static String formatTime(long millis) {
		long days = 0, hours = 0, mins = 0, secs = 0;
		String time = "";
 
		secs = millis / 1000;
 
		while ( secs >=  86400) { 
			days++;
			secs -= 86400; 
		} 
		while ( secs >= 3600 ) { 
			hours++;
			secs -= 3600; 
		}
		while ( secs >= 60 ) {
			mins++;
			secs -= 60; 
		}
		if ( days != 0 ) time = days + "days ";
		if ( hours != 0 ) time = hours + "h ";
		if ( mins != 0 ) time += mins + "m ";
		time += secs + "s";
		
		return time;
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