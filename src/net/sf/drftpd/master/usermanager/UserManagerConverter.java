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
package net.sf.drftpd.master.usermanager;

import java.io.IOException;
import java.util.Iterator;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.event.listeners.Trial;

/**
 * Usage: java net.sf.drftpd.master.usermanager.UserManagerConverter net.sf.drftpd.master.usermanager.glftpd.GlftpdUserManager net.sf.drftpd.master.usermanager.JSXUserManager
 * 
 * @author mog
 * @version $Id: UserManagerConverter.java,v 1.9 2004/02/10 00:03:09 mog Exp $
 */
public class UserManagerConverter {
	private static final Logger logger =
		Logger.getLogger(UserManagerConverter.class);

	public static void main(String[] args)
		throws
			InstantiationException,
			IllegalAccessException,
			ClassNotFoundException,
			IOException,
			UserFileException {
		BasicConfigurator.configure();
		if (args.length != 2) {
			System.out.println(
				"arguments: <from usermanager class> <to usermanager class>");
			return;
		}
		UserManager from = (UserManager) Class.forName(args[0]).newInstance();
		UserManager to = (UserManager) Class.forName(args[1]).newInstance();
		logger.debug(from.getAllUsers());
		for (Iterator iter = from.getAllUsers().iterator(); iter.hasNext();) {
			User user = (User) iter.next();
			convert(user, to.create(user.getUsername()));
		}
	}

	public static void convert(User from, User to) throws UserFileException {
		logger.debug("Converting " + from.getUsername());
		for (Iterator iter = from.getGroups().iterator(); iter.hasNext();) {
			try {
				to.addGroup((String) iter.next());
			} catch (DuplicateElementException e) {
				logger.warn("", e);
			}
		}

		for (Iterator iter = from.getIpMasks().iterator(); iter.hasNext();) {
			String ipmask = (String) iter.next();
			try {
				to.addIPMask(ipmask);
			} catch (DuplicateElementException e) {
				logger.warn("", e);
			}
		}

		to.setComment(from.getComment());

		to.setCredits(from.getCredits());

		to.setDeleted(from.isDeleted());

		to.setGroup(from.getGroupName());

		to.setGroupLeechSlots(from.getGroupLeechSlots());

		to.setGroupSlots(from.getGroupLeechSlots());

		to.setIdleTime(from.getIdleTime());

		to.setLastAccessTime(from.getLastAccessTime());

		to.setLastNuked(from.getLastNuked());

		to.setLogins(from.getLogins());

		to.setMaxLogins(from.getMaxLogins());

		to.setMaxLoginsPerIP(from.getMaxLoginsPerIP());

		to.setMaxSimDownloads(from.getMaxSimDownloads());

		to.setMaxSimUploads(from.getMaxSimUploads());

		to.setNukedBytes(from.getNukedBytes());

		if (from instanceof PlainTextPasswordUser) {
			to.setPassword(((PlainTextPasswordUser) from).getPassword());
		} else if (
			from instanceof UnixPassword && to instanceof UnixPassword) {
			((UnixPassword) to).setUnixPassword(
				((UnixPassword) from).getUnixPassword());
		} else {
			logger.warn(
				"Don't know how to convert password from "
					+ from.getUsername());
		}

		to.setRatio(from.getRatio());

		to.setTagline(from.getTagline());

		to.setTimesNuked(from.getTimesNuked());

		int periods[] =
			new int[] {
				Trial.PERIOD_ALL,
				Trial.PERIOD_DAILY,
				Trial.PERIOD_MONTHLY,
				Trial.PERIOD_WEEKLY };

		for (int i = 0; i < periods.length; i++) {
			int period = periods[i];
			to.setUploadedMillisecondsForPeriod(
				period,
				from.getUploadedMillisecondsForPeriod(period));

			to.setDownloadedMillisecondsForPeriod(
				period,
				from.getDownloadedMillisecondsForPeriod(period));

			to.setUploadedBytesForPeriod(
				period,
				from.getUploadedBytesForPeriod(period));

			to.setDownloadedBytesForPeriod(
				period,
				from.getDownloadedBytesForPeriod(period));

			to.setUploadedFilesForPeriod(
				period,
				from.getUploadedFilesForPeriod(period));

			to.setDownloadedFilesForPeriod(
				period,
				from.getDownloadedFilesForPeriod(period));
		}

		to.commit();

	}
}
