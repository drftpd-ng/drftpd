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
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.*;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.slave.exceptions.FileExistsException;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class GroupManagementHandler extends CommandInterface {
    private static final Logger logger = LogManager.getLogger();
    private static final GroupCaseInsensitiveComparator GROUP_CASE_INSENSITIVE_COMPARATOR = new GroupCaseInsensitiveComparator();
    private static final UserCaseInsensitiveComparator USER_CASE_INSENSITIVE_COMPARATOR = new UserCaseInsensitiveComparator();
    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();
    }

    public CommandResponse doADDGROUP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();

        // The user requesting this command
        User currentUser = session.getUserNull(request.getUser());

        boolean isAdmin = currentUser.isAdmin();

        if (!isAdmin) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (st.countTokens() != 1) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }
        String newGroupname = st.nextToken();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetgroup", newGroupname);

        try {
            Properties cfg = ConfigLoader.loadConfig("defaultgroup.conf");
            String minratio = cfg.getProperty("min_ratio", "3.0");
            String maxratio = cfg.getProperty("max_ratio", "3.0");
            float minratioVal = Float.parseFloat(minratio);
            float maxratioVal = Float.parseFloat(maxratio);

            Group newGroup = GlobalContext.getGlobalContext().getUserManager().createGroup(newGroupname);

            newGroup.setCreated(new Date());
            newGroup.setGroupSlots(0);
            newGroup.setLeechSlots(0);
            newGroup.setMinRatio(minratioVal);
            newGroup.setMaxRatio(maxratioVal);

            logger.info("'{}' added group '{}'", request.getUser(), newGroup.getName());

            newGroup.commit();
            response.addComment(session.jprintf(_bundle, "addgroup.success", env, request.getUser()));

        } catch (IllegalArgumentException e) {
            return new CommandResponse(500, e.getMessage());
        } catch (FileExistsException e) {
            return new CommandResponse(500, "Group already exists");
        } catch (GroupFileException e) {
            logger.error(e, e);
            return new CommandResponse(452, e.getMessage());
        }

        return response;
    }

    public CommandResponse doDELGROUP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();

        // The user requesting this command
        User currentUser = session.getUserNull(request.getUser());

        boolean isAdmin = currentUser.isAdmin();

        if (!isAdmin) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (st.countTokens() != 1) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        String groupname = st.nextToken();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetgroup", groupname);

        try {
            Group requestedGroup = GlobalContext.getGlobalContext().getUserManager().getGroupByName(groupname);

            // Make sure the group is not used anymore
            if (GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(requestedGroup).size() != 0) {
                return new CommandResponse(500, "This group " + requestedGroup.getName() + " still has users attached");
            }

            requestedGroup.purge();
            response.addComment(session.jprintf(_bundle, "addgroup.success", env, request.getUser()));
            logger.info("'{}' purged '{}'", currentUser.getName(), requestedGroup.getName());
        } catch (GroupFileException e) {
            return new CommandResponse(452, e.getMessage());
        } catch (NoSuchGroupException e) {
            return new CommandResponse(500, "Group does not exist");
        }

        return response;
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
    public CommandResponse doCHANGEGROUP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Collection<Group> groups = new ArrayList<>();

        Group groupToChange;
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        StringTokenizer arguments = new StringTokenizer(request.getArgument());

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String groupname = arguments.nextToken();

        try {
            if (groupname.equals("*")) {
                // This request is to change all users
                groups = GlobalContext.getGlobalContext().getUserManager().getAllGroups();
            } else {
                // Get the actual group
                groups.add(GlobalContext.getGlobalContext().getUserManager().getGroupByNameUnchecked(groupname));
            }
        } catch (NoSuchGroupException e) {
            return new CommandResponse(550, "Group " + groupname + " not found: " + e.getMessage());
        } catch (GroupFileException e) {
            logger.log(Level.ERROR, "Error loading group", e);

            return new CommandResponse(550, "Error loading group: " + e.getMessage());
        }

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String command = arguments.nextToken().toLowerCase();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        boolean isAdmin = currentUser.isAdmin();

        // Only Site Admins are allowed here
        if (!isAdmin) {
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

        for (Group group1 : groups) {
            groupToChange = group1;
            env.put("targetgroup", groupToChange.getName());

            switch (command) {
                case "ratio":
                    // [# min] [# max]
                    if (commandArguments.length != 2) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }

                    try {
                        float minRatio = Float.parseFloat(commandArguments[0]);
                        float maxRatio = Float.parseFloat(commandArguments[1]);

                        env.put("minratio", "" + minRatio);
                        env.put("maxratio", "" + maxRatio);

                        logger.info("'{}' changed min/max ratio for group '{}' from '{}/{}' to '{}/{}'", currentUser.getName(), groupToChange.getName(), groupToChange.getMinRatio(), groupToChange.getMaxRatio(), minRatio, maxRatio);

                        if (minRatio < 1 || maxRatio < minRatio)
                            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");

                        groupToChange.setMinRatio(minRatio);
                        groupToChange.setMaxRatio(maxRatio);

                        response.addComment(session.jprintf(_bundle, "changegroup.ratio.success", env, request.getUser()));

                    } catch (NumberFormatException ex) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }
                    break;
                case "slots":
                    try {
                        if (commandArguments.length != 1) {
                            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                        }

                        int groupSlots = Short.parseShort(commandArguments[0]);

                        logger.info("'{}' changed group slots for '{}' from '{}' to '{}'", currentUser.getName(), groupToChange.getName(), groupToChange.getGroupSlots(), groupSlots);
                        groupToChange.setGroupSlots(groupSlots);
                        env.put("groupslots", "" + groupToChange.getGroupSlots());
                        response.addComment(session.jprintf(_bundle, "changegroup.slots.success", env, request.getUser()));
                    } catch (NumberFormatException ex) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }
                    break;
                case "leechslots":
                    try {
                        if (commandArguments.length != 1) {
                            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                        }

                        int leechSlots = Short.parseShort(commandArguments[0]);

                        logger.info("'{}' changed group leech slots for '{}' from '{}' to '{}'", currentUser.getName(), groupToChange.getName(), groupToChange.getLeechSlots(), leechSlots);
                        groupToChange.setLeechSlots(leechSlots);
                        env.put("leechslots", "" + groupToChange.getLeechSlots());
                        response.addComment(session.jprintf(_bundle, "changegroup.leechslots.success", env, request.getUser()));
                    } catch (NumberFormatException ex) {
                        return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
                    }
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

                    logger.info("'{}' changed created for group '{}' from '{}' to '{}'", currentUser.getName(), groupToChange.getName(), groupToChange.getCreated(), myDate);
                    groupToChange.setCreated(myDate);

                    response = new CommandResponse(200, session.jprintf(_bundle, "changegroup.created.success", env, request.getUser()));
                    break;
                default:
                    throw new ImproperUsageException();
            }

            groupToChange.commit();

        }

        return response;
    }

    /**
     * USAGE: site chgrp <user><group>[ <group>] Adds/removes a user from
     * group(s).
     * <p>
     * ex. site chgrp archimede ftp This would change the group to 'ftp' for the
     * user 'archimede'.
     * <p>
     * ex1. site chgrp archimede ftp This would remove the group ftp from the
     * user 'archimede'.
     * <p>
     * ex2. site chgrp archimede ftp eleet This moves archimede from ftp group
     * to eleet group.
     *
     * @throws ImproperUsageException
     */
    public CommandResponse doCHGRP(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split("[ ,]");

        if (args.length < 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        User myUser;

        try {
            myUser = GlobalContext.getGlobalContext().getUserManager().getUserByName(args[0]);
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

            Group groupToChange = session.getGroupNull(string);
            if (groupToChange == null) {
                response.addComment("Unknown group " + string);
            } else {
                try {
                    myUser.removeSecondaryGroup(session.getGroupNull(string));
                    logger.info("'{}' removed '{}' from group '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), string);
                    response.addComment(myUser.getName() + " removed from group " + string);
                } catch (NoSuchFieldException e1) {
                    try {
                        myUser.addSecondaryGroup(session.getGroupNull(string));
                        logger.info("'{}' added '{}' to group '{}'", session.getUserNull(request.getUser()).getName(), myUser.getName(), string);
                        response.addComment(myUser.getName() + " added to group " + string);
                    } catch (DuplicateElementException e2) {
                        throw new RuntimeException("Error, user was not a member before", e2);
                    }
                }
            }
        }
        myUser.commit();
        return response;
    }

    public CommandResponse doGINFO(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String group = request.getArgument();

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        boolean isGroupAdmin = GlobalContext.getGlobalContext().getUserManager().isGroupAdmin(currentUser);
        boolean isAdmin = currentUser.isAdmin();

        // Disallow this command if these scenario's are not met
        if (!isAdmin && !isGroupAdmin) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        // Get the group entity
        Group g = session.getGroupNull(group);

        // If the user is not an admin we check if he actually has rights
        if (!isAdmin) {
            // Group does not exist
            if (g == null) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
            // Group exists, but user is not an admin
            if (!g.isAdmin(currentUser)) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
        }

        // Group does not exist
        if (g == null) {
            throw new ImproperUsageException();
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        Map<String, Object> env = new HashMap<>();
        env.put("group", g.getName());
        env.put("sp", " ");

        // add header
        String head = _bundle.getString("ginfo.head");
        response.addComment(ReplacerUtils.jprintf(head, env));

        // vars for total stats
        int numUsers = 0;
        int numLeechUsers = 0;
        int allfup = 0;
        int allfdn = 0;
        long allmbup = 0;
        long allmbdn = 0;

        ArrayList<User> users = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(g));
        users.sort(GroupManagementHandler.USER_CASE_INSENSITIVE_COMPARATOR);

        for (User user : users) {

            char status = ' ';
            if (g.isAdmin(user)) {
                status = '+';
            } else if (user.isAdmin()) {
                status = '*';
            } else if (user.isDeleted()) {
                status = '!';
            }

            try {
                String body = _bundle.getString("ginfo.user");
                env.put("user", status + user.getName());
                env.put("fup", "" + user.getUploadedFiles());
                env.put("mbup", Bytes.formatBytes(user.getUploadedBytes()));
                env.put("fdn", "" + user.getDownloadedFiles());
                env.put("mbdn", Bytes.formatBytes(user.getDownloadedBytes()));
                env.put("ratio", "1:" + user.getKeyedMap().getObjectFloat(UserManagement.RATIO));
                env.put("wkly", Bytes.formatBytes(user.getKeyedMap().getObjectLong(UserManagement.WKLYALLOTMENT)));
                response.addComment(ReplacerUtils.jprintf(body, env));
            } catch (MissingResourceException e) {
                response.addComment(e.getMessage());
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
        env.put("allfup", "" + allfup);
        env.put("allmbup", Bytes.formatBytes(allmbup));
        env.put("allfdn", "" + allfdn);
        env.put("allmbdn", Bytes.formatBytes(allmbdn));
        env.put("slotstotal", g.getGroupSlots());
        env.put("slotsfree", g.getGroupSlots() - numUsers);
        env.put("leechtotal", g.getLeechSlots());
        env.put("leechfree", g.getLeechSlots() - numLeechUsers);
        env.put("minratio", g.getMinRatio());
        env.put("maxratio", g.getMaxRatio());
        env.put("groupratio", g.getMinRatio()+"/"+g.getMaxRatio());
        env.put("slots", g.getGroupSlots()+"/"+(g.getGroupSlots() - numUsers));
        env.put("leechslots", g.getGroupSlots()+"/"+(g.getLeechSlots() - numLeechUsers));
        env.put("created", new SimpleDateFormat("d MMM yyyy HH:mm:ss").format(g.getCreated()));

        String tail = _bundle.getString("ginfo.tail");
        try {
            response.addComment(ReplacerUtils.jprintf(tail, env));
        } catch (MissingResourceException e) {
            logger.warn("", e);
            response.addComment(e.getMessage());
        }

        return response;
    }

    public CommandResponse doGROUPS(CommandRequest request) {

        ArrayList<Group> groups = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllGroups());
        groups.sort(GroupManagementHandler.GROUP_CASE_INSENSITIVE_COMPARATOR);

        CommandResponse response = new CommandResponse(200);
        response.addComment("All groups:");

        for (Group element : groups) {
            response.addComment(element.getName());
        }

        return response;
    }

    public CommandResponse doGRPREN(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (st.countTokens() != 2) {
            throw new ImproperUsageException();
        }

        Group g = session.getGroupNull(st.nextToken());
        String oldGroup = g.getName();
        String newGroup = st.nextToken();
        Group g2 = session.getGroupNull(newGroup);

        Collection<User> users = GlobalContext.getGlobalContext().getUserManager().getAllUsersByGroup(g);

        if (g2 != null) {
            return new CommandResponse(500, newGroup + " already exists");
        }

        CommandResponse response = new CommandResponse(200);
        response.addComment("Renaming group " + g.getName() + " to " + newGroup);

        // First we loop through all the users are remove the old group
        List<User> usersToSet = new ArrayList<>();
        List<User> usersToAdd = new ArrayList<>();
        for (User userToChange : users) {
            if (userToChange.getGroup().getName().equals(oldGroup)) {
                usersToSet.add(userToChange);
            } else {
                usersToAdd.add(userToChange);
                try {
                    userToChange.removeSecondaryGroup(g);
                } catch (NoSuchFieldException e1) {
                    throw new RuntimeException("User was not in group returned by getAllUsersByGroup");
                }
            }
        }

        try {
            g.rename(newGroup);
        } catch (GroupFileException | GroupExistsException e) {
            logger.error("Unable to rename group from {} to {}", oldGroup, newGroup, e);
            return new CommandResponse(500, "Failed to rename group from " + oldGroup + " to " + newGroup);
        }

        g.commit();

        for (User u1 : usersToSet) {
            u1.setGroup(g);
        }
        for (User u2 : usersToAdd) {
            try {
                u2.addSecondaryGroup(g);
            } catch (DuplicateElementException ignored) {
            }
        }

        for (User userToChange : users) {
            userToChange.commit();
            response.addComment("Changed user " + userToChange.getName());
        }

        return response;
    }

    /**
     * USAGE: site changegroupadmin <group> <user>[ <user>] Adds/removes a group admin from group.
     * <p>
     * ex1. site changegroupadmin ftp archimede This would add/remove 'archimede' as a group admin for 'ftp' group.
     *
     * @throws ImproperUsageException If there is something wrong with this request
     */
    public CommandResponse doCHANGEGROUPADMIN(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (st.countTokens() < 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        Session session = request.getSession();

        User currentUser = session.getUserNull(request.getUser());

        String groupname = st.nextToken();

        Group requestedGroup = session.getGroupNull(groupname);

        if (requestedGroup == null) {
            return new CommandResponse(500, "Group " + groupname + " does not exist");
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        env.put("targetgroup", groupname);

        while (st.hasMoreTokens()) {
            String username = st.nextToken();
            User requestedUser = session.getUserNull(username);
            env.put("targetuser", requestedUser.getName());
            if (requestedUser == null) {
                response.addComment(session.jprintf(_bundle, "changegroupadmin.bad.user", env, request.getUser()));
            } else {
                try {
                    requestedGroup.removeAdmin(requestedUser);
                    logger.info("'{}' removed group admin '{}' from group '{}'", currentUser.getName(), requestedUser.getName(), groupname);
                    response.addComment(session.jprintf(_bundle, "changegroupadmin.remove.user", env, request.getUser()));
                } catch (NoSuchFieldException e1) {
                    try {
                        requestedGroup.addAdmin(requestedUser);
                        logger.info("'{}' added group admin '{}' to group '{}'", currentUser.getName(), requestedUser.getName(), groupname);
                        response.addComment(session.jprintf(_bundle, "changegroupadmin.add.user", env, request.getUser()));
                    } catch (DuplicateElementException e2) {
                        throw new RuntimeException("Error, user was not a group admin before", e2);
                    }
                }
            }
        }
        requestedGroup.commit();

        return response;
    }

    static class GroupCaseInsensitiveComparator implements Comparator<Group> {
        @Override
        public int compare(Group group0, Group group1) {
            return String.CASE_INSENSITIVE_ORDER.compare(group0.getName(), group1.getName());
        }
    }

    static class UserCaseInsensitiveComparator implements Comparator<User> {
        @Override
        public int compare(User user0, User user1) {
            return String.CASE_INSENSITIVE_ORDER.compare(user0.getName(), user1.getName());
        }
    }
}
