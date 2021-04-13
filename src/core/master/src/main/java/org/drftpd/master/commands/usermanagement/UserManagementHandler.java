/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.commands.usermanagement;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.Master;
import org.drftpd.master.commands.*;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.network.TransferState;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.*;
import org.drftpd.master.util.Time;
import org.drftpd.slave.exceptions.FileExistsException;
import org.drftpd.slave.network.Transfer;

import java.io.FileNotFoundException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class UserManagementHandler extends CommandInterface {
    public static final Key<List<BaseFtpConnection>> CONNECTIONS = new Key<>(UserManagement.class, "connections");
    private static final Logger logger = LogManager.getLogger(UserManagement.class);
    private static final UserCaseInsensitiveComparator USER_CASE_INSENSITIVE_COMPARATOR = new UserCaseInsensitiveComparator();
    private ResourceBundle _bundle;

    private static final double FAIRNESS_DEFAULT_RATIO_OK = 0.5;
    private static final double FAIRNESS_DEFAULT_RATIO_GOOD = 1.0;
    private static final double FAIRNESS_DEFAULT_RATIO_AWESOME = 2.0;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();
    }

    public CommandResponse doADDIP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        Session session = request.getSession();

        if (args.length < 2) {
            return new CommandResponse(501, session.jprintf(_bundle, "addip.specify", request.getUser()));
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[0]);
            User currentUser = session.getUserNull(request.getUser());

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            Map<String, Object> env = new HashMap<>();
            env.put("targetuser", requestedUser.getName());

            for (int i = 1; i < args.length; i++) {
                String string = args[i].replace(",", ""); // strip commas (for easy copy+paste)
                env.put("mask", string);

                try {
                    requestedUser.addIPMask(string);
                    response.addComment(session.jprintf(_bundle, "addip.success", env, request.getUser()));
                    logger.info("'{}' added ip '{}' to '{}'", currentUser.getName(), string, requestedUser.getName());
                } catch (DuplicateElementException e) {
                    response.addComment(session.jprintf(_bundle, "addip.dupe", env, request.getUser()));
                }
            }

            requestedUser.commit(); // throws UserFileException

        } catch (NoSuchUserException ex) {
            return new CommandResponse(452, "No such user: " + args[0]);
        } catch (UserFileException ex) {
            response.addComment(ex.getMessage());
        }

        return response;
    }

    /**
     * USAGE: site adduser <user><password>[ <ident@ip#1>... <ident@ip#5>] Adds
     * a user. You can have wild cards for users that have dynamic ips Examples:
     * *@192.168.1.* , frank@192.168.*.* , bob@192.*.*.* (*@192.168.1.1[5-9]
     * will allow only 192.168.1.15-19 to connect but no one else)
     * <p>
     * If a user is added by a groupadmin, that user will have the GLOCK flag
     * enabled and will inherit the groupadmin's home directory.
     * <p>
     * All default values for the user are read from file default.user in
     * /glftpd/ftp-data/users. Comments inside describe what is what. Gadmins
     * can be assigned their own default. <group>userfiles as templates to be
     * used when they add a user, if one is not found, default.user will be
     * used. default.groupname files will also be used for "site gadduser".
     * <p>
     * ex. site ADDUSER Archimede mypassword
     * <p>
     * This would add the user 'Archimede' with the password 'mypassword'.
     * <p>
     * ex. site ADDUSER Archimede mypassword *@127.0.0.1
     * <p>
     * This would do the same as above + add the ip '*@127.0.0.1' at the same
     * time.
     * <p>
     * HOMEDIRS: After login, the user will automatically be transferred into
     * his/her homedir. As of 1.16.x this dir is now "kinda" chroot'ed and they
     * are now unable to "cd ..".
     * <p>
     * <p>
     * <p>
     * USAGE: site gadduser <group><user><password>[ <ident@ip#1 ..
     * ident@ip#5>] Adds a user and changes his/her group to <group>. If
     * default.group exists, it will be used as a base instead of default.user.
     * <p>
     * Only public groups can be used as <group>.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doGenericAddUser(boolean isGAdduser, CommandRequest request) throws ImproperUsageException {

        Session session = request.getSession();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        // The user requesting this command
        User currentUser = session.getUserNull(request.getUser());

        boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdmin(currentUser);
        boolean isAdmin = currentUser.isAdmin();

        if (!isGroupAdmin && !isAdmin) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        // Start the string tokenizer
        StringTokenizer st = new StringTokenizer(request.getArgument());

        Group g = null;
        if (isGAdduser) {
            g = session.getGroupNull(st.nextToken());
            // Make sure the group actually exists...
            if (g == null) {
                return new CommandResponse(500, "Group does not exist");
            }
        }

        // If the currentUser is a group admin check if he actually is the group admin for the group we are adding a user to and that there are enough slots available
        if (isGroupAdmin) {
            if (!isGAdduser) {
                // regular adduser, take over primary group of user
                g = currentUser.getGroup();
            }
            if (!g.isAdmin(currentUser)) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
            Collection<User> groupUsers = GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(g);
            int users = groupUsers.size();
            logger.debug("Group: [{}], users[{}]: [{}]", g.getName(), users, groupUsers);
            if (users >= g.getGroupSlots()) {
                return new CommandResponse(452, session.jprintf(_bundle, "adduser.noslots", request.getUser()));
            }
        }

        User newUser;
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        try {
            String newUsername = st.nextToken();
            env.put("targetuser", newUsername);
            String pass = st.nextToken();

            Properties cfg = ConfigLoader.loadConfig("defaultuser.conf");

            String ratio = cfg.getProperty("ratio", "3.0");
            String maxlogins = cfg.getProperty("max_logins", "2");
            String maxloginsip = cfg.getProperty("max_logins_ip", "2");
            String maxsimup = cfg.getProperty("max_uploads", "2");
            String maxsimdn = cfg.getProperty("max_downloads", "2");
            String idletime = cfg.getProperty("idle_time", "300");
            String wklyallotment = cfg.getProperty("wkly_allotment", "0");
            String credits = cfg.getProperty("credits", "0b");
            String tagline = cfg.getProperty("tagline", "No tagline set.");
            String group = cfg.getProperty("group", "NoGroup");
            // If we do not have a group yet we got here using 'adduser' and we pick the default group
            if (g == null) {
                g = session.getGroupNull(group);
                // Make sure the group actually exists...
                if (g == null) {
                    return new CommandResponse(500, "Defaultuser group does not exist");
                }
            }

            float ratioVal = Float.parseFloat(ratio);
            int maxloginsVal = Integer.parseInt(maxlogins);
            int maxloginsipVal = Integer.parseInt(maxloginsip);
            int maxsimupVal = Integer.parseInt(maxsimup);
            int maxsimdnVal = Integer.parseInt(maxsimdn);
            int idletimeVal = Integer.parseInt(idletime);
            long creditsVal = Bytes.parseBytes(credits);
            long wklyallotmentVal = Bytes.parseBytes(wklyallotment);

            // action, no more NoSuchElementException below here
            newUser = GlobalContext.getGlobalContext().getUserManager().createUser(newUsername);

            newUser.setPassword(pass);
            newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
            newUser.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
            newUser.getKeyedMap().setObject(UserManagement.BANTIME, new Date());
            newUser.getKeyedMap().setObject(UserManagement.COMMENT, "Added by " + currentUser.getName());

            // TODO fix this.
            //newUser.getKeyedMap().setObject(Statistics.LOGINS,0);
            newUser.getKeyedMap().setObject(UserManagement.IRCIDENT, "");
            newUser.getKeyedMap().setObject(UserManagement.TAGLINE, tagline);
            newUser.getKeyedMap().setObject(UserManagement.RATIO, ratioVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXLOGINS, maxloginsVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, maxloginsipVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXSIMUP, maxsimupVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXSIMDN, maxsimdnVal);
            newUser.getKeyedMap().setObject(UserManagement.WKLYALLOTMENT, wklyallotmentVal);

            newUser.setIdleTime(idletimeVal);
            newUser.setCredits(creditsVal);

            newUser.setGroup(g);
            logger.info("'{}' added '{}' with group {}'", request.getUser(), newUser.getName(), newUser.getGroup());
            env.put("primgroup", newUser.getGroup());
            response.addComment(session.jprintf(_bundle, "adduser.primgroup", env, request.getUser()));

            newUser.commit();
            response.addComment(session.jprintf(_bundle, "adduser.success", env, request.getUser()));

        } catch (FileExistsException e) {
            return new CommandResponse(500, "User already exists");
        } catch (NoSuchElementException e) {
            return new CommandResponse(501, session.jprintf(_bundle, "adduser.missingpass", request.getUser()));
        } catch (UserFileException e) {
            logger.error(e, e);
            return new CommandResponse(452, e.getMessage());
        } catch (NumberFormatException e) {
            logger.error(e, e);
            return new CommandResponse(501, e.getMessage());
        } catch (IllegalArgumentException e) {
            logger.error(e, e);
            return new CommandResponse(500, e.getMessage());
        }

        while (st.hasMoreTokens()) {
            String string = st.nextToken().replace(",", ""); // strip commas (for easy copy+paste)
            env.put("mask", string);
            try {
                newUser.addIPMask(string);
                response.addComment(session.jprintf(_bundle, "addip.success", env, request.getUser()));
                logger.info("'{}' added ip '{}' to '{}'", request.getUser(), string, newUser.getName());
            } catch (DuplicateElementException e1) {
                response.addComment(session.jprintf(_bundle, "addip.dupe", env, request.getUser()));
            }
        }

        newUser.commit();

        return response;
    }

    public CommandResponse doADDUSER(CommandRequest request) throws ImproperUsageException {
        return doGenericAddUser(false, request);
    }

    public CommandResponse doGADDUSER(CommandRequest request) throws ImproperUsageException {
        return doGenericAddUser(true, request);
    }

    /**
     * USAGE: site change <user><field><value>- change a field for a user site
     * change =<group><field><value>- change a field for each member of group
     * <group>site change {<user1><user2>.. }<field><value>- change a field
     * for each user in the list site change *<field><value>- change a field
     * for everyone
     * <p>
     * Type "site change user help" in glftpd for syntax.
     * <p>
     * Fields available:
     * <p>
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
     * <p>
     * Flags available:
     * <p>
     * Flagname Flag Description
     * ------------------------------------------------------------- SITEOP 1
     * User is siteop. GADMIN 2 User is Groupadmin of his/her first public group
     * (doesn't work for private groups). GLOCK 3 User cannot change group.
     * EXEMPT 4 Allows to log in when site is full. Also allows user to do "site
     * idle 0", which is the same as having the idler flag. Also exempts the
     * user from the sim_xfers limit in config file. COLOR 5 Enable/Disable the
     * use of color (toggle with "site color"). DELETED 6 User is deleted.
     * USEREDIT 7 "Co-Siteop" ANON 8 User is anonymous (per-session like login).
     * <p>
     * NOTE* The 1 flag is not GOD mode, you must have the correct flags for the
     * actions you wish to perform. NOTE* If you have flag 1 then you DO NOT
     * WANT flag 2
     * <p>
     * Restrictions placed on users flagged ANONYMOUS. 1. '!' on login is
     * ignored. 2. They cannot DELETE, RMDIR, or RENAME. 3. Userfiles do not
     * update like usual, meaning no stats will be kept for these users. The
     * userfile only serves as a template for the starting environment of the
     * logged in user. Use external scripts if you must keep records of their
     * transfer stats.
     * <p>
     * NUKE A User is allowed to use site NUKE. UNNUKE B User is allowed to use
     * site UNNUKE. UNDUPE C User is allowed to use site UNDUPE. KICK D User is
     * allowed to use site KICK. KILL E User is allowed to use site KILL/SWHO.
     * TAKE F User is allowed to use site TAKE. GIVE G User is allowed to use
     * site GIVE. USERS/USER H This allows you to view users ( site USER/USERS )
     * IDLER I User is allowed to idle forever. CUSTOM1 J Custom flag 1 CUSTOM2
     * K Custom flag 2 CUSTOM3 L Custom flag 3 CUSTOM4 M Custom flag 4 CUSTOM5 N
     * Custom flag 5
     * <p>
     * You can use custom flags in the config file to give some users access to
     * certain things without having to use private groups. These flags will
     * only show up in "site flags" if they're turned on.
     * <p>
     * ex. site change Archimede ratio 5
     * <p>
     * This would set the ratio to 1:5 for the user 'Archimede'.
     * <p>
     * ex. site change Archimede flags +2-AG
     * <p>
     * This would make the user 'Archimede' groupadmin and remove his ability to
     * use the commands site nuke and site give.
     * <p>
     * NOTE: The flag DELETED can not be changed with site change, it will
     * change when someone does a site deluser/readd.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doCHANGEUSER(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Collection<User> users = new ArrayList<>();

        User userToChange;
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        StringTokenizer arguments = new StringTokenizer(request.getArgument());

        Session session = request.getSession();

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String username = arguments.nextToken();

        try {
            if (username.startsWith("=")) {
                // This request is to change something for all users in a group
                String group = username.replace("=", "");
                Group g = session.getGroupNull(group);
                if (g == null) {
                    return new CommandResponse(550, "Unknown group");
                }
                users = GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(g);
            } else if (username.equals("*")) {
                // This request is to change all users
                users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();
            } else {
                // Get the actual user
                users.add(GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(username));
            }
        } catch (NoSuchUserException e) {
            return new CommandResponse(550, "User " + username + " not found: " + e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.ERROR, "Error loading user", e);

            return new CommandResponse(550, "Error loading user: " + e.getMessage());
        }

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String command = arguments.nextToken().toLowerCase();

        User currentUser = session.getUserNull(request.getUser());

        boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdmin(currentUser);
        boolean isAdmin = currentUser.isAdmin();

        // Group admins are only allowed to change ratio. The actual group checking is done later below
        if (!isAdmin && isGroupAdmin && !command.equals("ratio")) {
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
            fullCommandArgument = fullCommandArgument + " " + commandArguments[x];
        }

        fullCommandArgument = fullCommandArgument.trim();

        for (User user1 : users) {
            userToChange = user1;
            env.put("targetuser", userToChange.getName());

            switch (command) {
                case "ratio":
                    // //// Ratio //////
                    if (commandArguments.length != 1) {
                        throw new ImproperUsageException();
                    }

                    float ratio = Float.parseFloat(commandArguments[0]);

                    if (isAdmin) {
                        ////// Ratio changes by an admin //////
                        logger.info("'{}' changed ratio for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO), ratio);
                        userToChange.getKeyedMap().setObject(UserManagement.RATIO, ratio);
                        env.put("newratio", Float.toString(userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
                        response.addComment(session.jprintf(_bundle, "changeratio.success", env, request.getUser()));
                    } else if (isGroupAdmin) {
                        ////// Group Admin Ratio //////
                        Group g = GlobalContext.getGlobalContext().getUserManager().getGroupByGroupAdminOfUser(currentUser, userToChange);
                        if (g == null) {
                            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
                        }
                        if (ratio == 0F) {
                            int usedleechslots = 0;

                            for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(g)) {
                                if ((user).getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0F) {
                                    usedleechslots++;
                                }
                            }

                            if (usedleechslots >= g.getLeechSlots()) {
                                return new CommandResponse(452, session.jprintf(_bundle, "changeratio.nomoreslots", request.getUser()));
                            }
                        } else if (ratio < g.getMinRatio() || ratio > g.getMaxRatio()) {
                            env.put("minratio", g.getMinRatio());
                            env.put("maxratio", g.getMaxRatio());
                            return new CommandResponse(452, session.jprintf(_bundle, "changeratio.invalidratio", env, request.getUser()));
                        }

                        logger.info("'{}' changed ratio for '{}' from '{}' to '{}'", currentUser.getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO), ratio);
                        userToChange.getKeyedMap().setObject(UserManagement.RATIO, ratio);
                        env.put("newratio", Float.toString(userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
                        response.addComment(session.jprintf(_bundle, "changeratio.success", env, request.getUser()));

                    } else {
                        return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
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
                    env.put("newcredits", Bytes.formatBytes(userToChange.getCredits()));
                    response.addComment(session.jprintf(_bundle,
                            "changecredits.success", env, request.getUser()));
                    break;
                case "comment":
                    logger.info("'{}' changed comment for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectString(
                            UserManagement.COMMENT), fullCommandArgument);
                    userToChange.getKeyedMap().setObject(UserManagement.COMMENT,
                            fullCommandArgument);
                    env.put("comment", userToChange.getKeyedMap().getObjectString(
                            UserManagement.COMMENT));
                    response.addComment(session.jprintf(_bundle,
                            "changecomment.success", env, request.getUser()));
                    break;
                case "idle_time":
                    if (commandArguments.length != 1) {
                        throw new ImproperUsageException();
                    }

                    int idleTime = Integer.parseInt(commandArguments[0]);
                    env.put("oldidletime", "" + userToChange.getIdleTime());
                    logger.info("'{}' changed idle_time for '{}' from '{} to '{}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getIdleTime(), idleTime);
                    userToChange.setIdleTime(idleTime);
                    env.put("newidletime", "" + idleTime);
                    response.addComment(session.jprintf(_bundle,
                            "changeidletime.success", env, request.getUser()));
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
                        env.put("numlogins", "" + numLogins);
                        env.put("numloginsip", "" + numLoginsIP);
                        response.addComment(session.jprintf(_bundle,
                                "changenumlogins.success", env, request.getUser()));
                    } catch (NumberFormatException ex) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }

                    // } else if ("max_dlspeed".equalsIgnoreCase(command)) {
                    // myUser.setMaxDownloadRate(Integer.parseInt(commandArgument));
                    // } else if ("max_ulspeed".equals(command)) {
                    // myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
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
                        env.put("maxdn", "" + maxdn);
                        env.put("maxup", "" + maxup);
                        response.addComment(session.jprintf(_bundle,
                                "changemaxsim.success", env, request.getUser()));

                    } catch (NumberFormatException ex) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }
                    break;
                case "group":
                    if (commandArguments.length != 1) {
                        throw new ImproperUsageException();
                    }

                    String newGroup = commandArguments[0];
                    Group g = session.getGroupNull(newGroup);
                    if (g == null) {
                        return new CommandResponse(550, "Unknown group");
                    }

                    logger.info("'{}' changed primary group for '{}' from '{}' to '{}'", currentUser.getName(), userToChange.getName(), userToChange.getGroup().getName(), g.getName());
                    userToChange.setGroup(g);
                    env.put("primgroup", userToChange.getGroup().getName());
                    response.addComment(session.jprintf(_bundle, "changeprimgroup.success", env, request.getUser()));

                    break;
                case "created":
                    Date myDate;

                    if (commandArguments.length != 0) {
                        try {
                            myDate = new SimpleDateFormat("yyyy-MM-dd").parse(commandArguments[0]);
                        } catch (ParseException e1) {
                            logger.log(Level.INFO, e1);

                            return new CommandResponse(452, e1.getMessage());
                        }
                    } else {
                        myDate = new Date();
                    }

                    logger.info("'{}' changed created for '{}' from '{}' to '{}'", currentUser.getName(), userToChange.getName(), userToChange.getKeyedMap().getObject(UserManagement.CREATED, new Date(0)), myDate);
                    userToChange.getKeyedMap().setObject(UserManagement.CREATED, myDate);

                    response = new CommandResponse(200, session.jprintf(_bundle, "changeuser.created.success", env, request.getUser()));
                    break;
                case "wkly_allotment":
                    if (commandArguments.length != 1) {
                        throw new ImproperUsageException();
                    }

                    long weeklyAllotment = Bytes.parseBytes(commandArguments[0]);
                    logger.info("'{}' changed wkly_allotment for '{}' from '{}' to {}'", session.getUserNull(request.getUser()).getName(), userToChange.getName(), userToChange.getKeyedMap().getObjectLong(
                            UserManagement.WKLYALLOTMENT), weeklyAllotment);
                    userToChange.getKeyedMap().setObject(UserManagement.WKLYALLOTMENT,
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
     * USAGE: site chpass <user><password>Change users password.
     * <p>
     * ex. site chpass Archimede newpassword This would change the password to
     * 'newpassword' for the user 'Archimede'.
     * <p>
     * See "site passwd" for more info if you get a "Password is not secure
     * enough" error. * Denotes any password, ex. site chpass arch * This will
     * allow arch to login with any password
     *
     * @throws ImproperUsageException @
     *                                Denotes any email-like password, ex. site chpass arch @ This will allow
     *                                arch to login with a@b.com but not ab.com
     */
    public CommandResponse doCHPASS(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[0]);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            requestedUser.setPassword(args[1]);
            requestedUser.commit();
            logger.info("'{}' changed password for '{}'", currentUser.getName(), requestedUser.getName());

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
    public CommandResponse doDELIP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length < 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[0]);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            for (int i = 1; i < args.length; i++) {
                String string = args[i].replace(",", ""); // strip commas (for easy copy+paste)

                try {
                    requestedUser.removeIpMask(string);
                    logger.info("'{}' removed ip '{}' from '{}'", currentUser.getName(), string, requestedUser);
                    response.addComment("Removed " + string);
                } catch (NoSuchFieldException e1) {
                    response.addComment("Mask " + string + " not found: " + e1.getMessage());
                }
            }

            requestedUser.commit();
        } catch (NoSuchUserException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error", e);

            return new CommandResponse(452, "IO error: " + e.getMessage());
        }

        return response;
    }

    public CommandResponse doDELPURGE(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        String delUsername = st.nextToken();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetuser", delUsername);

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(delUsername);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            requestedUser.setDeleted(true);
            String reason = "";
            if (st.hasMoreTokens()) {
                requestedUser.getKeyedMap().setObject(UserManagement.REASON, reason = st.nextToken("").substring(1));
            }
            requestedUser.commit();
            response.addComment(session.jprintf(_bundle, "deluser.success", env, request.getUser()));
            logger.info("'{}' deleted user '{}' with reason '{}'", currentUser.getName(), requestedUser.getName(), reason);
            logger.debug("reason {}", requestedUser.getKeyedMap().getObjectString(UserManagement.REASON));

            requestedUser.purge();
            response.addComment(session.jprintf(_bundle, "purgeuser.success", env, request.getUser()));
            logger.info("'{}' purged '{}'", currentUser.getName(), requestedUser.getName());

        } catch (NoSuchUserException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(452, "Couldn't getUser: " + e.getMessage());
        }

        return response;
    }

    public CommandResponse doDELUSER(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        String delUsername = st.nextToken();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetuser", delUsername);

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(delUsername);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            requestedUser.setDeleted(true);
            String reason = "";
            if (st.hasMoreTokens()) {
                requestedUser.getKeyedMap().setObject(UserManagement.REASON, reason = st.nextToken("").substring(1));
            }
            requestedUser.commit();
            response.addComment(session.jprintf(_bundle, "deluser.success", env, request.getUser()));
            logger.info("'{}' deleted user '{}' with reason '{}'", currentUser.getName(), requestedUser.getName(), reason);
            logger.debug("reason {}", requestedUser.getKeyedMap().getObjectString(UserManagement.REASON));

        } catch (NoSuchUserException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(452, "Couldn't getUser: " + e.getMessage());
        }

        return response;
    }

    public CommandResponse doFAIRNESS(CommandRequest request) {

        Collection<User> users;
        if (request.hasArgument()) {
            StringTokenizer st = new StringTokenizer(request.getArgument());
            if (st.countTokens() != 1) {
                return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            }
            String arg = st.nextToken();
            if(arg.charAt(0) == '=') {
                // Deal with the scenario where we only get a '='
                if (arg.length() <= 1) {
                    return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                }
                try {
                    users = GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(GlobalContext.getGlobalContext().getUserManager().getGroupByName(arg.substring(1)));
                } catch (NoSuchGroupException | GroupFileException e) {
                    return new CommandResponse(550, "Requested group " + arg.substring(1) + " does not exist");
                }
            } else
            {
                users = new ArrayList<>();
                try {
                    users.add(GlobalContext.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(arg));
                } catch (NoSuchUserException | UserFileException e) {
                    return new CommandResponse(550, "Requested user " + arg + " does not exist");
                }
            }
        } else {
            users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();
        }

        // If we get here we are supposed to have at least 1 entry in our users collection
        if (users.size() <= 0) {
            return new CommandResponse(550, "An unexpected situation has occurred. Stopping execution");
        }
        FairnessConfig cfg = new FairnessConfig();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        for (User user : users) {
            env.put("username", user.getName());
            env.put("bytesup", Bytes.formatBytes(user.getUploadedBytes()));
            env.put("bytesdn", Bytes.formatBytes(user.getDownloadedBytes()));

            if (user.getDownloadedBytes() <= 0 || user.getUploadedBytes() <= 0) {
                response.addComment(request.getSession().jprintf(_bundle, "fairness.noratio", env, user.getName()));
            } else {

                double fairnessratio = (double) user.getUploadedBytes() / (double) user.getDownloadedBytes();
                env.put("fairnessratio", new DecimalFormat("0.00").format(fairnessratio));

                if (fairnessratio < cfg.getOk()) {
                    response.addComment(request.getSession().jprintf(_bundle, "fairness.bad", env, user.getName()));
                } else if (fairnessratio < cfg.getGood()) {
                    response.addComment(request.getSession().jprintf(_bundle, "fairness.ok", env, user.getName()));
                } else if (fairnessratio < cfg.getAwesome()) {
                    response.addComment(request.getSession().jprintf(_bundle, "fairness.good", env, user.getName()));
                } else {
                    response.addComment(request.getSession().jprintf(_bundle, "fairness.awesome", env, user.getName()));
                }
            }
        }
        return response;
    }

    public CommandResponse doSWAP(CommandRequest request) throws ImproperUsageException {
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
            return new CommandResponse(452, "You cannot give more credits than you have.");
        }

        logger.info("'{}' transfered {} ('{}') to '{}'", srcUser.getName(), Bytes.formatBytes(credits), credits, destUser.getName());

        srcUser.updateCredits(-credits);
        srcUser.commit();
        destUser.updateCredits(credits);
        destUser.commit();

        return new CommandResponse(200, "OK, gave " + Bytes.formatBytes(credits) + " of " + srcUser.getName() + "'s credits to " + destUser.getName());
    }

    public CommandResponse doGIVE(CommandRequest request) throws ImproperUsageException {

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

    public CommandResponse doKICK(CommandRequest request) throws ImproperUsageException {

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

    public CommandResponse doKICKALL(CommandRequest request) {

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

    public CommandResponse doKILL(CommandRequest request) throws ImproperUsageException {
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

    public CommandResponse doPASSWD(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        logger.info("'{}' changed his password", currentUser.getName());
        currentUser.setPassword(request.getArgument());
        currentUser.commit();

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doPURGE(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String delUsername = request.getArgument();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetuser", delUsername);

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(delUsername);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            if (!requestedUser.isDeleted()) {
                return new CommandResponse(452, "User isn't deleted");
            }
            requestedUser.purge();
            response.addComment(session.jprintf(_bundle, "purgeuser.success", env, request.getUser()));
            logger.info("'{}' purged '{}'", currentUser.getName(), requestedUser.getName());

        } catch (NoSuchUserException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(452, "Couldn't getUser: " + e.getMessage());
        }

        return response;
    }

    public CommandResponse doREADD(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String requestedUsername = request.getArgument();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(requestedUsername);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isAdmin = currentUser.isAdmin();

            if (!isAdmin && !isGroupAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            if (!requestedUser.isDeleted()) {
                return new CommandResponse(452, "User wasn't deleted");
            }

            requestedUser.setDeleted(false);
            requestedUser.getKeyedMap().remove(UserManagement.REASON);
            logger.info("'{}' readded '{}'", currentUser.getName(), requestedUser.getName());
            requestedUser.commit();
        } catch (NoSuchUserException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(452, "IO error: " + e.getMessage());
        }

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doRENUSER(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }
        String argsUserName = args[0];
        String argsUserNameNew = args[1];

        Session session = request.getSession();
        String requesterUsername = session.getUserNull(request.getUser()).getName();

        // This is a safe guard as a lot of places assume the "drftpd" user exists
        if (argsUserName.equalsIgnoreCase("drftpd")) {
            logger.warn("{} Tried to rename [{}] user which is not allowed", requesterUsername, argsUserName);
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        try {
            User myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(argsUserName);
            String oldUsername = myUser.getName();
            // We need to get the requester username before we rename as we might be renaming ourselves...
            myUser.rename(argsUserNameNew);
            BaseFtpConnection.fixBaseFtpConnUser(oldUsername, myUser.getName());
            // Fix the request user reference
            if (requesterUsername.equals(oldUsername)) {
                request.setUser(myUser.getName());
            }
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

    public CommandResponse doSEEN(CommandRequest request) throws ImproperUsageException {

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

    public CommandResponse doTAGLINE(CommandRequest request) throws ImproperUsageException {

        Session session = request.getSession();
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        User u = session.getUserNull(request.getUser());

        logger.info("'{}' changed his tagline from '{}' to '{}'", request.getUser(), u.getKeyedMap().getObjectString(UserManagement.TAGLINE), request.getArgument());

        u.getKeyedMap().setObject(UserManagement.TAGLINE, request.getArgument());
        u.commit();

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doDEBUG(CommandRequest request) {
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
        return new CommandResponse(200, session.jprintf(_bundle, "debug", request.getUser()));
    }

    /**
     * USAGE: site take <user><kbytes>[ <message>] Removes credit from user
     * <p>
     * ex. site take Archimede 100000 haha
     * <p>
     * This will remove 100mb of credits from the user 'Archimede' and send the
     * message haha to him.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doTAKE(CommandRequest request) throws ImproperUsageException {

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
     * <p>
     * ex. site user
     * <p>
     * This will display a list of all users currently on site.
     * <p>
     * ex. site user Archimede
     * <p>
     * This will show detailed information about user 'Archimede'.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doUSER(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String requestedUsername = request.getArgument();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());
        boolean isAdmin = currentUser.isAdmin();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        try {
            User requestedUser = GlobalContext.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(requestedUsername);

            boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdminOfUser(currentUser, requestedUser);
            boolean isMyself = currentUser.equals(requestedUser);

            if (!isAdmin && !isGroupAdmin && !isMyself) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }

            String userData = request.getSession().jprintf(_bundle, "user", requestedUser.getName());
            response.addComment(userData);

        } catch (NoSuchUserException ex) {
            // Only global admins get the real error
            if (!isAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
            response.setMessage("User " + request.getArgument() + " not found");
            return response;
        } catch (UserFileException ex) {
            // Only global admins get the real error
            if (!isAdmin) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
            return new CommandResponse(452, "Userfile error: " + ex.getMessage());
        }

        return response;
    }

    public CommandResponse doUSERS(CommandRequest request) {

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

        Map<String, Object> env = new HashMap<>();

        List<BaseFtpConnection> conns = request.getSession().getObject(CONNECTIONS, null);
        if (conns == null) {
            conns = Master.getConnectionManager().getConnections();
        }

        for (BaseFtpConnection conn : conns) {
            if (!conn.isAuthenticated()) {
                if (!restrictUser) {
                    env.put("targetuser", "<new>");
                    env.put("ip", conn.getClientAddress().getHostAddress());
                    env.put("idle", Time.formatTime(System.currentTimeMillis() - conn.getLastActive()));

                    if (!conn.isExecuting() && idle) {
                        response.addComment(session.jprintf(_bundle, type + ".new", env, request.getUser()));
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

            env.put("targetuser", user.getName());
            env.put("ip", conn.getObject(BaseFtpConnection.ADDRESS, null).getHostAddress());
            env.put("thread", conn.getThreadID());

            synchronized (conn) {
                TransferState ts = conn.getTransferState();

                env.put("idle", Time.formatTime(System.currentTimeMillis()
                        - conn.getLastActive()));

                if (!conn.isExecuting() && idle) {
                    response.addComment(session.jprintf(_bundle, type + ".idle", env, request.getUser()));
                    xferidle++;
                } else {
                    synchronized (ts) {
                        if (ts.isTransfering()) {
                            speed = ts.getXferSpeed();
                            env.put("speed", Bytes.formatBytes(speed) + "/s");
                            env.put("slave", ts.getTransferSlave().getName());
                            env.put("file", ts.getTransferFile().getPath());
                            char direction = ts.getDirection();
                            if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                                if (up) {
                                    response.addComment(session.jprintf(
                                            _bundle, type + ".up",
                                            env, request.getUser()));
                                }
                                speedup += speed;
                                xfersup++;
                            } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                                if (down) {
                                    env.put("percentcomplete",
                                            calculateProgress(ts));
                                    response.addComment(session.jprintf(
                                            _bundle, type
                                                    + ".down", env, request
                                                    .getUser()));
                                }
                                speeddn += speed;
                                xfersdn++;
                            }
                        } else if (command) {
                            env.put("command", conn.getRequest()
                                    .getCommand());
                            response.addComment(session.jprintf(_bundle,
                                    type + ".command", env,
                                    request.getUser()));
                        }
                    }
                }
            }
        }

        env.put("currentusers", conns.size());
        env.put("maxusers", GlobalContext.getConfig().getMaxUsersTotal());
        env.put("totalupspeed", Bytes.formatBytes(speedup) + "/s");
        env.put("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
        env.put("totalspeed", Bytes.formatBytes(speedup + speeddn) + "/s");
        env.put("xfersup", xfersup);
        env.put("xfersdn", xfersdn);
        env.put("xfers", xfersup + xfersdn);
        env.put("idlers", xferidle);
        if (response.getComment().size() > 0 && (statusSpeed || statusUsers)) {
            response.addComment("");
        }
        if (statusSpeed) {
            response.addComment(session.jprintf(_bundle, type + ".statusspeed", env, request.getUser()));
        }
        if (statusUsers) {
            response.addComment(session.jprintf(_bundle, type + ".statususers", env, request.getUser()));
        }

        return response;
    }

    private long calculateProgress(TransferState ts) {
        long size = 1;
        try {
            size = ts.getTransferFile().getSize();
        } catch (FileNotFoundException e) {
            // Not sure about this yet, just log the exception
            logger.warn("Bug?", e);
        }
        return (ts.getTransfered() * 100) / size;
    }

    public CommandResponse doSWHO(CommandRequest request) {
        return doListConnections(request, "swho", true, true, true, true, true, true, false);
    }

    public CommandResponse doWHO(CommandRequest request) {
        return doListConnections(request, "who", true, true, true, true, true, true, false);
    }

    public CommandResponse doLeechers(CommandRequest request) {
        boolean returnresponse = true;
        CommandResponse response = doListConnections(request, "who", false, true, false, false, false, false, false);
        if (response.getComment().size() == 0) {
            response.addComment(request.getSession().jprintf(_bundle, "download.empty", request.getUser()));
        }

        if (response.getComment().toString().contains(request.getArgument())) {
            return response;
        } else if (request.getArgument() != null) {
            //We got arguments, but no matches
            if (returnresponse) {
                response.clear();
                response.addComment(request.getSession().jprintf(_bundle, "download.empty", request.getUser()));
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
            response.addComment(request.getSession().jprintf(_bundle, "upload.empty", request.getUser()));
        }

        if (response.getComment().toString().contains(request.getArgument())) {
            return response;
        } else if (request.getArgument() != null) {
            //We got arguments, but no matches
            if (returnresponse) {
                response.clear();
                response.addComment(request.getSession().jprintf(_bundle, "download.empty", request.getUser()));
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
            response.addComment(request.getSession().jprintf(_bundle, "idle.empty", request.getUser()));
        }
        return response;
    }

    public CommandResponse doBW(CommandRequest request) {
        int count = 1;

        if (request.hasArgument()) {
            try {
                int arg = Integer.parseInt(request.getArgument());
                count = (arg > 5) ? 5 : arg;
                count = (count < 1) ? 1 : count;
            } catch (NumberFormatException e) {
                //no need to catch this
            }
        }

        CommandResponse response = null;

        while (count-- > 0) {
            try {
                CommandResponse currentResponse = doListConnections(request, "bw", false, false, false, false, false, true, false);
                if (response == null) {
                    response = currentResponse;
                } else {
                    response.addComment(currentResponse.getComment().elementAt(0));
                }
                if (count > 0)
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

    public CommandResponse doBAN(CommandRequest request) throws ImproperUsageException {

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

        myUser.getKeyedMap().setObject(UserManagement.BANTIME,
                new Date(System.currentTimeMillis() + (banTime * 60000)));
        myUser.getKeyedMap().setObject(UserManagement.BANREASON, banMsg);
        myUser.commit();

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doBANALL(CommandRequest request) throws ImproperUsageException {

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
            user.getKeyedMap().setObject(UserManagement.BANTIME,
                    new Date(System.currentTimeMillis() + (banTime * 60000)));
            user.getKeyedMap().setObject(UserManagement.BANREASON, banMsg);
            user.commit();
        }

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doUNBAN(CommandRequest request) throws ImproperUsageException {

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

        myUser.getKeyedMap().setObject(UserManagement.BANTIME, new Date());
        myUser.getKeyedMap().setObject(UserManagement.BANREASON, "");

        myUser.commit();

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doUNBANALL(CommandRequest request) {

        for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
            user.getKeyedMap().setObject(UserManagement.BANTIME, new Date());
            user.getKeyedMap().setObject(UserManagement.BANREASON, "");
            user.commit();
        }

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doBANS(CommandRequest request) {
        Collection<User> myUsers = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        for (User user : myUsers) {
            long timeleft = user.getKeyedMap().getObject(
                    UserManagement.BANTIME, new Date()).getTime()
                    - System.currentTimeMillis();
            if (timeleft > 0) {
                Map<String, Object> env = new HashMap<>();
                env.put("timeleft", "" + (timeleft / 60000));
                response.addComment(request.getSession().jprintf(_bundle, "bans", env, user.getName()));
            }
        }

        return response;
    }

    public CommandResponse doCredits(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
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
                env.put("credituser", request.getArgument());
                response.addComment(request.getSession().jprintf(_bundle, "credits.error", env, request.getUser()));
                return response;
            } catch (UserFileException e) {
                logger.warn("Error loading userfile for {}", request.getUser(), e);
                return response;
            }
        }
        env.put("credituser", user.getName());
        env.put("creditscount", Bytes.formatBytes(user.getCredits()));
        response.addComment(request.getSession().jprintf(_bundle, "credits.user", env, request.getUser()));
        return response;
    }

    protected void showAllUserCredits(CommandRequest request, CommandResponse response) {
        long totalcredits = 0;
        Map<String, Object> env = new HashMap<>();

        ArrayList<User> users = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());
        for (User user : users) {
            totalcredits += user.getCredits();
        }
        env.put("usercount", Integer.toString(users.size()));
        env.put("totalcredits", Bytes.formatBytes(totalcredits));
        response.addComment(request.getSession().jprintf(_bundle, "credits.total", env, request.getUser()));
    }

    static class UserCaseInsensitiveComparator implements Comparator<User> {
        @Override
        public int compare(User user0, User user1) {
            return String.CASE_INSENSITIVE_ORDER.compare(user0.getName(), user1.getName());
        }
    }

    private static class FairnessConfig {
        private double _ok;
        private double _good;
        private double _awesome;

        public FairnessConfig() {
            Properties p = ConfigLoader.loadConfig("fairness.conf");

            try {
                _ok = Double.parseDouble(p.getProperty("fairness.ok"));
            } catch(NumberFormatException e) {
                logger.error("Failed at parsing 'fairness.ok'", e);
                _ok = FAIRNESS_DEFAULT_RATIO_OK;
            } catch(Exception e) {
                logger.debug("A generic exception happened while trying to get 'fairness.ok', we should be able to ignore this");
                _ok = FAIRNESS_DEFAULT_RATIO_OK;
            }

            try {
                _good = Double.parseDouble(p.getProperty("fairness.good"));
            } catch(NumberFormatException e) {
                logger.error("Failed at parsing 'fairness.good'", e);
                _good = FAIRNESS_DEFAULT_RATIO_GOOD;
            } catch(Exception e) {
                logger.debug("A generic exception happened while trying to get 'fairness.good', we should be able to ignore this");
                _good = FAIRNESS_DEFAULT_RATIO_GOOD;
            }

            try {
                _awesome = Double.parseDouble(p.getProperty("fairness.awesome"));
            } catch(NumberFormatException e) {
                logger.error("Failed at parsing 'fairness.awesome'", e);
                _awesome = FAIRNESS_DEFAULT_RATIO_AWESOME;
            } catch(Exception e) {
                logger.debug("A generic exception happened while trying to get 'fairness.awesome', we should be able to ignore this");
                _awesome = FAIRNESS_DEFAULT_RATIO_AWESOME;
            }
        }

        public double getOk() { return _ok; }
        public double getGood() { return _good; }
        public double getAwesome() { return _awesome; }
    }
}
