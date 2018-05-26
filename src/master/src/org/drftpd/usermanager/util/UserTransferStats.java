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
package org.drftpd.usermanager.util;

import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserManager;

import java.util.Collection;

/**
 * @author djb61
 * @version $Id$
 */
public class UserTransferStats {

	public static int getStatsPlace(String command, User user,
			UserManager userman) {
		// AL MONTH WK DAY
		int place = 1;
		long bytes = getStats(command, user);
		Collection<User> users = userman.getAllUsers();

		for (User tempUser : users) {
			long tempBytes = getStats(command, tempUser);

			if (tempBytes > bytes) {
				place++;
			}
		}

		return place;
	}

	public static long getStats(String command, User user) {
		// AL MONTH WK DAY
		String period = command.substring(0, command.length() - 2).toUpperCase();

		// UP DN
		String updn = command.substring(command.length() - 2).toUpperCase();

		if (updn.equals("UP")) {
			if (period.equals("AL")) {
				return user.getUploadedBytes();
			}

			if (period.equals("DAY")) {
				return user.getUploadedBytesDay();
			}

			if (period.equals("WK")) {
				return user.getUploadedBytesWeek();
			}

			if (period.equals("MONTH")) {
				return user.getUploadedBytesMonth();
			}
		} else if (updn.equals("DN")) {
			if (period.equals("AL")) {
				return user.getDownloadedBytes();
			}

			if (period.equals("DAY")) {
				return user.getDownloadedBytesDay();
			}

			if (period.equals("WK")) {
				return user.getDownloadedBytesWeek();
			}

			if (period.equals("MONTH")) {
				return user.getDownloadedBytesMonth();
			}
		}

		throw new IllegalArgumentException("unhandled command = " + command);
	}
}
