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
package org.drftpd.commands.transferstatistics;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.UserManagement;
import org.drftpd.master.Session;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;
import org.drftpd.usermanager.util.UserComparator;
import org.drftpd.usermanager.util.UserTransferStats;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.IOException;
import java.util.*;


/**
 * @version $Id$
 */
public class TransferStatistics extends CommandInterface  {
	public static final int PERIOD_ALL = 0;
	public static final int PERIOD_MONTHLY = 1;
	public static final int PERIOD_WEEKLY = 2;
	public static final int PERIOD_DAILY = 3;
	
	private static final Logger logger = LogManager.getLogger(TransferStatistics.class);

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	/* TODO: not sure this method is actually
	 * use anywhere
	 */
	public static long getFiles(String command, User user) {
		// AL MONTH WK DAY
		String period = command.substring(0, command.length() - 2);

		// UP DN
		String updn = command.substring(command.length() - 2);

		if (updn.equals("UP")) {
			if (period.equals("AL")) {
				return user.getUploadedFiles();
			}

			if (period.equals("DAY")) {
				return user.getUploadedFilesDay();
			}

			if (period.equals("WK")) {
				return user.getUploadedFilesWeek();
			}

			if (period.equals("MONTH")) {
				return user.getUploadedFilesMonth();
			}
		} else if (updn.equals("DN")) {
			if (period.equals("AL")) {
				return user.getDownloadedFiles();
			}

			if (period.equals("DAY")) {
				return user.getDownloadedFilesDay();
			}

			if (period.equals("WK")) {
				return user.getDownloadedFilesWeek();
			}

			if (period.equals("MONTH")) {
				return user.getDownloadedFilesMonth();
			}
		}

		throw new IllegalArgumentException("unhandled command = " + command);
	}

	/**
	 * USAGE: site stats [<user>]
	 *        Display a user's upload/download statistics.
	 */
	public CommandResponse doSITE_STATS(CommandRequest request) {

		Session session = request.getSession();
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User user;

		if (!request.hasArgument()) {
			user = session.getUserNull(request.getUser());
		} else {
			try {
				user = GlobalContext.getGlobalContext().getUserManager().getUserByName(request.getArgument());
			} catch (NoSuchUserException e) {
				return new CommandResponse(200, "No such user: " + e.getMessage());
			} catch (UserFileException e) {
				logger.log(Level.WARN, "", e);

				return new CommandResponse(200, e.getMessage());
			}
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		UserManager userman = GlobalContext.getGlobalContext().getUserManager();

		ReplacerEnvironment env = new ReplacerEnvironment();

		env.add("created", user.getKeyedMap().getObject(UserManagement.CREATED, new Date(0L)));
		
		env.add("aluprank", UserTransferStats.getStatsPlace("ALUP", user, userman));
		env.add("aldnrank", UserTransferStats.getStatsPlace("ALDN", user, userman));
		env.add("mnuprank", UserTransferStats.getStatsPlace("MONTHUP", user, userman));
		env.add("mndnrank", UserTransferStats.getStatsPlace("MONTHDN", user, userman));
		env.add("wkuprank", UserTransferStats.getStatsPlace("WKUP", user, userman));
		env.add("wkdnrank", UserTransferStats.getStatsPlace("WKDN", user, userman));
		env.add("dayuprank", UserTransferStats.getStatsPlace("DAYUP", user, userman));
		env.add("daydnrank", UserTransferStats.getStatsPlace("DAYDN", user, userman));

		env.add("alupfiles", user.getUploadedFiles());
		env.add("alupbytes", Bytes.formatBytes(user.getUploadedBytes()));
		env.add("aldnfiles", user.getDownloadedFiles());
		env.add("aldnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
		env.add("mnupfiles", user.getUploadedFilesMonth());
		env.add("mnupbytes", Bytes.formatBytes(user.getUploadedBytesMonth()));
		env.add("mndnfiles", user.getDownloadedFilesMonth());
		env.add("mndnbytes", Bytes.formatBytes(user.getDownloadedBytesMonth()));
		env.add("wkupfiles", user.getUploadedFilesWeek());
		env.add("wkupbytes", Bytes.formatBytes(user.getUploadedBytesWeek()));
		env.add("wkdnfiles", user.getDownloadedFilesWeek());
		env.add("wkdnbytes", Bytes.formatBytes(user.getDownloadedBytesWeek()));
		env.add("dayupfiles", user.getUploadedFilesDay());
		env.add("dayupbytes", Bytes.formatBytes(user.getUploadedBytesDay()));
		env.add("daydnfiles", user.getDownloadedFilesDay());
		env.add("daydnbytes", Bytes.formatBytes(user.getDownloadedBytesDay()));

		response.addComment(session.jprintf(_bundle, _keyPrefix + "stats", env,	request.getUser()));

		return response;
	}

	public CommandResponse doSITE_ALUP(CommandRequest request) {
		return execute(request, "alup");
	}

	public CommandResponse doSITE_ALDN(CommandRequest request) {
		return execute(request, "aldn");
	}

	public CommandResponse doSITE_MONTHUP(CommandRequest request) {
		return execute(request, "monthup");
	}

	public CommandResponse doSITE_MONTHDN(CommandRequest request) {
		return execute(request, "monthdn");
	}

	public CommandResponse doSITE_WKUP(CommandRequest request) {
		return execute(request, "wkup");
	}

	public CommandResponse doSITE_WKDN(CommandRequest request) {
		return execute(request, "wkdn");
	}

	public CommandResponse doSITE_DAYUP(CommandRequest request) {
		return execute(request, "dayup");
	}

	public CommandResponse doSITE_DAYDN(CommandRequest request) {
		return execute(request, "daydn");
	}

	private CommandResponse execute(CommandRequest request, String type) {

		Collection<User> users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

		int count = 10; // default # of users to list

		if (request.hasArgument()) {
			StringTokenizer st = new StringTokenizer(request.getArgument());

			try {
				count = Integer.parseInt(st.nextToken());
			} catch (NumberFormatException ex) {
				st = new StringTokenizer(request.getArgument());
			}

			if (st.hasMoreTokens()) {
				/* TODO Likely this will need revisiting
				 * to move to prehooks
				 */
				Permission perm = new Permission(Permission.makeUsers(st));

				users.removeIf(user -> !perm.check(user));
			}
		}

		Permission perm = new Permission(Permission.makeUsers(new StringTokenizer(GlobalContext.getConfig().getHideInStats())));

		users.removeIf(perm::check);

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ArrayList<User> users2 = new ArrayList<>(users);
		users2.sort(new UserComparator(type));
		ReplacerEnvironment env = new ReplacerEnvironment();

		String headerBundleKey = _keyPrefix + type + ".header"; 
		String headerText = request.getSession().jprintf(_bundle, headerBundleKey, env,
				request.getUser());
		if (headerText.equals(headerBundleKey)) {
			try {
				addTextToResponse(response, "userdata/text/" + type + "_header.txt");
			} catch (IOException ioe) {
                logger.warn("Error reading userdata/text/{}_header.txt", type, ioe);
			}
		} else {
			response.addComment(headerText);
		}

		int i = 0;

		for (User user : users2) {
			if (++i > count) {
				break;
			}

			env.add("pos", "" + i);

			env.add("upbytesday", Bytes.formatBytes(user.getUploadedBytesDay()));
			env.add("upfilesday", "" + user.getUploadedFilesDay());
			env.add("uprateday", getUpRate(user, PERIOD_DAILY));
			env.add("upbytesweek",
					Bytes.formatBytes(user.getUploadedBytesWeek()));
			env.add("upfilesweek", "" + user.getUploadedFilesWeek());
			env.add("uprateweek", getUpRate(user, PERIOD_WEEKLY));
			env.add("upbytesmonth",
					Bytes.formatBytes(user.getUploadedBytesMonth()));
			env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
			env.add("upratemonth", getUpRate(user, PERIOD_MONTHLY));
			env.add("upbytes", Bytes.formatBytes(user.getUploadedBytes()));
			env.add("upfiles", "" + user.getUploadedFiles());
			env.add("uprate", getUpRate(user, PERIOD_ALL));

			env.add("dnbytesday",
					Bytes.formatBytes(user.getDownloadedBytesDay()));
			env.add("dnfilesday", "" + user.getDownloadedFilesDay());
			env.add("dnrateday", getDownRate(user, PERIOD_DAILY));
			env.add("dnbytesweek",
					Bytes.formatBytes(user.getDownloadedBytesWeek()));
			env.add("dnfilesweek", "" + user.getDownloadedFilesWeek());
			env.add("dnrateweek", getDownRate(user, PERIOD_WEEKLY));
			env.add("dnbytesmonth",
					Bytes.formatBytes(user.getDownloadedBytesMonth()));
			env.add("dnfilesmonth", "" + user.getDownloadedFilesMonth());
			env.add("dnratemonth", getDownRate(user, PERIOD_MONTHLY));
			env.add("dnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
			env.add("dnfiles", "" + user.getDownloadedFiles());
			env.add("dnrate", getDownRate(user, PERIOD_ALL));

			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix + type, env,
					user.getName()));

			//			response.addComment(
			//	user.getUsername()
			//		+ " "
			//		+ Bytes.formatBytes(
			//			getStats(command.substring("SITE ".length()), user)));
		}

		String footerBundleKey = _keyPrefix + type + ".footer"; 
		String footerText = request.getSession().jprintf(_bundle, footerBundleKey, env,
				request.getUser());
		if (footerText.equals(footerBundleKey)) {
			try {
				addTextToResponse(response, "userdata/text/" + type + "_footer.txt");
			} catch (IOException ioe) {
                logger.warn("Error reading userdata/text/{}_footer.txt", type, ioe);
			}
		} else {
			response.addComment(footerText);
		}

		return response;
	}

	public static String getUpRate(User user, int period) {
		double s = user.getUploadedTimeForPeriod(period) / 1000.0;

		if (s <= 0) {
			return "- k/s";
		}

		double rate = user.getUploadedBytesForPeriod(period) / s;

		return Bytes.formatBytes((long) rate) + "/s";
	}

	public static String getDownRate(User user, int period) {
		double s = user.getDownloadedTimeForPeriod(period) / 1000.0;

		if (s <= 0) {
			return "- k/s";
		}

		double rate = user.getDownloadedBytesForPeriod(period) / s;

		return Bytes.formatBytes((long) rate) + "/s";
	}
}
