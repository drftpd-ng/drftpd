package net.sf.drftpd.util;

/**
 * @author Flowman
 * @version $Id: Time.java,v 1.2 2004/01/31 16:01:53 flowman Exp $
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
		if ( secs > 0 ) time += secs + "s";
		
		return time;
	}
}