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

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.*;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.Session;
import org.drftpd.master.TransferState;
import org.drftpd.permissions.Permission;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserExistsException;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class UserManagementHandler extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(UserManagement.class);

	private ResourceBundle _bundle;

	private String _keyPrefix;
	
	private static final UserCaseInsensitiveComparator USER_CASE_INSENSITIVE_COMPARATOR = new UserCaseInsensitiveComparator();

	static class UserCaseInsensitiveComparator implements Comparator<User> {
		@Override
		public int compare(User user0, User user1) {
			return String.CASE_INSENSITIVE_ORDER.compare(user0.getName(), user1.getName());
		}
	}
	
	public static final Key<List<BaseFtpConnection>> CONNECTIONS = new Key<>(UserManagement.class, "connections");

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();
    	_keyPrefix = this.getClass().getName()+".";
    }

	public CommandResponse doSITE_ADDIP(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		Session session = request.getSession();
		if (args.length < 2) {
			return new CommandResponse(501, session.jprintf(_bundle,
					_keyPrefix+"addip.specify", request.getUser()));
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		User myUser;

		try {
			myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(
					args[0]);

			if (session.getUserNull(request.getUser()).isGroupAdmin()
					&& !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}

			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("targetuser", myUser.getName());

			for (int i = 1; i < args.length; i++) {
				String string = args[i].replace(",",""); // strip commas (for easy copy+paste)
				env.add("mask", string);

				try {
					myUser.addIPMask(string);
					response.addComment(session.jprintf(_bundle,
							_keyPrefix+"addip.success", env, request.getUser()));
                    logger.info("'{}' added ip '{}' to '{}'", session.getUserNull(request.getUser()).getName(), string, myUser.getName());
				} catch (DuplicateElementException e) {
					response.addComment(session.jprintf(_bundle,
							_keyPrefix+"addip.dupe", env, request.getUser()));
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
	public CommandResponse doGenericAddUser(boolean isGAdduser, CommandRequest request)
			throws ImproperUsageException {

		Session session = request.getSession();

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String newGroup = null;

		if (session.getUserNull(request.getUser()).isGroupAdmin()) {
			if (isGAdduser) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}

			int users;

			users = GlobalContext.getGlobalContext().getUserManager()
					.getAllUsersByGroup(session.getUserNull(request.getUser()).getGroup())
					.size();
            logger.debug("Group {} is {}", session.getUserNull(request.getUser()).getGroup(), GlobalContext.getGlobalContext().getUserManager()
                    .getAllUsersByGroup(
                            session.getUserNull(request.getUser()).getGroup()));

			if (users >= session.getUserNull(request.getUser()).getKeyedMap().getObjectInteger(
					UserManagement.GROUPSLOTS)) {
				return new CommandResponse(452, session.jprintf(_bundle,
						_keyPrefix+"adduser.noslots", request.getUser()));
			}

			newGroup = session.getUserNull(request.getUser()).getGroup();
		} else if (!session.getUserNull(request.getUser()).isAdmin()) {
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

			Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("defaultuser");

			String ratio = cfg.getProperty("ratio", "3.0");
			String minratio = cfg.getProperty("min_ratio", "3.0");
			String maxratio = cfg.getProperty("max_ratio", "3.0");
			String maxlogins = cfg.getProperty("max_logins", "2");
			String maxloginsip = cfg.getProperty("max_logins_ip", "2");
			String maxsimup = cfg.getProperty("max_uploads", "2");
			String maxsimdn = cfg.getProperty("max_downloads", "2");
			String idletime = cfg.getProperty("idle_time", "300");  
			String wklyallot= cfg.getProperty("wkly_allotment", "0");
			String credits = cfg.getProperty("credits", "0b");
			String tagline = cfg.getProperty("tagline", "No tagline set.");
			String group = cfg.getProperty("group", "nogroup");
			
			float ratioVal = Float.parseFloat(ratio);
			float minratioVal = Float.parseFloat(minratio);
			float maxratioVal = Float.parseFloat(maxratio);
			int maxloginsVal = Integer.parseInt(maxlogins);
			int maxloginsipVal = Integer.parseInt(maxloginsip);
			int maxsimupVal = Integer.parseInt(maxsimup);
			int maxsimdnVal = Integer.parseInt(maxsimdn);
			int idletimeVal = Integer.parseInt(idletime);
			long creditsVal = Bytes.parseBytes(credits);
			long wklyallotVal = Bytes.parseBytes(wklyallot);


			// action, no more NoSuchElementException below here
			newUser = GlobalContext.getGlobalContext().getUserManager().create(newUsername);
			
			newUser.setPassword(pass);
			newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
			newUser.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
			newUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
			newUser.getKeyedMap().setObject(UserManagement.COMMENT,	"Added by " + session.getUserNull(request.getUser()).getName());
			newUser.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
			newUser.getKeyedMap().setObject(UserManagement.MINRATIO, minratioVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXRATIO, maxratioVal);
			
			// TODO fix this.
			//newUser.getKeyedMap().setObject(Statistics.LOGINS,0);
			
			newUser.getKeyedMap().setObject(UserManagement.IRCIDENT, "");

			newUser.getKeyedMap().setObject(UserManagement.TAGLINE, tagline);
			newUser.getKeyedMap().setObject(UserManagement.RATIO, ratioVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINS, maxloginsVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, maxloginsipVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMUP, maxsimupVal);
			newUser.getKeyedMap().setObject(UserManagement.MAXSIMDN, maxsimdnVal);
			newUser.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT, wklyallotVal);

			newUser.setIdleTime(idletimeVal);
			newUser.setCredits(creditsVal);
			
			if (newGroup != null) {
				newUser.setGroup(newGroup);
                logger.info("'{}' added '{}' with group {}'", request.getUser(), newUser.getName(), newUser.getGroup());
				env.add("primgroup", newUser.getGroup());
				response.addComment(session.jprintf(_bundle, _keyPrefix+"adduser.primgroup", env, request.getUser()));
			} else {
                logger.info("'{}' added '{}'", request.getUser(), newUser.getName());
				newUser.setGroup(group);
			}
			
			newUser.commit();
			response.addComment(session.jprintf(_bundle, _keyPrefix+"adduser.success", env, request.getUser()));
			
		} catch (NoSuchElementException e) {
			return new CommandResponse(501, session.jprintf(_bundle, _keyPrefix+"adduser.missingpass", request.getUser()));
		} catch (UserFileException e) {
			logger.error(e, e);
			return new CommandResponse(452, e.getMessage());			
		} catch (NumberFormatException e) {
			logger.error(e, e);
			return new CommandResponse(501, e.getMessage());
		}

		while (st.hasMoreTokens()) {
			String string = st.nextToken().replace(",",""); // strip commas (for easy copy+paste)
			env.add("mask", string);
			try {
				newUser.addIPMask(string);
				response.addComment(session.jprintf(_bundle, _keyPrefix+"addip.success", env, request.getUser()));
                logger.info("'{}' added ip '{}' to '{}'", request.getUser(), string, newUser.getName());
			} catch (DuplicateElementException e1) {
				response.addComment(session.jprintf(_bundle, _keyPrefix+"addip.dupe", env, request.getUser()));
			}
		}

		newUser.commit();

		return response;
	}
	
	public CommandResponse doSITE_ADDUSER(CommandRequest request) throws ImproperUsageException {
		return doGenericAddUser(false, request);
	}
	
	public CommandResponse doSITE_GADDUSER(CommandRequest request) throws ImproperUsageException {
		return doGenericAddUser(true, request);
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

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		Collection<User> users = new ArrayList<>();

		User userToChange;
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		StringTokenizer arguments = new StringTokenizer(request.getArgument());

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String username = arguments.nextToken();

		try {

		if(username.startsWith("=")) {
			String group = username.replace("=","");
			users = GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(group);
		}
		else if(username.equals("*")) {
			users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();
            	}
		else
			users.add(GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(username));
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

		Session session = request.getSession();
		if (session.getUserNull(request.getUser()).isGroupAdmin() && !command.equals("ratio")) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

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

		for (User user1 : users) {
			userToChange = user1;

			switch (command) {
				case "ratio":
					// //// Ratio //////
					if (commandArguments.length != 1) {
						throw new ImproperUsageException();
					}

					float ratio = Float.parseFloat(commandArguments[0]);

					if (session.getUserNull(request.getUser()).isGroupAdmin()
							&& !session.getUserNull(request.getUser()).isAdmin()) {
						// //// Group Admin Ratio //////

						if (!userToChange.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
							return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
						}


			/*	if (!session.getUserNull(request.getUser()).getGroup().equals(
						userToChange.getGroup())) {
					return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
				}
			*/
						if (ratio == 0F) {
							int usedleechslots = 0;

							for (User user : GlobalContext.getGlobalContext()
									.getUserManager().getAllUsersByGroup(
											session.getUserNull(request.getUser()).getGroup())) {
								if ((user).getKeyedMap()
										.getObjectFloat(UserManagement.RATIO) == 0F) {
									usedleechslots++;
								}
							}

							if (usedleechslots >= session.getUserNull(request.getUser()).getKeyedMap()
									.getObjectInteger(UserManagement.LEECHSLOTS)) {
								return new CommandResponse(452, session.jprintf(_bundle,
										_keyPrefix + "changeratio.nomoreslots", request.getUser()));
							}
						} else if (ratio < session.getUserNull(request.getUser()).getMinRatio()
								|| ratio > session.getUserNull(request.getUser()).getMaxRatio()) {
							env.add("minratio", session.getUserNull(request.getUser()).getMinRatio());
							env.add("maxratio", session.getUserNull(request.getUser()).getMaxRatio());
							return new CommandResponse(452, session.jprintf(_bundle,
									_keyPrefix + "changeratio.invalidratio", env, request.getUser()));
						}

						logger.info("'{}' changed ratio for '{}' from '{}' to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectFloat(
								UserManagement.RATIO), ratio);
						userToChange.getKeyedMap().setObject(UserManagement.RATIO,
								ratio);
						env.add("newratio", Float.toString(userToChange.getKeyedMap()
								.getObjectFloat(UserManagement.RATIO)));
						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changeratio.success", env, request.getUser()));
					} else {
						// Ratio changes by an admin //
						logger.info("'{}' changed ratio for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectFloat(
								UserManagement.RATIO), ratio);
						userToChange.getKeyedMap().setObject(UserManagement.RATIO,
								ratio);
						env.add("newratio", Float.toString(userToChange.getKeyedMap()
								.getObjectFloat(UserManagement.RATIO)));
						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changeratio.success", env, request.getUser()));
					}
					break;
				case "credits":
					if (commandArguments.length != 1) {
						throw new ImproperUsageException();
					}

					long credits = 0L;

					try {
						credits = Bytes.parseBytes(commandArguments[0]);
					} catch (NumberFormatException e) {
						return new CommandResponse(452, "The string " + commandArguments[0]
								+ " cannot be interpreted");
					}

					logger.info("'{}' changed credits for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getCredits(), credits);
					userToChange.setCredits(credits);
					env.add("newcredits", Bytes.formatBytes(userToChange.getCredits()));
					response.addComment(session.jprintf(_bundle,
							_keyPrefix + "changecredits.success", env, request.getUser()));
					break;
				case "comment":
					logger.info("'{}' changed comment for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectString(
							UserManagement.COMMENT), fullCommandArgument);
					userToChange.getKeyedMap().setObject(UserManagement.COMMENT,
							fullCommandArgument);
					env.add("comment", userToChange.getKeyedMap().getObjectString(
							UserManagement.COMMENT));
					response.addComment(session.jprintf(_bundle,
							_keyPrefix + "changecomment.success", env, request.getUser()));
					break;
				case "idle_time":
					if (commandArguments.length != 1) {
						throw new ImproperUsageException();
					}

					int idleTime = Integer.parseInt(commandArguments[0]);
					env.add("oldidletime", "" + userToChange.getIdleTime());
					logger.info("'{}' changed idle_time for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getIdleTime(), idleTime);
					userToChange.setIdleTime(idleTime);
					env.add("newidletime", "" + idleTime);
					response.addComment(session.jprintf(_bundle,
							_keyPrefix + "changeidletime.success", env, request.getUser()));
					break;
				case "num_logins":
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
							numLoginsIP = userToChange.getKeyedMap().getObjectInteger(
									UserManagement.MAXLOGINSIP);
						}

						logger.info("'{}' changed num_logins for '{}' from '{}' '{}' to '{}' '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectInteger(
								UserManagement.MAXLOGINS), userToChange.getKeyedMap().getObjectInteger(
								UserManagement.MAXLOGINSIP), numLogins, numLoginsIP);
						userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINS,
								numLogins);
						userToChange.getKeyedMap().setObject(
								UserManagement.MAXLOGINSIP, numLoginsIP);
						env.add("numlogins", "" + numLogins);
						env.add("numloginsip", "" + numLoginsIP);
						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changenumlogins.success", env, request.getUser()));
					} catch (NumberFormatException ex) {
						return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
					}

					// } else if ("max_dlspeed".equalsIgnoreCase(command)) {
					// myUser.setMaxDownloadRate(Integer.parseInt(commandArgument));
					// } else if ("max_ulspeed".equals(command)) {
					// myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
					break;
				case "group_ratio":
					// [# min] [# max]
					if (commandArguments.length != 2) {
						return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
					}

					try {
						float minRatio = Float.parseFloat(commandArguments[0]);
						float maxRatio = Float.parseFloat(commandArguments[1]);

						env.add("minratio", "" + minRatio);
						env.add("maxratio", "" + maxRatio);

						logger.info("'{}' changed gadmin min/max ratio for user '{}' group '{}' from '{}/{}' to '{}/{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getGroup(), userToChange.getMinRatio(), userToChange.getMaxRatio(), minRatio, maxRatio);

						if (minRatio < 1 || maxRatio < minRatio)
							return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");

						userToChange.setMinRatio(minRatio);
						userToChange.setMaxRatio(maxRatio);

						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changegadminratio.success", env, request.getUser()));

					} catch (NumberFormatException ex) {
						return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
					}
					break;
				case "max_sim":
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
								.info("'{}' changed max simultaneous download/upload slots for '{}' from '{}' '{}' to '{}' '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getMaxSimDown(), userToChange.getMaxSimUp(), maxdn, maxup);

						userToChange.getKeyedMap().setObject(UserManagement.MAXSIMDN,
								maxdn);
						userToChange.getKeyedMap().setObject(UserManagement.MAXSIMUP,
								maxup);
						userToChange.setMaxSimUp(maxup);
						userToChange.setMaxSimDown(maxdn);
						env.add("maxdn", "" + maxdn);
						env.add("maxup", "" + maxup);
						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changemaxsim.success", env, request.getUser()));

					} catch (NumberFormatException ex) {
						return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
					}
					break;
				case "group":
					if (commandArguments.length != 1) {
						throw new ImproperUsageException();
					}

					logger.info("'{}' changed primary group for '{}' from '{}' to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getGroup(), commandArguments[0]);
					userToChange.setGroup(commandArguments[0]);
					env.add("primgroup", userToChange.getGroup());
					response.addComment(session.jprintf(_bundle,
							_keyPrefix + "changeprimgroup.success", env, request.getUser()));

					// group_slots Number of users a GADMIN is allowed to add.
					// If you specify a second argument, it will be the
					// number of leech accounts the gadmin can give (done by
					// "site change user ratio 0") (2nd arg = leech slots)
					break;
				case "group_slots":
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
							groupLeechSlots = userToChange.getKeyedMap().getObjectInteger(
									UserManagement.LEECHSLOTS);
						}

						logger.info("'{}' changed group_slots for '{}' from '{}' {}' to '{}' '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectInteger(
								UserManagement.GROUPSLOTS), userToChange.getKeyedMap().getObjectInteger(
								UserManagement.LEECHSLOTS), groupSlots, groupLeechSlots);
						userToChange.getKeyedMap().setObject(UserManagement.GROUPSLOTS,
								groupSlots);
						userToChange.getKeyedMap().setObject(UserManagement.LEECHSLOTS,
								groupLeechSlots);
						env.add("groupslots", ""
								+ userToChange.getKeyedMap().getObjectInteger(
								UserManagement.GROUPSLOTS));
						env.add("groupleechslots", ""
								+ userToChange.getKeyedMap().getObjectInteger(
								UserManagement.LEECHSLOTS));
						response.addComment(session.jprintf(_bundle,
								_keyPrefix + "changegroupslots.success", env, request.getUser()));
					} catch (NumberFormatException ex) {
						return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
					}
					break;
				case "created":
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

					logger.info("'{}' changed created for '{}' from '{}' to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObject(
							UserManagement.CREATED, new Date(0)), myDate);
					userToChange.getKeyedMap()
							.setObject(UserManagement.CREATED, myDate);

					response = new CommandResponse(200, session.jprintf(_bundle,
							_keyPrefix + "changecreated.success", env, request.getUser()));
					break;
				case "wkly_allotment":
					if (commandArguments.length != 1) {
						throw new ImproperUsageException();
					}

					long weeklyAllotment = Bytes.parseBytes(commandArguments[0]);
					logger.info("'{}' changed wkly_allotment for '{}' from '{}' to {}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectLong(
							UserManagement.WKLY_ALLOTMENT), weeklyAllotment);
					userToChange.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT,
							weeklyAllotment);

					response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
					break;
				case "tagline":
					if (commandArguments.length < 1) {
						throw new ImproperUsageException();
					}

					logger.info("'{}' changed tagline for '{}' from '{}' to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectString(UserManagement.TAGLINE), fullCommandArgument);
					userToChange.getKeyedMap().setObject(UserManagement.TAGLINE,
							fullCommandArgument);

					response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
					break;
				default:
					throw new ImproperUsageException();
			}

			userToChange.commit();

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

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		Session session = request.getSession();
		for (int i = 1; i < args.length; i++) {
			String string = args[i];

			try {
				myUser.removeSecondaryGroup(string);
                logger.info("'{}' removed '{}' from group '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), string);
				response.addComment(myUser.getName() + " removed from group "
						+ string);
			} catch (NoSuchFieldException e1) {
				try {
					myUser.addSecondaryGroup(string);
                    logger.info("'{}' added '{}' to group '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), string);
					response.addComment(myUser.getName() + " added to group "
							+ string);
				} catch (DuplicateElementException e2) {
					throw new RuntimeException(
							"Error, user was not a member before", e2);
				}
			}
		}
		myUser.commit();
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

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length != 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		Session session = request.getSession();
		try {
			User myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(args[0]);
			if (session.getUserNull(request.getUser()).isGroupAdmin()
					&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}
			myUser.setPassword(args[1]);
			myUser.commit();
            logger.info("'{}' changed password for '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName());

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
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_DELIP(CommandRequest request)
			throws ImproperUsageException {

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

		Session session = request.getSession();
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
/*
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
*/
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		for (int i = 1; i < args.length; i++) {
			String string = args[i].replace(",",""); // strip commas (for easy copy+paste)

			try {
				myUser.removeIpMask(string);
                logger
                        .info("'{}' removed ip '{}' from '{}'", session.getUserNull(request.getUser()).getName(), string, myUser);
				response.addComment("Removed " + string);
			} catch (NoSuchFieldException e1) {
				response.addComment("Mask " + string + " not found: "
						+ e1.getMessage());

            }
		}
		
		myUser.commit();

		return response;
	}


	public CommandResponse doSITE_DELPURGE(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
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

                Session session = request.getSession();
                if (session.getUserNull(request.getUser()).isGroupAdmin()
                                && !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
                        return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
                }

                myUser.setDeleted(true);
                String reason = "";
                if (st.hasMoreTokens()) {
                        myUser.getKeyedMap().setObject(UserManagement.REASON,
                                        reason = st.nextToken("").substring(1));
                }
                myUser.commit();
        logger.info("'{}' deleted user '{}' with reason '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), reason);
        logger.debug("reason {}", myUser.getKeyedMap().getObjectString(UserManagement.REASON));

                myUser.purge();
        logger.info("'{}' purged '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName());

                return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_DELUSER(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
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

		Session session = request.getSession();
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
/*
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
*/
		myUser.setDeleted(true);
		String reason = "";
		if (st.hasMoreTokens()) {
			myUser.getKeyedMap().setObject(UserManagement.REASON,
					reason = st.nextToken("").substring(1));
		}
		myUser.commit();
        logger.info("'{}' deleted user '{}' with reason '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), reason);
        logger.debug("reason {}", myUser.getKeyedMap().getObjectString(UserManagement.REASON));
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_GINFO(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		// gadmin
		String group = request.getArgument();

		Session session = request.getSession();
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(group)) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("group", group);
		env.add("sp", " ");

		// add header
		String head = _bundle.getString(_keyPrefix+"ginfo.head");
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

		ArrayList<User> users = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());
		users.sort(UserManagementHandler.USER_CASE_INSENSITIVE_COMPARATOR);

		for (User user : users) {
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
				String body = _bundle.getString(_keyPrefix+"ginfo.user");
				env.add("user", status + user.getName());
				env.add("fup", "" + user.getUploadedFiles());
				env.add("mbup", Bytes.formatBytes(user.getUploadedBytes()));
				env.add("fdn", "" + user.getDownloadedFiles());
				env.add("mbdn", Bytes.formatBytes(user.getDownloadedBytes()));
				env.add("ratio", "1:"
						+ user.getKeyedMap().getObjectFloat(
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
			if (user.getKeyedMap().getObjectFloat(UserManagement.RATIO).intValue() == 0) {
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

		String tail = _bundle.getString(_keyPrefix+"ginfo.tail");
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

	public CommandResponse doSITE_SWAP(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
	
		StringTokenizer st = new StringTokenizer(request.getArgument());
	
		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
	
		User srcUser;
		
		try {
			srcUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(st.nextToken());
		} catch (Exception e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}
	
		if (!st.hasMoreTokens()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
		
		User destUser;
		try {
			destUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(st.nextToken());
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
			return new CommandResponse(452, "The string " + amt + " cannot be interpreted");
		}
	
		if (0 > credits) {
			return new CommandResponse(452, credits + " is not a positive number.");
		}
	
		if (credits > srcUser.getCredits()) {
			return new CommandResponse(452,"You cannot give more credits than you have.");
		}

        logger.info("'{}' transfered {} ('{}') to '{}'", srcUser.getName(), Bytes.formatBytes(credits), credits, destUser.getName());
		
		srcUser.updateCredits(-credits);
		srcUser.commit();
		destUser.updateCredits(credits);
		destUser.commit();
	
		return new CommandResponse(200, "OK, gave " + Bytes.formatBytes(credits) + " of " + srcUser.getName() + "'s credits to " + destUser.getName());
	}
	
	public CommandResponse doSITE_GIVE(CommandRequest request)
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

		Session session = request.getSession();
		if (!session.getUserNull(request.getUser()).isAdmin()) {
			if (credits > session.getUserNull(request.getUser()).getCredits()) {
				return new CommandResponse(452,
						"You cannot give more credits than you have.");
			}

			session.getUserNull(request.getUser()).updateCredits(-credits);
			session.getUserNull(request.getUser()).commit();
		}

        logger.info("'{}' transfered {} ('{}') to '{}'", session.getUserNull(request.getUser()).getName(), Bytes.formatBytes(credits), credits, myUser.getName());
		myUser.updateCredits(credits);
		myUser.commit();

		return new CommandResponse(200, "OK, gave " + Bytes.formatBytes(credits)
				+ " of your credits to " + myUser.getName());
	}

	public CommandResponse doSITE_GROUP(CommandRequest request)
			throws ImproperUsageException {

		boolean ip = false;
		float ratio = 0;
		int numLogin = 0, numLoginIP = 0, maxUp = 0, maxDn = 0;
		String opt, group;

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

		switch (opt) {
			case "num_logins":
				numLogin = Integer.parseInt(st.nextToken());
				if (st.hasMoreTokens()) {
					ip = true;
					numLoginIP = Integer.parseInt(st.nextToken());
				}
				break;
			case "ratio":
				ratio = Float.parseFloat(st.nextToken());
				break;
			case "max_sim":
				maxUp = Integer.parseInt(st.nextToken());
				if (!st.hasMoreTokens()) {
					throw new ImproperUsageException();
				}
				maxDn = Integer.parseInt(st.nextToken());
				break;
			default:
				return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		// getting data

		CommandResponse response = new CommandResponse(200);

		Collection<User> users = GlobalContext.getGlobalContext().getUserManager()
		.getAllUsersByGroup(group);

		response.addComment("Changing '" + group + "' members " + opt);

		for (User userToChange : users) {

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
                            ratio);
				}
				userToChange.commit();
				response.addComment("Changed " + userToChange.getName() + "!");
				
			}
		}

		response.addComment("Done!");

		return response;
	}

	public CommandResponse doSITE_GROUPS(CommandRequest request) {
		Collection<String> groups = GlobalContext.getGlobalContext().getUserManager().getAllGroups();

		CommandResponse response = new CommandResponse(200);
		response.addComment("All groups:");

		for (String element : groups) {
			response.addComment(element);
		}

		return response;
	}

	public CommandResponse doSITE_GRPREN(CommandRequest request)
			throws ImproperUsageException {

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
		Collection<User> users = GlobalContext.getGlobalContext().getUserManager()
		.getAllUsersByGroup(oldGroup);

		if (!GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(
				newGroup).isEmpty()) {
			return new CommandResponse(500, newGroup + " already exists");
		}

		CommandResponse response = new CommandResponse(200);
		response.addComment("Renaming group " + oldGroup + " to " + newGroup);

		for (User userToChange : users) {
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

			userToChange.commit();
			response.addComment("Changed user " + userToChange.getName());
		}

		return response;
	}

	public CommandResponse doSITE_KICK(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		Session session = request.getSession();
		String arg = request.getArgument();
		int pos = arg.indexOf(' ');
		String username;
		String message = "Kicked by " + session.getUserNull(request.getUser()).getName();

		if (pos == -1) {
			username = arg;
		} else {
			username = arg.substring(0, pos);
			message = arg.substring(pos + 1);
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		for (BaseFtpConnection conn2 : GlobalContext.getConnectionManager().getConnections()) {

			try {
				if (conn2.getUser().getName().equals(username)) {
					conn2.stop(message);
				}
			} catch (NoSuchUserException e) {
			}
		}

		return response;
	}

	public CommandResponse doSITE_KICKALL(CommandRequest request) {

		Session session = request.getSession();
		String kicker = session.getUserNull(request.getUser()).getName();

		String message = "Kicked by " + kicker;

		if (request.hasArgument()) {
			message = request.getArgument();
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		for (BaseFtpConnection conn : GlobalContext.getConnectionManager().getConnections()) {
			try {
				if (!conn.getUser().getName().equals(kicker)) {
					conn.stop(message);
				}
			} catch (NoSuchUserException e) {
			}
		}

		return response;
	}
	
	public CommandResponse doSITE_KILL(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		StringTokenizer st = new StringTokenizer(request.getArgument());
		
		int threadId = -1;
		try {
			threadId = Integer.parseInt(st.nextToken(" "));
		} catch (NumberFormatException e) {
			throw new ImproperUsageException();
		}

		String reason = "No Reason Specified";
		if (st.hasMoreTokens()) {
			reason = st.nextToken("\r\n");
		}
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ArrayList<BaseFtpConnection> conns = new ArrayList<>(GlobalContext.getConnectionManager().getConnections());
		for (BaseFtpConnection conn2 : conns) {
			if (conn2.getThreadID() == threadId) {
				conn2.stop("Session Killed: " + reason);
			}
		}
		
		return response;
	}

	public CommandResponse doSITE_PASSWD(CommandRequest request)
			throws ImproperUsageException {

		Session session = request.getSession();
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

        logger.info("'{}' changed his password", session.getUserNull(request.getUser()).getName());
		session.getUserNull(request.getUser()).setPassword(request.getArgument());
		session.getUserNull(request.getUser()).commit();

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_PURGE(CommandRequest request)
			throws ImproperUsageException {

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

		Session session = request.getSession();
                if (session.getUserNull(request.getUser()).isGroupAdmin()
                                && !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
                        return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
                }
/*
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
*/
		myUser.purge();
        logger.info("'{}' purged '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName());

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_READD(CommandRequest request) throws ImproperUsageException {

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

		Session session = request.getSession();
		
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
/*
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
*/
		if (!myUser.isDeleted()) {
			return new CommandResponse(452, "User wasn't deleted");
		}

		myUser.setDeleted(false);
		myUser.getKeyedMap().remove(UserManagement.REASON);
        logger.info("'{}' readded '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName());
		myUser.commit();
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_RENUSER(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		if (args.length != 2) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		Session session = request.getSession();
		try {
			User myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(args[0]);
			String oldUsername = myUser.getName();
			myUser.rename(args[1]);
			BaseFtpConnection.fixBaseFtpConnUser(oldUsername, myUser.getName());
			myUser.commit();
            logger.info("'{}' renamed '{}' to '{}'", session.getUserNull(request.getUser()).getName(), oldUsername, myUser.getName());
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
				+ user.getKeyedMap().getObject(UserManagement.LASTSEEN, new Date(0)));
	}

	public CommandResponse doSITE_TAGLINE(CommandRequest request) throws ImproperUsageException {

		Session session = request.getSession();
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		User u = session.getUserNull(request.getUser());

        logger.info("'{}' changed his tagline from '{}' to '{}'", request.getUser(), u.getKeyedMap().getObjectString(UserManagement.TAGLINE), request.getArgument());
		
		u.getKeyedMap().setObject(UserManagement.TAGLINE,	request.getArgument());
		u.commit();
		
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_DEBUG(CommandRequest request) {
		Session session = request.getSession();
		User user = session.getUserNull(request.getUser());
		if (!request.hasArgument()) {
			user.getKeyedMap().setObject(
					UserManagement.DEBUG,
                    !user.getKeyedMap().getObjectBoolean(
                            UserManagement.DEBUG));
		} else {
			String arg = request.getArgument();
			user.getKeyedMap().setObject(UserManagement.DEBUG,
                    arg.equals("true") || arg.equals("on"));
		}
		user.commit();
		return new CommandResponse(200, session.jprintf(_bundle, _keyPrefix+"debug", request.getUser()));
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

		Session session = request.getSession();
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

            logger.info("'{}' took {} ('{}') from '{}'", session.getUserNull(request.getUser()).getName(), Bytes.formatBytes(credits), credits, myUser.getName());
			myUser.updateCredits(-credits);
			myUser.commit();
		} catch (NumberFormatException ex) {
			return new CommandResponse(452, "The string " + amt
					+ " cannot be interpreted");
		} catch (Exception ex) {
			logger.debug("", ex);
			return new CommandResponse(452, ex.getMessage());
		}

		return new CommandResponse(200, "OK, removed " + Bytes.formatBytes(credits) + " from "
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

		Session session = request.getSession();

		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !myUser.isMemberOf(session.getUserNull(request.getUser()).getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
/*
		if (session.getUserNull(request.getUser()).isGroupAdmin()
				&& !session.getUserNull(request.getUser()).getGroup().equals(myUser.getGroup())) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}
*/
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
		response.addComment(request.getSession().jprintf(_bundle,
				_keyPrefix+"user", null, myUser.getName()));
		return response;
	}

	public CommandResponse doSITE_USERS(CommandRequest request) {

		CommandResponse response = new CommandResponse(200);
		ArrayList<User> myUsers = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());

		if (request.hasArgument()) {
			Permission perm = new Permission(Permission
					.makeUsers(new StringTokenizer(request.getArgument())),
					true);

			myUsers.removeIf(element -> !perm.check(element));
		}

		myUsers.sort(UserManagementHandler.USER_CASE_INSENSITIVE_COMPARATOR);
		for (User myUser : myUsers) {
			response.addComment(myUser.getName());
		}

		response.addComment("Ok, " + myUsers.size() + " users listed.");

		return response;
	}

	/**
	 * Lists currently connected users.
	 */
	private CommandResponse doListConnections(CommandRequest request, String type, boolean up, boolean down,
			boolean idle, boolean command, boolean statusUsers, boolean statusSpeed, boolean restrictUser) {
		Session session = request.getSession();
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		long speedup = 0;
		long speeddn = 0;
		long speed = 0;
		int xfersup = 0;
		int xfersdn = 0;
		int xferidle = 0;

		ReplacerEnvironment env = new ReplacerEnvironment();
		
		List<BaseFtpConnection> conns = request.getSession().getObject(CONNECTIONS, null);		
		if (conns == null) {
			conns = ConnectionManager.getConnectionManager().getConnections();
		}	

		for (BaseFtpConnection conn : conns) {
			if (!conn.isAuthenticated()) {
                if (!restrictUser) {
                 	env.add("targetuser", "<new>");
                 	env.add("ip", conn.getClientAddress().getHostAddress());
                 	env.add("idle", Time.formatTime(System.currentTimeMillis() - conn.getLastActive()));

                 	if (!conn.isExecuting() && idle) {
                 		response.addComment(session.jprintf(_bundle, _keyPrefix + type + ".new", env, request.getUser())); 
                 	} 
                 	xferidle++;
                 }
				continue;
			}
			
			User user;

			try {
				user = conn.getUser();
			} catch (NoSuchUserException e) {
				// user was deleted maybe? who knows?
				// very unlikely to happen.
				continue;
			}
			
			if (restrictUser) {
				// If the user owning this connection isn't the one we want then skip
				if (!user.getName().equals(request.getArgument())) {
					continue;
				}
			}

			env.add("targetuser", user.getName());
			env.add("ip", conn.getObject(BaseFtpConnection.ADDRESS, null).getHostAddress());
			env.add("thread", conn.getThreadID());
			
			synchronized (conn) {
				TransferState ts = conn.getTransferState();

				env.add("idle", Time.formatTime(System.currentTimeMillis()
						- conn.getLastActive()));

				if (!conn.isExecuting() && idle) {
					response.addComment(session.jprintf(_bundle, _keyPrefix
							+ type + ".idle", env, request.getUser()));
					xferidle++;
				} else {
					synchronized (ts) {
						if (ts.isTransfering()) {
							speed = ts.getXferSpeed();
							env.add("speed", Bytes.formatBytes(speed) + "/s");
							env.add("slave", ts.getTransferSlave().getName());
							env.add("file", ts.getTransferFile().getPath());
							char direction = ts.getDirection();
							if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
								if (up) {
									response.addComment(session.jprintf(
											_bundle, _keyPrefix + type + ".up",
											env, request.getUser()));
								}
								speedup += speed;
								xfersup++;
							} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
								if (down) {
									env.add("percentcomplete",
											calculateProgress(ts));
									response.addComment(session.jprintf(
											_bundle, _keyPrefix + type
													+ ".down", env, request
													.getUser()));
								}
								speeddn += speed;
								xfersdn++;
							}
						} else if (command) {
							env.add("command", conn.getRequest()
									.getCommand());
							response.addComment(session.jprintf(_bundle,
									_keyPrefix + type + ".command", env,
									request.getUser()));
						}
					}
				}
			}
		}

		env.add("currentusers", conns.size());
		env.add("maxusers", GlobalContext.getConfig().getMaxUsersTotal());
		env.add("totalupspeed", Bytes.formatBytes(speedup) + "/s");
		env.add("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
		env.add("totalspeed", Bytes.formatBytes(speedup+speeddn) +"/s");
		env.add("xfersup", xfersup);
		env.add("xfersdn", xfersdn);
		env.add("xfers", xfersup+xfersdn);
		env.add("idlers",xferidle);
		if (response.getComment().size() > 0 && (statusSpeed || statusUsers)) {
			response.addComment("");
		}
		if (statusSpeed) {
			response.addComment(session.jprintf(_bundle, _keyPrefix+type+".statusspeed", env, request.getUser()));
		}
		if (statusUsers) {
			response.addComment(session.jprintf(_bundle, _keyPrefix+type+".statususers", env, request.getUser()));
		}

		return response;
	}

	private long calculateProgress(TransferState ts) {
		long size = 1;
		try {
			size = ts.getTransferFile().getSize();
		} catch (FileNotFoundException e) {
			// Not sure about this yet, just log the exception
			logger.warn("Bug?",e);
		}
		return (ts.getTransfered() * 100) / size;
	}

	public CommandResponse doSITE_SWHO(CommandRequest request) {
		return doListConnections(request, "swho", true, true, true, true, true, true, false);
	}
	
	public CommandResponse doSITE_WHO(CommandRequest request) {
		return doListConnections(request, "who", true, true, true, true, true, true, false);
	}

	public CommandResponse doLeechers(CommandRequest request) {
		boolean returnresponse = true;
		CommandResponse response = doListConnections(request, "who", false, true, false, false, false, false, false);
		if (response.getComment().size() == 0) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"download.empty", request.getUser()));
		}

		if (response.getComment().toString().contains(request.getArgument().toString())) {
			return response;
		} else if (request.getArgument() != null) {
			//We got arguments, but no matches
			if (returnresponse) {
				response.clear();
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"download.empty", request.getUser()));
				returnresponse = false;
				return response;
			} else {
				return null;
			}
		}
		return response;
	}

	public CommandResponse doUploaders(CommandRequest request) {
		boolean returnresponse = true;
		CommandResponse response = doListConnections(request, "who", true, false, false, false, false, false, false);
		if (response.getComment().size() == 0) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"upload.empty", request.getUser()));
		}
	
		if (response.getComment().toString().contains(request.getArgument().toString())) {
			return response;
		} else if (request.getArgument() != null) {
			//We got arguments, but no matches
			if (returnresponse) {
				response.clear();
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"download.empty", request.getUser()));
				returnresponse = false;
				return response;
			} else {
				return null;
			}
		}
		return response;
	}

	public CommandResponse doIdlers(CommandRequest request) {
		CommandResponse response = doListConnections(request, "who", false, false, true, false, false, false, false);
		if (response.getComment().size() == 0) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"idle.empty", request.getUser()));
		}
		return response;
	}

	public CommandResponse doBW(CommandRequest request) {
		int count = 1;
		
		if(request.hasArgument()) {
			try {
				int arg = Integer.parseInt(request.getArgument());
				count = (arg>5) ? 5 : arg;
				count = (count<1) ? 1 : count;
			} catch(NumberFormatException e) {
				//no need to catch this
			}
		}
				
		CommandResponse response=null;
		
		while(count-->0) {
			try {
				CommandResponse currentResponse = doListConnections(request, "bw", false, false, false, false, false, true, false);
				if(response==null) {
					response=currentResponse;
				} else {
					response.addComment(currentResponse.getComment().elementAt(0));
				}
				if(count>0)
					Thread.sleep(1000);
				} catch (InterruptedException ie) {
					//Handle exception
				}
			}
		return response;
		
	}

	public CommandResponse doSpeed(CommandRequest request) {
		String userName = "";

		if (!request.hasArgument()) {
			userName = request.getUser();
			request.setArgument(userName);
		} else {
			userName = request.getArgument();
		}

		try {
			GlobalContext.getGlobalContext().getUserManager().getUserByName(userName);
		} catch (NoSuchUserException e) {
			return new CommandResponse(501, "Invalid username!");
		} catch (UserFileException e) {
			return new CommandResponse(500, "User file corrupt: " + e.getMessage());
		}

		return doListConnections(request, "speed", true, true, true, false, false, false, true);
	}

	public CommandResponse doSITE_BAN(CommandRequest request)
			throws ImproperUsageException {

		Session session = request.getSession();
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
			banMsg = "[" + session.getUserNull(request.getUser()).getName() + "]";
			while (st.hasMoreTokens())
				banMsg += " " + st.nextToken();
		} else {
			banMsg = "Banned by " + session.getUserNull(request.getUser()).getName() + " for "
					+ banTime + "m";
		}

		myUser.getKeyedMap().setObject(UserManagement.BAN_TIME,
				new Date(System.currentTimeMillis() + (banTime * 60000)));
		myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, banMsg);
		myUser.commit();

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_BANALL(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(request.getArgument());

		long banTime;
		try {
			banTime = Long.parseLong(st.nextToken());
		} catch (NumberFormatException e) {
			logger.warn("", e);
			return new CommandResponse(200, e.getMessage());
		}

		String executioner = request.getUser();

		String banMsg;
		if (st.hasMoreTokens()) {
			banMsg = "[" + executioner + "]";
			while (st.hasMoreTokens())
				banMsg += " " + st.nextToken();
		} else {
			banMsg = "Banned by " + executioner + " for "
					+ banTime + "m";
		}

		for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
			if (user.getName().equals(executioner))
				continue;
			user.getKeyedMap().setObject(UserManagement.BAN_TIME,
				new Date(System.currentTimeMillis() + (banTime * 60000)));
			user.getKeyedMap().setObject(UserManagement.BAN_REASON, banMsg);
			user.commit();
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

		myUser.commit();

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_UNBANALL(CommandRequest request) {

		for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
			user.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
			user.getKeyedMap().setObject(UserManagement.BAN_REASON, "");
			user.commit();
		}

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_BANS(CommandRequest request) {
		Collection<User> myUsers = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		for (User user : myUsers) {
			long timeleft = user.getKeyedMap().getObject(
					UserManagement.BAN_TIME, new Date()).getTime()
					- System.currentTimeMillis();
			if (timeleft > 0) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("timeleft", "" + (timeleft / 60000));
				response.addComment(request.getSession().jprintf(
						_bundle, _keyPrefix+"bans", env, user.getName()));
			}
		}

		return response;
	}

	public CommandResponse doCredits(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();
		User user;
		if (!request.hasArgument()) {
			try {
				user = GlobalContext.getGlobalContext().getUserManager().getUserByName(request.getUser());
			} catch (NoSuchUserException e) {
				// this can't happen as we are running the command as this user, therefore they must exist
				return response;
			} catch (UserFileException e) {
                logger.warn("Error loading userfile for {}", request.getUser(), e);
				return response;
			}
		} else if (request.getArgument().equals("*")) {
			showAllUserCredits(request, response);
			return response;
		} else {
			try {
				user = GlobalContext.getGlobalContext().getUserManager().getUserByName(request.getArgument());
			} catch (NoSuchUserException e) {
				env.add("credituser", request.getArgument());
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"credits.error", env, request.getUser()));
				return response;
			} catch (UserFileException e) {
                logger.warn("Error loading userfile for {}", request.getUser(), e);
				return response;
			}
		}
		env.add("credituser", user.getName());
		env.add("creditscount",Bytes.formatBytes(user.getCredits()));
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"credits.user", env, request.getUser()));
		return response;
	}

	protected void showAllUserCredits(CommandRequest request, CommandResponse response) {
		long totalcredits = 0;
		ReplacerEnvironment env = new ReplacerEnvironment();

		ArrayList<User> users = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());
		for (User user : users) {
			totalcredits += user.getCredits();
		}
		env.add("usercount",Integer.toString(users.size()));
		env.add("totalcredits",Bytes.formatBytes(totalcredits));
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"credits.total", env, request.getUser()));
	}
}
