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
package org.drftpd.commands.speedtest.net;

import java.util.Comparator;

/**
 * @author scitz0
 */
public class DistanceFromMeComparator implements Comparator<SpeedTestServer> {
	double myLat;
	double myLong;

	public DistanceFromMeComparator(double lat, double lon) {
		myLat = lat;
		myLong = lon;
	}

	private Double distanceFromMe(SpeedTestServer s) {
		double theta = s.getLongitude() - myLong;
		double dist = Math.sin(deg2rad(s.getLatitude())) * Math.sin(deg2rad(myLat))
				+ Math.cos(deg2rad(s.getLatitude())) * Math.cos(deg2rad(myLat))
				* Math.cos(deg2rad(theta));
		dist = Math.acos(dist);
		dist = rad2deg(dist);
		return dist;
	}

	private double deg2rad(double deg) { return (deg * Math.PI / 180.0); }
	private double rad2deg(double rad) { return (rad * 180.0 / Math.PI); }

	@Override
	public int compare(SpeedTestServer s1, SpeedTestServer s2) {
		int result = distanceFromMe(s1).compareTo(distanceFromMe(s2));
		if (result != 0) {
			return result;
		} else {
			int a = s1.getId();
			int b = s2.getId();
			return Integer.compare(a, b);
		}
	}
}
