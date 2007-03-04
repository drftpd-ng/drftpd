/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands.usermanagement;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.MissingResourceException;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.UserManagement;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.config.FtpConfig;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.HostMask;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserExistsException;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

/**
 * @author mog
 * @author zubov
 * @version $Id: UserManagement.java 1646 2007-02-23 17:29:39Z djb61 $
 */
public class UserManagementHandler extends CommandInterface {
	private static final Logger logger = Logger.getLogger(UserManagement.class);

	private ResourceBundle _bundle;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = ResourceBundle.getBundle(this.getClass().getName());
    }

	public CommandResponse doSITE_ADDIP(CommandRequest request)
			throws ImproperUsageException {

		if (getUserNull(request.getUser()).isAdmin() && getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length < 2) {
			return new CommandResponse(501, jprintf(_bundle,
					"addip.specify", request.getUser()));
		}

		CommandResponse response = new CommandResponse(200);
		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					args[0]);

			if (getUserNull(request.getUser()).isGroupAdmin()
					&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}

			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("targetuser", myUser.getName());

			for (int i = 1; i < args.length; i++) {
				String string = args[i].replace(",",""); // strip commas (for easy copy+paste)
				env.add("mask", string);

				try {
					myUser.addIPMask(string);
					response.addComment(jprintf(_bundle,
							"addip.success", env, request.getUser()));
					logger.info("'" + getUserNull(request.getUser()).getName()
							+ "' added ip '" + string + "' to '"
							+ myUser.getName() + "'");
				} catch (DuplicateElementException e) {
					response.addComment(jprintf(_bundle,
							"addip.dupe", env, request.getUser()));
				}
			}

			myUser.commit(); // throws UserFileException

			// userManager.save(user2);
		} catch (NoSuchUserException ex) {
			return new CommandResponse(452, "No such user: " + args[0]);
		} catch (UserFileException ex) {
			response.addComment(ex.getMessage());

			return response;
		}

		return response;
	}

	/**
	 * USAGE: site adduser <user><password>[ <ident@ip#1>... <ident@ip#5>] Adds
	 * a user. You can have wild cards for users that have dynamic ips Examples:
	 * *@192.168.1.* , frank@192.168.*.* , bob@192.*.*.* (*@192.168.1.1[5-9]
	 * will allow only 192.168.1.15-19 to connect but no one else)
	 *
	 * If a user is added by a groupadmin, that user will have the GLOCK flag
	 * enabled and will inherit the groupadmin's home directory.
	 *
	 * All default values for the user are read from file default.user in
	 * /glftpd/ftp-data/users. Comments inside describe what is what. Gadmins
	 * can be assigned their own default. <group>userfiles as templates to be
	 * used when they add a user, if one is not found, default.user will be
	 * used. default.groupname files will also be used for "site gadduser".
	 *
	 * ex. site ADDUSER Archimede mypassword
	 *
	 * This would add the user 'Archimede' with the password 'mypassword'.
	 *
	 * ex. site ADDUSER Archimede mypassword *@127.0.0.1
	 *
	 * This would do the same as above + add the ip '*@127.0.0.1' at the same
	 * time.
	 *
	 * HOMEDIRS: After login, the user will automatically be transferred into
	 * his/her homedir. As of 1.16.x this dir is now "kinda" chroot'ed and they
	 * are now unable to "cd ..".
	 *
	 *
	 *
	 * USAGE: site gadduser <group><user><password>[ <ident@ip#1 ..
	 * ident@ip#5>] Adds a user and changes his/her group to <group>. If
	 * default.group exists, it will be used as a base instead of default.user.
	 *
	 * Only public groups can be used as <group>.
	 *
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_ADDUSER(CommandRequest request)
			throws ImproperUsageException {

		/* TODO: This will need to be implemented differently to allow
		 * for other frontends.
		 */
		boolean isGAdduser = request.getCommand().equals("SITE GADDUSER");

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String newGroup = null;

		if (getUserNull(request.getUser()).isGroupAdmin()) {
			if (isGAdduser) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}

			int users;

			users = GlobalContext.getGlobalContext().getUserManager()
					.getAllUsersByGroup(getUserNull(request.getUser()).getGroup())
					.size();
			logger.debug("Group "
					+ getUserNull(request.getUser()).getGroup()
					+ " is "
					+ GlobalContext.getGlobalContext().getUserManager()
							.getAllUsersByGroup(
									getUserNull(request.getUser()).getGroup()));

			if (users >= getUserNull(request.getUser()).getKeyedMap().getObjectInt(
					UserManagement.GROUPSLOTS)) {
				return new CommandResponse(452, jprintf(_bundle,
						"adduser.noslots", request.getUser()));
			}

			newGroup = getUserNull(request.getUser()).getGroup();
		} else if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		User newUser;
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		try {
			if (isGAdduser) {
				newGroup = st.nextToken();
			}

			String newUsername = st.nextToken();
			env.add("targetuser", newUsername);
			String pass = st.nextToken();

			// Changed To Read In From File :)
			String confFile = "conf/defaultuser.conf";

			Properties cfg = new Properties();
			FileInputStream file = null;

			String ratio;
			String max_logins;
			String max_logins_ip;
			String max_uploads;
			String max_downloads;
			String wkly_allotment;
			String credits;
			String idle_time;
			String tagline;

			try {
				file = new FileInputStream(confFile);
				cfg.load(file);

				ratio = cfg.getProperty("ratio");
				max_logins = cfg.getProperty("max_logins");
				max_logins_ip = cfg.getProperty("max_logins_ip");
				max_uploads = cfg.getProperty("max_uploads");
				max_downloads = cfg.getProperty("max_downloads");
				wkly_allotment = cfg.getProperty("wkly_allotment");
				credits = cfg.getProperty("credits");
				idle_time = cfg.getProperty("idle_time");
				tagline = cfg.getProperty("tagline");

				if (ratio == null) {
					throw new ImproperUsageException(
							"Unspecified value 'ratio' in " + confFile);
				}
				if (max_logins == null) {
					throw new ImproperUsageException(
							"Unspecified value 'max_logins' in " + confFile);
				}
				if (max_logins_ip == null) {
					throw new ImproperUsageException(
							"Unspecified value 'max_logins_ip' in " + confFile);
				}
				if (max_uploads == null) {
					throw new ImproperUsageException(
							"Unspecified value 'max_uploads' in " + confFile);
				}
				if (max_downloads == null) {
					throw new ImproperUsageException(
							"Unspecified value 'max_downloads' in " + confFile);
				}
				if (wkly_allotment == null) {
					throw new ImproperUsageException(
							"Unspecified value 'wkly_allotment' in " + confFile);
				}
				if (credits == null) {
					throw new ImproperUsageException(
							"Unspecified value 'credits' in " + confFile);
				}
				if (idle_time == null) {
					throw new ImproperUsageException(
							"Unspecified value 'idle_time' in " + confFile);
				}
				if (tagline == null) {
					throw new ImproperUsageException(
							"Unspecified value 'tagline' in " + confFile);
				}

			} catch (FileNotFoundException e) {
				logger.error("Error reading " + confFile, e);
				throw new RuntimeException(e.getMessage());
			} catch (IOException e) {
				logger.error("Error reading " + confFile, e);
				throw new RuntimeException(e.getMessage());
			} finally {
				try {
					if (file != null) {
						file.close();
					}
				} catch (IOException e) {
				}
			}

			float ratioVal = Float.parseFloat(ratio);
			int max_loginsVal = Integer.parseInt(max_logins);
			int max_logins_ipVal = Integer.parseInt(max_logins_ip);
			int max_uploadsVal = Integer.parseInt(max_uploads);
			int max_downloadsVal = Integer.parseInt(max_downloads);
			int idle_timeVal = Integer.parseInt(idle_time);
			long creditsVal = Bytes.parseBytes(credits);
			long wkly_allotmentVal = Bytes.parseBytes(wkly_allotment);

			// action, no more NoSuchElementException below here
			newUser = GlobalContext.getGlobalContext().getUserManager().create(
					newUsername);
			newUser.setPassword(pass);
			newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
			response.addComment(jprintf(_bundle,
					"adduser.success", env, request.getUser()));
			newUser.getKeyedMap().setObject(UserManagement.COMMENT,
					"Added by " + getUserNull(request.getUser()).getName());
			newUser.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.MINRATIO, 3F);
			newUser.getKeyedMap().setObject(UserManagement.MAXRATIO, 3F);
			newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
			newUser.getKeyedMap()
					.setObject(UserManagement.LASTSEEN, new Date());
			newUser.getKeyedMap().setObject(UserManagement.IRCIDENT, "N/A");
			newUser.getKeyedMap()
					.setObject(UserManagement.BAN_TIME, new Date());
			// newUser.getKeyedMap().setObject(Statistics.LOGINS,0);
			// newUser.getKeyedMap().setObject(Nuke.NUKED,0);
			// newUser.getKeyedMap().setObject(Nuke.NUKEDBYTES,new Long(0));
			newUser.getKeyedMap().setObject(UserManagement.TAGLINE, tagline);
			newUser.getKeyedMap().setObject(UserManagement.RATIO, ratioVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINS,
					max_loginsVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP,
					max_logins_ipVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMUP,
					max_uploadsVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMDN,
					max_downloadsVal);
			newUser.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT,
					wkly_allotmentVal);

			newUser.setIdleTime(idle_timeVal);
			newUser.setCredits(creditsVal);

			if (newGroup != null) {
				newUser.setGroup(newGroup);
				logger.info("'" + getUserNull(request.getUser()).getName() + "' added '"
						+ newUser.getName() + "' with group "
						+ newUser.getGroup() + "'");
				env.add("primgroup", newUser.getGroup());
				response.addComment(jprintf(_bundle,
						"adduser.primgroup", env, request.getUser()));
			} else {
				logger.info("'" + getUserNull(request.getUser()).getName() + "' added '"
						+ newUser.getName() + "'");
			}
		} catch (NoSuchElementException ex) {
			return new CommandResponse(501, jprintf(_bundle,
					"adduser.missingpass", request.getUser()));
		} catch (UserFileException ex) {
			return new CommandResponse(452, ex.getMessage());
		} catch (ImproperUsageException e) {
			return new CommandResponse(501, e.getMessage());
		} catch (NumberFormatException e) {
			return new CommandResponse(501, e.getMessage());
		}

		try {
			while (st.hasMoreTokens()) {
				String string = st.nextToken().replace(",",""); // strip commas (for easy copy+paste)
				env.add("mask", string);
				new HostMask(string); // validate hostmask

				try {
					newUser.addIPMask(string);
					response.addComment(jprintf(_bundle,
							"addip.success", env, request.getUser()));
					logger.info("'" + getUserNull(request.getUser()).getName()
							+ "' added ip '" + string + "' to '"
							+ newUser.getName() + "'");
				} catch (DuplicateElementException e1) {
					response.addComment(jprintf(_bundle,
							"addip.dupe", env, request.getUser()));
				}
			}

			newUser.commit();
		} catch (UserFileException ex) {
			logger.warn("", ex);

			return new CommandResponse(452, ex.getMessage());
		}

		return response;
	}

	/**
	 * USAGE: site change <user><field><value>- change a field for a user site
	 * change =<group><field><value>- change a field for each member of group
	 * <group>site change {<user1><user2>.. }<field><value>- change a field
	 * for each user in the list site change *<field><value>- change a field
	 * for everyone
	 *
	 * Type "site change user help" in glftpd for syntax.
	 *
	 * Fields available:
	 *
	 * Field Description
	 * ------------------------------------------------------------- ratio
	 * Upload/Download ratio. 0 = Unlimited (Leech) wkly_allotment The number of
	 * kilobytes that this user will be given once a week (you need the reset
	 * binary enabled in your crontab). Syntax: site change user wkly_allotment
	 * "#,###" The first number is the section number (0=default section), the
	 * second is the number of kilobytes to give. (user's credits are replaced,
	 * not added to, with this value) Only one section at a time is supported,
	 * homedir This will change the user's homedir. NOTE: This command is
	 * disabled by default. To enable it, add "min_homedir /site" to your config
	 * file, where "/site" is the minimum directory that users can have, i.e.
	 * you can't change a user's home directory to /ftp-data or anything that
	 * doesn't have "/site" at the beginning. Important: don't use a trailing
	 * slash for homedir! Users CAN NOT cd, list, upload/download, etc, outside
	 * of their home dir. It acts similarly to chroot() (try man chroot).
	 * startup_dir The directory to start in. ex: /incoming will start the user
	 * in /glftpd/site/incoming if rootpath is /glftpd and homedir is /site.
	 * Users CAN cd, list, upload/download, etc, outside of startup_dir.
	 * idle_time Sets the default and maximum idle time for this user (overrides
	 * the -t and -T settings on glftpd command line). If -1, it is disabled; if
	 * 0, it is the same as the idler flag. credits Credits left to download.
	 * flags +1ABC or +H or -3, type "site flags" for a list of flags.
	 * num_logins # # : number of simultaneous logins allowed. The second number
	 * is number of sim. logins from the same IP. timeframe # # : the hour from
	 * which to allow logins and the hour when logins from this user will start
	 * being rejected. This is set in a 24 hour format. If a user is online past
	 * his timeframe, he'll be disconnected the next time he does a 'CWD'.
	 * time_limit Time limits, per LOGIN SESSION. (set in minutes. 0 =
	 * Unlimited) tagline User's tagline. group_slots Number of users a GADMIN
	 * is allowed to add. If you specify a second argument, it will be the
	 * number of leech accounts the gadmin can give (done by "site change user
	 * ratio 0") (2nd arg = leech slots) comment Changes the user's comment (max
	 * 50 characters). Comments are displayed by the comment cookie (see below).
	 * max_dlspeed Downstream bandwidth control (KBytes/sec) (0 = Unlimited)
	 * max_ulspeed Same but for uploads max_sim_down Maximum number of
	 * simultaneous downloads for this user (-1 = unlimited, 0 = zero [user
	 * can't download]) max_sim_up Maximum number of simultaneous uploads for
	 * this user (-1 = unlimited, 0 = zero [user can't upload]) sratio
	 * <SECTIONNAME><#>This is to change the ratio of a section (other than
	 * default).
	 *
	 * Flags available:
	 *
	 * Flagname Flag Description
	 * ------------------------------------------------------------- SITEOP 1
	 * User is siteop. GADMIN 2 User is Groupadmin of his/her first public group
	 * (doesn't work for private groups). GLOCK 3 User cannot change group.
	 * EXEMPT 4 Allows to log in when site is full. Also allows user to do "site
	 * idle 0", which is the same as having the idler flag. Also exempts the
	 * user from the sim_xfers limit in config file. COLOR 5 Enable/Disable the
	 * use of color (toggle with "site color"). DELETED 6 User is deleted.
	 * USEREDIT 7 "Co-Siteop" ANON 8 User is anonymous (per-session like login).
	 *
	 * NOTE* The 1 flag is not GOD mode, you must have the correct flags for the
	 * actions you wish to perform. NOTE* If you have flag 1 then you DO NOT
	 * WANT flag 2
	 *
	 * Restrictions placed on users flagged ANONYMOUS. 1. '!' on login is
	 * ignored. 2. They cannot DELETE, RMDIR, or RENAME. 3. Userfiles do not
	 * update like usual, meaning no stats will be kept for these users. The
	 * userfile only serves as a template for the starting environment of the
	 * logged in user. Use external scripts if you must keep records of their
	 * transfer stats.
	 *
	 * NUKE A User is allowed to use site NUKE. UNNUKE B User is allowed to use
	 * site UNNUKE. UNDUPE C User is allowed to use site UNDUPE. KICK D User is
	 * allowed to use site KICK. KILL E User is allowed to use site KILL/SWHO.
	 * TAKE F User is allowed to use site TAKE. GIVE G User is allowed to use
	 * site GIVE. USERS/USER H This allows you to view users ( site USER/USERS )
	 * IDLER I User is allowed to idle forever. CUSTOM1 J Custom flag 1 CUSTOM2
	 * K Custom flag 2 CUSTOM3 L Custom flag 3 CUSTOM4 M Custom flag 4 CUSTOM5 N
	 * Custom flag 5
	 *
	 * You can use custom flags in the config file to give some users access to
	 * certain things without having to use private groups. These flags will
	 * only show up in "site flags" if they're turned on.
	 *
	 * ex. site change Archimede ratio 5
	 *
	 * This would set the ratio to 1:5 for the user 'Archimede'.
	 *
	 * ex. site change Archimede flags +2-AG
	 *
	 * This would make the user 'Archimede' groupadmin and remove his ability to
	 * use the commands site nuke and site give.
	 *
	 * NOTE: The flag DELETED can not be changed with site change, it will
	 * change when someone does a site deluser/readd.
	 *
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_CHANGE(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		User userToChange;
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		StringTokenizer arguments = new StringTokenizer(request.getArgument());

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String username = arguments.nextToken();

		try {
			userToChange = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(username);
		} catch (NoSuchUserException e) {
			return new CommandResponse(550, "User " + username + " not found: "
					+ e.getMessage());
		} catch (UserFileException e) {
			logger.log(Level.ERROR, "Error loading user", e);

			return new CommandResponse(550, "Error loading user: " + e.getMessage());
		}

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String command = arguments.nextToken().toLowerCase();

		if (getUserNull(request.getUser()).isGroupAdmin() && !command.equals("ratio")) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		env.add("targetuser", userToChange.getName());

		// String args[] = request.getArgument().split(" ");
		// String command = args[1].toLowerCase();
		// 0 = user
		// 1 = command
		// 2- = argument
		String[] commandArguments = new String[arguments.countTokens()];
		String fullCommandArgument = "";

		for (int x = 0; arguments.hasMoreTokens(); x++) {
			commandArguments[x] = arguments.nextToken();
			fullCommandArgument = fullCommandArgument + " "
					+ commandArguments[x];
		}

		fullCommandArgument = fullCommandArgument.trim();

		if ("ratio".equals(command)) {
			// //// Ratio //////
			if (commandArguments.length != 1) {
				throw new ImproperUsageException();
			}

			float ratio = Float.parseFloat(commandArguments[0]);

			if (getUserNull(request.getUser()).isGroupAdmin()
					&& !getUserNull(request.getUser()).isAdmin()) {
				// //// Group Admin Ratio //////
				if (!getUserNull(request.getUser()).getGroup().equals(
						userToChange.getGroup())) {
					return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
				}

				if (ratio == 0F) {
					int usedleechslots = 0;

					for (Iterator iter = GlobalContext.getGlobalContext()
							.getUserManager().getAllUsersByGroup(
									getUserNull(request.getUser()).getGroup())
							.iterator(); iter.hasNext();) {
						if (((User) iter.next()).getKeyedMap()
								.getObjectFloat(UserManagement.RATIO) == 0F) {
							usedleechslots++;
						}
					}

					if (usedleechslots >= getUserNull(request.getUser()).getKeyedMap()
							.getObjectInt(UserManagement.LEECHSLOTS)) {
						return new CommandResponse(452, jprintf(_bundle,
										"changeratio.nomoreslots", request.getUser()));
					}
				} else if (ratio < getUserNull(request.getUser()).getMinRatio()
						|| ratio > getUserNull(request.getUser()).getMaxRatio()) {
					env.add("minratio", getUserNull(request.getUser()).getMinRatio());
					env.add("maxratio", getUserNull(request.getUser()).getMaxRatio());
					return new CommandResponse(452, jprintf(_bundle,
							"changeratio.invalidratio", env, request.getUser()));
				}

				logger.info("'"
						+ getUserNull(request.getUser()).getName()
						+ "' changed ratio for '"
						+ userToChange.getName()
						+ "' from '"
						+ userToChange.getKeyedMap().getObjectFloat(
								UserManagement.RATIO) + "' to '" + ratio + "'");
				userToChange.getKeyedMap().setObject(UserManagement.RATIO,
						new Float(ratio));
				env.add("newratio", Float.toString(userToChange.getKeyedMap()
						.getObjectFloat(UserManagement.RATIO)));
				response.addComment(jprintf(_bundle,
						"changeratio.success", env, request.getUser()));
			} else {
				// Ratio changes by an admin //
				logger.info("'"
						+ getUserNull(request.getUser()).getName()
						+ "' changed ratio for '"
						+ userToChange.getName()
						+ "' from '"
						+ userToChange.getKeyedMap().getObjectFloat(
								UserManagement.RATIO) + " to '" + ratio + "'");
				userToChange.getKeyedMap().setObject(UserManagement.RATIO,
						new Float(ratio));
				env.add("newratio", Float.toString(userToChange.getKeyedMap()
						.getObjectFloat(UserManagement.RATIO)));
				response.addComment(jprintf(_bundle,
						"changeratio.success", env, request.getUser()));
			}
		} else if ("credits".equals(command)) {
			if (commandArguments.length != 1) {
				throw new ImproperUsageException();
			}

			long credits = Bytes.parseBytes(commandArguments[0]);
			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed credits for '" + userToChange.getName()
					+ "' from '" + userToChange.getCredits() + " to '"
					+ credits + "'");
			userToChange.setCredits(credits);
			env.add("newcredits", Bytes.formatBytes(userToChange.getCredits()));
			response.addComment(jprintf(_bundle,
					"changecredits.success", env, request.getUser()));
		} else if ("comment".equals(command)) {
			logger.info("'"
					+ getUserNull(request.getUser()).getName()
					+ "' changed comment for '"
					+ userToChange.getName()
					+ "' from '"
					+ userToChange.getKeyedMap().getObjectString(
							UserManagement.COMMENT) + " to '"
					+ fullCommandArgument + "'");
			userToChange.getKeyedMap().setObject(UserManagement.COMMENT,
					fullCommandArgument);
			env.add("comment", userToChange.getKeyedMap().getObjectString(
					UserManagement.COMMENT));
			response.addComment(jprintf(_bundle,
					"changecomment.success", env, request.getUser()));
		} else if ("idle_time".equals(command)) {
			if (commandArguments.length != 1) {
				throw new ImproperUsageException();
			}

			int idleTime = Integer.parseInt(commandArguments[0]);
			env.add("oldidletime", "" + userToChange.getIdleTime());
			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed idle_time for '" + userToChange.getName()
					+ "' from '" + userToChange.getIdleTime() + " to '"
					+ idleTime + "'");
			userToChange.setIdleTime(idleTime);
			env.add("newidletime", "" + idleTime);
			response.addComment(jprintf(_bundle,
					"changeidletime.success", env, request.getUser()));
		} else if ("num_logins".equals(command)) {
			// [# sim logins] [# sim logins/ip]
			try {
				int numLogins;
				int numLoginsIP;

				if ((commandArguments.length < 1)
						|| (commandArguments.length > 2)) {
					return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
				}

				numLogins = Integer.parseInt(commandArguments[0]);

				if (commandArguments.length == 2) {
					numLoginsIP = Integer.parseInt(commandArguments[1]);
				} else {
					numLoginsIP = userToChange.getKeyedMap().getObjectInt(
							UserManagement.MAXLOGINSIP);
				}

				logger.info("'"
						+ getUserNull(request.getUser()).getName()
						+ "' changed num_logins for '"
						+ userToChange.getName()
						+ "' from '"
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.MAXLOGINS)
						+ "' '"
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.MAXLOGINSIP) + "' to '"
						+ numLogins + "' '" + numLoginsIP + "'");
				userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINS,
						numLogins);
				userToChange.getKeyedMap().setObject(
						UserManagement.MAXLOGINSIP, numLoginsIP);
				env.add("numlogins", "" + numLogins);
				env.add("numloginsip", "" + numLoginsIP);
				response.addComment(jprintf(_bundle,
						"changenumlogins.success", env, request.getUser()));
			} catch (NumberFormatException ex) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}

			// } else if ("max_dlspeed".equalsIgnoreCase(command)) {
			// myUser.setMaxDownloadRate(Integer.parseInt(commandArgument));
			// } else if ("max_ulspeed".equals(command)) {
			// myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
		} else if ("group_ratio".equals(command)) {
			// [# min] [# max]
			if (commandArguments.length != 2) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}

			try {
				float minRatio = Float.parseFloat(commandArguments[0]);
				float maxRatio = Float.parseFloat(commandArguments[1]);

				env.add("minratio", "" + minRatio);
				env.add("maxratio", "" + maxRatio);

				logger.info("'" + getUserNull(request.getUser()).getName()
						+ "' changed gadmin min/max ratio for user '"
						+ userToChange.getName() + "' group '"
						+ userToChange.getGroup() + "' from '"
						+ userToChange.getMinRatio() + "/"
						+ userToChange.getMaxRatio() + "' to '" + minRatio
						+ "/" + maxRatio + "'");

				if (minRatio < 1 || maxRatio < minRatio)
					return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");

				userToChange.setMinRatio(minRatio);
				userToChange.setMaxRatio(maxRatio);

				response.addComment(jprintf(_bundle,
						"changegadminratio.success", env, request.getUser()));

			} catch (NumberFormatException ex) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}
		} else if ("max_sim".equals(command)) {
			// [# DN] [# UP]

			try {
				int maxup;
				int maxdn;

				if (commandArguments.length != 2) {
					return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
				}

				maxdn = Integer.parseInt(commandArguments[0]);
				maxup = Integer.parseInt(commandArguments[1]);

				logger
						.info("'"
								+ getUserNull(request.getUser()).getName()
								+ "' changed max simultaneous download/upload slots for '"
								+ userToChange.getName() + "' from '"
								+ userToChange.getMaxSimDown() + "' '"
								+ userToChange.getMaxSimUp() + "' to '" + maxdn
								+ "' '" + maxup + "'");

				userToChange.getKeyedMap().setObject(UserManagement.MAXSIMDN,
						maxdn);
				userToChange.getKeyedMap().setObject(UserManagement.MAXSIMUP,
						maxup);
				userToChange.setMaxSimUp(maxup);
				userToChange.setMaxSimDown(maxdn);
				env.add("maxdn", "" + maxdn);
				env.add("maxup", "" + maxup);
				response.addComment(jprintf(_bundle,
						"changemaxsim.success", env, request.getUser()));

			} catch (NumberFormatException ex) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}
		} else if ("group".equals(command)) {
			if (commandArguments.length != 1) {
				throw new ImproperUsageException();
			}

			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed primary group for '" + userToChange.getName()
					+ "' from '" + userToChange.getGroup() + "' to '"
					+ commandArguments[0] + "'");
			userToChange.setGroup(commandArguments[0]);
			env.add("primgroup", userToChange.getGroup());
			response.addComment(jprintf(_bundle,
					"changeprimgroup.success", env, request.getUser()));

			// group_slots Number of users a GADMIN is allowed to add.
			// If you specify a second argument, it will be the
			// number of leech accounts the gadmin can give (done by
			// "site change user ratio 0") (2nd arg = leech slots)
		} else if ("group_slots".equals(command)) {
			try {
				if ((commandArguments.length < 1)
						|| (commandArguments.length > 2)) {
					return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
				}

				int groupSlots = Short.parseShort(commandArguments[0]);
				int groupLeechSlots;

				if (commandArguments.length >= 2) {
					groupLeechSlots = Integer.parseInt(commandArguments[1]);
				} else {
					groupLeechSlots = userToChange.getKeyedMap().getObjectInt(
							UserManagement.LEECHSLOTS);
				}

				logger.info("'"
						+ getUserNull(request.getUser()).getName()
						+ "' changed group_slots for '"
						+ userToChange.getName()
						+ "' from '"
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.GROUPSLOTS)
						+ "' "
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.LEECHSLOTS) + "' to '"
						+ groupSlots + "' '" + groupLeechSlots + "'");
				userToChange.getKeyedMap().setObject(UserManagement.GROUPSLOTS,
						groupSlots);
				userToChange.getKeyedMap().setObject(UserManagement.LEECHSLOTS,
						groupLeechSlots);
				env.add("groupslots", ""
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.GROUPSLOTS));
				env.add("groupleechslots", ""
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.LEECHSLOTS));
				response.addComment(jprintf(_bundle,
						"changegroupslots.success", env, request.getUser()));
			} catch (NumberFormatException ex) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}
		} else if ("created".equals(command)) {
			Date myDate;

			if (commandArguments.length == 0) {
				try {
					myDate = new SimpleDateFormat("yyyy-MM-dd")
							.parse(commandArguments[0]);
				} catch (ParseException e1) {
					logger.log(Level.INFO, e1);

					return new CommandResponse(452, e1.getMessage());
				}
			} else {
				myDate = new Date();
			}

			logger.info("'"
					+ getUserNull(request.getUser()).getName()
					+ "' changed created for '"
					+ userToChange.getName()
					+ "' from '"
					+ new Date(userToChange.getKeyedMap().getObjectLong(
							UserManagement.CREATED)) + "' to '" + myDate + "'");
			userToChange.getKeyedMap()
					.setObject(UserManagement.CREATED, myDate);

			response = new CommandResponse(200, jprintf(_bundle,
					"changecreated.success", env, request.getUser()));
		} else if ("wkly_allotment".equals(command)) {
			if (commandArguments.length != 1) {
				throw new ImproperUsageException();
			}

			long weeklyAllotment = Bytes.parseBytes(commandArguments[0]);
			logger.info("'"
					+ getUserNull(request.getUser()).getName()
					+ "' changed wkly_allotment for '"
					+ userToChange.getName()
					+ "' from '"
					+ userToChange.getKeyedMap().getObjectLong(
							UserManagement.WKLY_ALLOTMENT) + "' to "
					+ weeklyAllotment + "'");
			userToChange.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT,
					weeklyAllotment);

			response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		} else if ("tagline".equals(command)) {
			if (commandArguments.length < 1) {
				throw new ImproperUsageException();
			}

			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed tagline for '" + userToChange.getName()
					+ "' from '"
					+ userToChange.getKeyedMap().getObject(UserManagement.TAGLINE, "")
					+ "' to '" + fullCommandArgument + "'");
			userToChange.getKeyedMap().setObject(UserManagement.TAGLINE,
					fullCommandArgument);

			response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		} else {
			throw new ImproperUsageException();
		}

		try {
			userToChange.commit();
		} catch (UserFileException e) {
			logger.warn("", e);
			response.addComment(e.getMessage());
		}

		return response;
	}

	/**
	 * USAGE: site chgrp <user><group>[ <group>] Adds/removes a user from
	 * group(s).
	 *
	 * ex. site chgrp archimede ftp This would change the group to 'ftp' for the
	 * user 'archimede'.
	 *
	 * ex1. site chgrp archimede ftp This would remove the group ftp from the
	 * user 'archimede'.
	 *
	 * ex2. site chgrp archimede ftp eleet This moves archimede from ftp group
	 * to eleet group.
	 *
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_CHGRP(CommandRequest request) throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split("[ ,]");

		if (args.length < 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					args[0]);
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, "User not found: " + e.getMessage());
		} catch (UserFileException e) {
			logger.log(Level.FATAL, "IO error reading user", e);

			return new CommandResponse(452, "IO error reading user: " + e.getMessage());
		}

		CommandResponse response = new CommandResponse(200);

		for (int i = 1; i < args.length; i++) {
			String string = args[i];

			try {
				myUser.removeSecondaryGroup(string);
				logger.info("'" + getUserNull(request.getUser()).getName() + "' removed '"
						+ myUser.getName() + "' from group '" + string + "'");
				response.addComment(myUser.getName() + " removed from group "
						+ string);
			} catch (NoSuchFieldException e1) {
				try {
					myUser.addSecondaryGroup(string);
					logger.info("'" + getUserNull(request.getUser()).getName()
							+ "' added '" + myUser.getName() + "' to group '"
							+ string + "'");
					response.addComment(myUser.getName() + " added to group "
							+ string);
				} catch (DuplicateElementException e2) {
					throw new RuntimeException(
							"Error, user was not a member before", e2);
				}
			}
		}
		try {
			myUser.commit();
		} catch (UserFileException e) {
			return new CommandResponse(452, "Error committing user: " + e.getMessage());
		}
		return response;
	}

	/**
	 * USAGE: site chpass <user><password>Change users password.
	 *
	 * ex. site chpass Archimede newpassword This would change the password to
	 * 'newpassword' for the user 'Archimede'.
	 *
	 * See "site passwd" for more info if you get a "Password is not secure
	 * enough" error. * Denotes any password, ex. site chpass arch * This will
	 * allow arch to login with any password
	 *
	 * @throws ImproperUsageException @
	 * Denotes any email-like password, ex. site chpass arch @ This will allow
	 *             arch to login with a@b.com but not ab.com
	 */
	public CommandResponse doSITE_CHPASS(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length != 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		try {
			User myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(args[0]);
			myUser.setPassword(args[1]);
			myUser.commit();
			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed password for '" + myUser.getName() + "'");

			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, "User not found: " + e.getMessage());
		} catch (UserFileException e) {
			logger.log(Level.FATAL, "Error reading userfile", e);

			return new CommandResponse(452, "Error reading userfile: " + e.getMessage());
		}
	}

	/**
	 * USAGE: site delip <user><ident@ip>...
	 *
	 * @param request
	 * @param out
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_DELIP(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length < 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					args[0]);
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, e.getMessage());
		} catch (UserFileException e) {
			logger.log(Level.FATAL, "IO error", e);

			return new CommandResponse(452, "IO error: " + e.getMessage());
		}

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		CommandResponse response = new CommandResponse(200);

		for (int i = 1; i < args.length; i++) {
			String string = args[i].replace(",",""); // strip commas (for easy copy+paste)

			try {
				myUser.removeIpMask(string);
				logger
						.info("'" + getUserNull(request.getUser()).getName()
								+ "' removed ip '" + string + "' from '"
								+ myUser + "'");
				response.addComment("Removed " + string);
			} catch (NoSuchFieldException e1) {
				response.addComment("Mask " + string + " not found: "
						+ e1.getMessage());

				continue;
			}
		}

		return response;
	}

	public CommandResponse doSITE_DELUSER(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());
		String delUsername = st.nextToken();
		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					delUsername);
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, e.getMessage());
		} catch (UserFileException e) {
			return new CommandResponse(452, "Couldn't getUser: " + e.getMessage());
		}

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		myUser.setDeleted(true);
		String reason = "";
		if (st.hasMoreTokens()) {
			myUser.getKeyedMap().setObject(UserManagement.REASON,
					reason = st.nextToken("").substring(1));
		}
		try {
			myUser.commit();
		} catch (UserFileException e1) {
			logger.error("", e1);
			return new CommandResponse(452, "Error committing user: " + e1.getMessage());
		}
		logger.info("'" + getUserNull(request.getUser()).getName() + "' deleted user '"
				+ myUser.getName() + "' with reason '" + reason + "'");
		logger.debug("reason "
				+ myUser.getKeyedMap().getObjectString(UserManagement.REASON));
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_GINFO(CommandRequest request)
			throws ImproperUsageException {
		// security
		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
		// syntax
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		// gadmin
		String group = request.getArgument();

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(group)) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("group", group);
		env.add("sp", " ");

		// add header
		String head = _bundle.getString("ginfo.head");
		try {
			response.addComment(SimplePrintf.jprintf(head, env));
		} catch (MissingResourceException e) {
			logger.warn("", e);
			response.addComment(e.getMessage());
		} catch (FormatterException e) {
			logger.warn("", e);
			response.addComment(e.getMessage());
		}

		// vars for total stats
		int numUsers = 0;
		int numLeechUsers = 0;
		int allfup = 0;
		int allfdn = 0;
		long allmbup = 0;
		long allmbdn = 0;

		Collection users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User user = (User) iter.next();
			if (!user.isMemberOf(group))
				continue;

			char status = ' ';
			if (user.isGroupAdmin()) {
				status = '+';
			} else if (user.isAdmin()) {
				status = '*';
			} else if (user.isDeleted()) {
				status = '!';
			}

			try {
				String body = _bundle.getString("ginfo.user");
				env.add("user", status + user.getName());
				env.add("fup", "" + user.getUploadedFiles());
				env.add("mbup", Bytes.formatBytes(user.getUploadedBytes()));
				env.add("fdn", "" + user.getDownloadedFiles());
				env.add("mbdn", Bytes.formatBytes(user.getDownloadedBytes()));
				env.add("ratio", "1:"
						+ (int) user.getKeyedMap().getObjectFloat(
								UserManagement.RATIO));
				env.add("wkly", Bytes.formatBytes(user.getKeyedMap()
						.getObjectLong(UserManagement.WKLY_ALLOTMENT)));
				response.addComment(SimplePrintf.jprintf(body, env));
			} catch (MissingResourceException e) {
				response.addComment(e.getMessage());
			} catch (FormatterException e1) {
				response.addComment(e1.getMessage());
			}

			// update totals
			numUsers++;
			if ((int) user.getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0) {
				numLeechUsers++;
			}
			allfup += user.getUploadedFiles();
			allfdn += user.getDownloadedFiles();
			allmbup += user.getUploadedBytes();
			allmbdn += user.getDownloadedBytes();
		}

		// add tail
		env.add("allfup", "" + allfup);
		env.add("allmbup", Bytes.formatBytes(allmbup));
		env.add("allfdn", "" + allfdn);
		env.add("allmbdn", Bytes.formatBytes(allmbdn));
		env.add("numusers", "" + numUsers);
		env.add("numleech", "" + numLeechUsers);

		String tail = _bundle.getString("ginfo.tail");
		try {
			response.addComment(SimplePrintf.jprintf(tail, env));
		} catch (MissingResourceException e) {
			logger.warn("", e);
			response.addComment(e.getMessage());
		} catch (FormatterException e) {
			logger.warn("", e);
			response.addComment(e.getMessage());
		}

		return response;
	}

	public CommandResponse doSITE_GIVE(CommandRequest request)
			throws ImproperUsageException {

		if (!GlobalContext.getGlobalContext().getConfig().checkPermission("give",
				getUserNull(request.getUser()))) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					st.nextToken());
		} catch (Exception e) {
			logger.warn("", e);

			return new CommandResponse(200, e.getMessage());
		}

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
		long credits = 0;
		String amt = null;
		try {
			amt = st.nextToken();
			credits = Bytes.parseBytes(amt);
		} catch (NumberFormatException ex) {
			return new CommandResponse(452, "The string " + amt
					+ " cannot be interpreted");
		}

		if (0 > credits) {
			return new CommandResponse(452, credits + " is not a positive number.");
		}

		if (!getUserNull(request.getUser()).isAdmin()) {
			if (credits > getUserNull(request.getUser()).getCredits()) {
				return new CommandResponse(452,
						"You cannot give more credits than you have.");
			}

			getUserNull(request.getUser()).updateCredits(-credits);
		}

		logger.info("'" + getUserNull(request.getUser()).getName() + "' transfered "
				+ Bytes.formatBytes(credits) + " ('" + credits + "') to '"
				+ myUser.getName() + "'");
		myUser.updateCredits(credits);

		return new CommandResponse(200, "OK, gave " + Bytes.formatBytes(credits)
				+ " of your credits to " + myUser.getName());
	}

	public CommandResponse doSITE_GROUP(CommandRequest request)
			throws ImproperUsageException {

		boolean ip = false;
		float ratio = 0;
		int numLogin = 0, numLoginIP = 0, maxUp = 0, maxDn = 0;
		String opt, group;

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
		group = st.nextToken();

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
		opt = st.nextToken();

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		if (opt.equals("num_logins")) {
			numLogin = Integer.parseInt(st.nextToken());
			if (st.hasMoreTokens()) {
				ip = true;
				numLoginIP = Integer.parseInt(st.nextToken());
			}
		} else if (opt.equals("ratio")) {
			ratio = Float.parseFloat(st.nextToken());
		} else if (opt.equals("max_sim")) {
			maxUp = Integer.parseInt(st.nextToken());
			if (!st.hasMoreTokens()) {
				throw new ImproperUsageException();
			}
			maxDn = Integer.parseInt(st.nextToken());
		} else {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// getting data

		CommandResponse response = new CommandResponse(200);

		Collection users = GlobalContext.getGlobalContext().getUserManager()
		.getAllUsersByGroup(group);;

		response.addComment("Changing '" + group + "' members " + opt);

		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User userToChange = (User) iter.next();

			if (userToChange.getGroup().equals(group)) {
				if (opt.equals("num_logins")) {
					userToChange.getKeyedMap().setObject(
							UserManagement.MAXLOGINS, numLogin);
					if (ip) {
						userToChange.getKeyedMap().setObject(
								UserManagement.MAXLOGINSIP, numLoginIP);
					}
				}
				if (opt.equals("max_sim")) {
					userToChange.setMaxSimDown(maxDn);
					userToChange.setMaxSimUp(maxUp);
				}
				if (opt.equals("ratio")) {
					userToChange.getKeyedMap().setObject(UserManagement.RATIO,
							new Float(ratio));
				}
				response.addComment("Changed " + userToChange.getName() + "!");
			}
		}

		response.addComment("Done!");

		return response;
	}

	public CommandResponse doSITE_GROUPS(CommandRequest request) {
		Collection groups = GlobalContext.getGlobalContext().getUserManager().getAllGroups();

		CommandResponse response = new CommandResponse(200);
		response.addComment("All groups:");

		for (Iterator iter = groups.iterator(); iter.hasNext();) {
			String element = (String) iter.next();
			response.addComment(element);
		}

		return response;
	}

	public CommandResponse doSITE_GRPREN(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String oldGroup = st.nextToken();

		if (!st.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String newGroup = st.nextToken();
		Collection users = GlobalContext.getGlobalContext().getUserManager()
		.getAllUsersByGroup(oldGroup);;

		if (!GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(
				newGroup).isEmpty()) {
			return new CommandResponse(500, newGroup + " already exists");
		}

		CommandResponse response = new CommandResponse(200);
		response.addComment("Renaming group " + oldGroup + " to " + newGroup);

		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User userToChange = (User) iter.next();

			if (userToChange.getGroup().equals(oldGroup)) {
				userToChange.setGroup(newGroup);
			} else {
				try {
					userToChange.removeSecondaryGroup(oldGroup);
				} catch (NoSuchFieldException e1) {
					throw new RuntimeException(
							"User was not in group returned by getAllUsersByGroup");
				}

				try {
					userToChange.addSecondaryGroup(newGroup);
				} catch (DuplicateElementException e2) {
					throw new RuntimeException("group " + newGroup
							+ " already exists");
				}
			}

			response.addComment("Changed user " + userToChange.getName());
		}

		return response;
	}

	public CommandResponse doSITE_KICK(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String arg = request.getArgument();
		int pos = arg.indexOf(' ');
		String username;
		String message = "Kicked by " + getUserNull(request.getUser()).getName();

		if (pos == -1) {
			username = arg;
		} else {
			username = arg.substring(0, pos);
			message = arg.substring(pos + 1);
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
				GlobalContext.getGlobalContext().getConnectionManager().getConnections());

		for (Iterator iter = conns.iterator(); iter.hasNext();) {
			BaseFtpConnection conn2 = (BaseFtpConnection) iter.next();

			try {
				if (conn2.getUser().getName().equals(username)) {
					conn2.stop(message);
				}
			} catch (NoSuchUserException e) {
			}
		}

		return response;
	}

	public CommandResponse doSITE_PASSWD(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		logger.info("'" + getUserNull(request.getUser()).getName()
				+ "' changed his password");
		getUserNull(request.getUser()).setPassword(request.getArgument());

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_PURGE(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String delUsername = request.getArgument();
		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByNameUnchecked(delUsername);
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, e.getMessage());
		} catch (UserFileException e) {
			return new CommandResponse(452, "Couldn't getUser: " + e.getMessage());
		}

		if (!myUser.isDeleted()) {
			return new CommandResponse(452, "User isn't deleted");
		}

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		myUser.purge();
		logger.info("'" + getUserNull(request.getUser()).getName() + "' purged '"
				+ myUser.getName() + "'");

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_READD(CommandRequest request) throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByNameUnchecked(request.getArgument());
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, e.getMessage());
		} catch (UserFileException e) {
			return new CommandResponse(452, "IO error: " + e.getMessage());
		}

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!myUser.isDeleted()) {
			return new CommandResponse(452, "User wasn't deleted");
		}

		myUser.setDeleted(false);
		myUser.getKeyedMap().remove(UserManagement.REASON);
		logger.info("'" + getUserNull(request.getUser()).getName() + "' readded '"
				+ myUser.getName() + "'");
		try {
			myUser.commit();
		} catch (UserFileException e1) {
			logger.error(e1);
			return new CommandResponse(452, "Error committing user: " + e1.getMessage());
		}
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_RENUSER(CommandRequest request)
			throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length != 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		try {
			User myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(args[0]);
			String oldUsername = myUser.getName();
			myUser.rename(args[1]);
			BaseFtpConnection.fixBaseFtpConnUser(oldUsername, myUser.getName());
			logger.info("'" + getUserNull(request.getUser()).getName() + "' renamed '"
					+ oldUsername + "' to '" + myUser.getName() + "'");
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, "No such user: " + e.getMessage());
		} catch (UserExistsException e) {
			return new CommandResponse(452, "Target username is already taken");
		} catch (UserFileException e) {
			return new CommandResponse(452, e.getMessage());
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_SEEN(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		User user;

		try {
			user = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					request.getArgument());
		} catch (NoSuchUserException e) {
			return new CommandResponse(452, e.getMessage());
		} catch (UserFileException e) {
			logger.log(Level.FATAL, "", e);

			return new CommandResponse(452, "Error reading userfile: " + e.getMessage());
		}

		return new CommandResponse(200, "User was last seen: "
				+ user.getKeyedMap().getObjectDate(UserManagement.LASTSEEN));
	}

	public CommandResponse doSITE_TAGLINE(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		try {
			logger.info("'" + getUserNull(request.getUser()).getName()
					+ "' changed his tagline from '"
					+ getUserNull(request.getUser()).getKeyedMap().getObject(UserManagement.TAGLINE, "")
					+ "' to '" + request.getArgument() + "'");
			getUserNull(request.getUser()).getKeyedMap().setObject(UserManagement.TAGLINE,
					request.getArgument());
			getUserNull(request.getUser()).commit();
		} catch (UserFileException e) {
			return new CommandResponse(452, "Error committing user: " + e.getMessage());
		}
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_DEBUG(CommandRequest request) {
		User user = getUserNull(request.getUser());
		if (!request.hasArgument()) {
			user.getKeyedMap().setObject(
					UserManagement.DEBUG,
					Boolean.valueOf(!user.getKeyedMap().getObjectBoolean(
							UserManagement.DEBUG)));
		} else {
			String arg = request.getArgument();
			user.getKeyedMap().setObject(UserManagement.DEBUG,
					Boolean.valueOf(arg.equals("true") || arg.equals("on")));
		}
		try {
			user.commit();
		} catch (UserFileException e) {
			return new CommandResponse(452, "Error committing user: " + e.getMessage());
		}
		return new CommandResponse(200, jprintf(_bundle, "debug", request.getUser()));
	}

	/**
	 * USAGE: site take <user><kbytes>[ <message>] Removes credit from user
	 *
	 * ex. site take Archimede 100000 haha
	 *
	 * This will remove 100mb of credits from the user 'Archimede' and send the
	 * message haha to him.
	 *
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_TAKE(CommandRequest request)
			throws ImproperUsageException {

		if (!GlobalContext.getGlobalContext().getConfig().checkPermission("take",
				getUserNull(request.getUser()))) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;
		long credits;
		String amt = null;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					st.nextToken());

			if (!st.hasMoreTokens()) {
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			}
			amt = st.nextToken();
			credits = Bytes.parseBytes(amt); // B, not KiB

			if (0 > credits) {
				return new CommandResponse(452, "Credits must be a positive number.");
			}

			logger.info("'" + getUserNull(request.getUser()).getName() + "' took "
					+ Bytes.formatBytes(credits) + " ('" + credits
					+ "') from '" + myUser.getName() + "'");
			myUser.updateCredits(-credits);
		} catch (NumberFormatException ex) {
			return new CommandResponse(452, "The string " + amt
					+ " cannot be interpreted");
		} catch (Exception ex) {
			logger.debug("", ex);
			return new CommandResponse(452, ex.getMessage());
		}

		return new CommandResponse(200, "OK, removed " + credits + "b from "
				+ myUser.getName() + ".");
	}

	/**
	 * USAGE: site user [ <user>] Lists users / Shows detailed info about a
	 * user.
	 *
	 * ex. site user
	 *
	 * This will display a list of all users currently on site.
	 *
	 * ex. site user Archimede
	 *
	 * This will show detailed information about user 'Archimede'.
	 *
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_USER(CommandRequest request) throws ImproperUsageException {

		if (!getUserNull(request.getUser()).isAdmin() && !getUserNull(request.getUser()).isGroupAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByNameUnchecked(request.getArgument());
		} catch (NoSuchUserException ex) {
			response.setMessage("User " + request.getArgument() + " not found");

			return response;

			// return FtpResponse.RESPONSE_200_COMMAND_OK);
		} catch (UserFileException ex) {
			return new CommandResponse(452, "Userfile error: " + ex.getMessage());
		}

		if (getUserNull(request.getUser()).isGroupAdmin()
				&& !getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// int i = (int) (myUser.getTimeToday() / 1000);
		// int hours = i / 60;
		// int minutes = i - hours * 60;
		// response.addComment("time on today: " + hours + ":" + minutes);
		// ReplacerEnvironment env = new ReplacerEnvironment();

		// env.add("username", myUser.getName());
		// env.add("created", new
		// Date(myUser.getObjectLong(UserManagement.CREATED)));
		// env.add("comment", myUser.getObjectString(UserManagement.COMMENT));
		// env.add("lastseen", new Date(myUser.getLastAccessTime()));
		// env.add("totallogins", Long.toString(myUser.getLogins()));
		// env.add("idletime", Long.toString(myUser.getIdleTime()));
		// env.add("userratio",
		// Float.toString(myUser.getObjectFloat(UserManagement.RATIO)));
		// env.add("usercredits", Bytes.formatBytes(myUser.getCredits()));
		// env.add("maxlogins", Long.toString(myUser.getMaxLogins()));
		// env.add("maxloginsip", Long.toString(myUser.getMaxLoginsPerIP()));
		// env.add("groupslots", Long.toString(myUser.getGroupSlots()));
		// env.add("groupleechslots",
		// Long.toString(myUser.getGroupLeechSlots()));
		// env.add("useruploaded",
		// Bytes.formatBytes(myUser.getUploadedBytes()));
		// env.add("userdownloaded",
		// Bytes.formatBytes(myUser.getDownloadedBytes()));

		// ReplacerEnvironment env =
		// BaseFtpConnection.getReplacerEnvironment(null, myUser);
		response.addComment(jprintf(_bundle,
				"user", null, myUser));
		return response;
	}

	public CommandResponse doSITE_USERS(CommandRequest request) {

		if (!getUserNull(request.getUser()).isAdmin()) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		CommandResponse response = new CommandResponse(200);
		Collection myUsers = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

		if (request.hasArgument()) {
			Permission perm = new Permission(FtpConfig
					.makeUsers(new StringTokenizer(request.getArgument())),
					true);

			for (Iterator iter = myUsers.iterator(); iter.hasNext();) {
				User element = (User) iter.next();

				if (!perm.check(element)) {
					iter.remove();
				}
			}
		}

		for (Iterator iter = myUsers.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			response.addComment(myUser.getName());
		}

		response.addComment("Ok, " + myUsers.size() + " users listed.");

		return response;
	}

	/**
	 * Lists currently connected users.
	 */
	public CommandResponse doSITE_WHO(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		long users = 0;
		long speedup = 0;
		long speeddn = 0;
		long speed = 0;

		try {
			ReplacerFormat formatup = ReplacerUtils.finalFormat(
					_bundle, "who.up");
			ReplacerFormat formatdown = ReplacerUtils.finalFormat(
					_bundle, "who.down");
			ReplacerFormat formatidle = ReplacerUtils.finalFormat(
					_bundle, "who.idle");
			ReplacerFormat formatcommand = ReplacerUtils.finalFormat(
					_bundle, "who.command");
			ReplacerEnvironment env = new ReplacerEnvironment();
			ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
					GlobalContext.getGlobalContext().getConnectionManager()
							.getConnections());

			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn2 = (BaseFtpConnection) iter.next();

				if (conn2.isAuthenticated()) {
					users++;

					User user;

					try {
						user = conn2.getUser();
					} catch (NoSuchUserException e) {
						continue;
					}

					if (GlobalContext.getGlobalContext().getConfig()
							.checkPathPermission("hideinwho", user,
									conn2.getCurrentDirectory())) {
						continue;
					}

					// StringBuffer status = new StringBuffer();
					env.add("idle", Time.formatTime(System.currentTimeMillis()
							- conn2.getLastActive()));
					env.add("targetuser", user.getName());
					/*
					 * synchronized (conn2.getDataConnectionHandler()) { if
					 * (!conn2.isExecuting()) {
					 * response.addComment(SimplePrintf.jprintf( formatidle,
					 * env)); } else if (conn2.getDataConnectionHandler()
					 * .isTransfering()) { try { speed =
					 * conn2.getDataConnectionHandler()
					 * .getTransfer().getXferSpeed(); } catch
					 * (ObjectNotFoundException e) { logger.debug("This is a
					 * bug, please report it", e); speed = 0; } env.add("speed",
					 * Bytes.formatBytes(speed) + "/s"); env.add("file",
					 * conn2.getDataConnectionHandler()
					 * .getTransferFile().getName()); env.add("slave",
					 * conn2.getDataConnectionHandler()
					 * .getTranferSlave().getName());
					 *
					 * if (conn2.getDirection() ==
					 * Transfer.TRANSFER_RECEIVING_UPLOAD) {
					 * response.addComment(SimplePrintf.jprintf( formatup,
					 * env)); speedup += speed; } else if (conn2.getDirection() ==
					 * Transfer.TRANSFER_SENDING_DOWNLOAD) {
					 * response.addComment(SimplePrintf.jprintf( formatdown,
					 * env)); speeddn += speed; } } else { env.add("command",
					 * conn2.getRequest().getCommand());
					 * response.addComment(SimplePrintf.jprintf( formatcommand,
					 * env)); } }
					 */
					// Have to move data from DataConnectionHandler to
					// BaseFtpConnection
				}
			}

			env.add("currentusers", "" + users);
			env.add("maxusers", ""
					+ GlobalContext.getGlobalContext().getConfig().getMaxUsersTotal());
			env.add("totalupspeed", Bytes.formatBytes(speedup) + "/s");
			env.add("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
			response.addComment("");
			response.addComment(jprintf(_bundle,
					"who.statusspeed", env, request.getUser()));
			response.addComment(jprintf(_bundle,
					"who.statususers", env, request.getUser()));

			return response;
		} catch (FormatterException e) {
			return new CommandResponse(452, e.getMessage());
		}
	}

	public CommandResponse doSITE_SWHO(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		long users = 0;
		long speedup = 0;
		long speeddn = 0;
		long speed = 0;

		try {
			ReplacerFormat formatup = ReplacerUtils.finalFormat(
					_bundle, "swho.up");
			ReplacerFormat formatdown = ReplacerUtils.finalFormat(
					_bundle, "swho.down");
			ReplacerFormat formatidle = ReplacerUtils.finalFormat(
					_bundle, "swho.idle");
			ReplacerFormat formatcommand = ReplacerUtils.finalFormat(
					_bundle, "swho.command");
			ReplacerEnvironment env = new ReplacerEnvironment();
			ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
					GlobalContext.getGlobalContext().getConnectionManager()
							.getConnections());

			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn2 = (BaseFtpConnection) iter.next();

				if (conn2.isAuthenticated()) {
					users++;

					User user;

					try {
						user = conn2.getUser();
					} catch (NoSuchUserException e) {
						continue;
					}

					// if
					// (conn.getGlobalContext().getConfig().checkPathPermission("hideinwho",
					// user, conn2.getCurrentDirectory())) {
					// continue;
					// }

					// StringBuffer status = new StringBuffer();
					env.add("idle", Time.formatTime(System.currentTimeMillis()
							- conn2.getLastActive()));
					env.add("targetuser", user.getName());
					env.add("ip", conn2.getClientAddress().getHostAddress());

					/*
					 * synchronized (conn2.getDataConnectionHandler()) { if
					 * (!conn2.isExecuting()) {
					 * response.addComment(SimplePrintf.jprintf( formatidle,
					 * env)); } else if (conn2.getDataConnectionHandler()
					 * .isTransfering()) { try { speed =
					 * conn2.getDataConnectionHandler()
					 * .getTransfer().getXferSpeed(); } catch
					 * (ObjectNotFoundException e) { logger.debug("This is a
					 * bug, please report it", e); } env.add("speed",
					 * Bytes.formatBytes(speed) + "/s"); env.add("file",
					 * conn2.getDataConnectionHandler()
					 * .getTransferFile().getName()); env.add("slave",
					 * conn2.getDataConnectionHandler()
					 * .getTranferSlave().getName());
					 *
					 * if (conn2.getTransferDirection() ==
					 * Transfer.TRANSFER_RECEIVING_UPLOAD) {
					 * response.addComment(SimplePrintf.jprintf( formatup,
					 * env)); speedup += speed; } else if
					 * (conn2.getTransferDirection() ==
					 * Transfer.TRANSFER_SENDING_DOWNLOAD) {
					 * response.addComment(SimplePrintf.jprintf( formatdown,
					 * env)); speeddn += speed; } } else { env.add("command",
					 * conn2.getRequest().getCommand());
					 * response.addComment(SimplePrintf.jprintf( formatcommand,
					 * env)); } }
					 */
					// Have to move data from DataConnectionHandler to
					// BaseFtpConnection
				}
			}

			env.add("currentusers", "" + users);
			env.add("maxusers", ""
					+ GlobalContext.getGlobalContext().getConfig().getMaxUsersTotal());
			env.add("totalupspeed", Bytes.formatBytes(speedup) + "/s");
			env.add("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
			response.addComment("");
			response.addComment(jprintf(_bundle,
					"swho.statusspeed", env, request.getUser()));
			response.addComment(jprintf(_bundle,
					"swho.statususers", env, request.getUser()));

			return response;
		} catch (FormatterException e) {
			return new CommandResponse(452, e.getMessage());
		}
	}

	public CommandResponse doSITE_BAN(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;
		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					st.nextToken());
		} catch (Exception e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		long banTime;
		try {
			banTime = Long.parseLong(st.nextToken());
		} catch (NumberFormatException e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		String banMsg;
		if (st.hasMoreTokens()) {
			banMsg = "[" + getUserNull(request.getUser()).getName() + "]";
			while (st.hasMoreTokens())
				banMsg += " " + st.nextToken();
		} else {
			banMsg = "Banned by " + getUserNull(request.getUser()).getName() + " for "
					+ banTime + "m";
		}

		myUser.getKeyedMap().setObject(UserManagement.BAN_TIME,
				new Date(System.currentTimeMillis() + (banTime * 60000)));
		myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, banMsg);
		try {
			myUser.commit();
		} catch (UserFileException e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_UNBAN(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		User myUser;
		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					st.nextToken());
		} catch (Exception e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		myUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
		myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, "");

		try {
			myUser.commit();
		} catch (UserFileException e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_BANS(CommandRequest request) {
		Collection<User> myUsers = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		for (User user : myUsers) {
			long timeleft = user.getKeyedMap().getObjectDate(
					UserManagement.BAN_TIME).getTime()
					- System.currentTimeMillis();
			if (timeleft > 0) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("timeleft", "" + (timeleft / 60000));
				response.addComment(jprintf(
						_bundle, "bans", env, user));
			}
		}

		return response;
	}
}
