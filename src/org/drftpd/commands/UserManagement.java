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
package org.drftpd.commands;

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

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.Time;
import org.drftpd.dynamicdata.Key;
import org.drftpd.permissions.Permission;
import org.drftpd.plugins.Statistics;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.HostMask;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserExistsException;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class UserManagement implements CommandHandler, CommandHandlerFactory {
    private static final Logger logger = Logger.getLogger(UserManagement.class);

    public static final Key TAGLINE = new Key(UserManagement.class, "tagline", String.class);
    public static final Key DEBUG = new Key(UserManagement.class, "debug", Boolean.class);
    public static final Key RATIO = new Key(UserManagement.class, "ratio", Float.class);
    public static final Key CREATED = new Key(UserManagement.class, "created", Date.class);
    public static final Key COMMENT = new Key(UserManagement.class, "comment", String.class);
    public static final Key REASON = new Key(UserManagement.class, "reason", String.class);
    public static final Key IRCIDENT = new Key(UserManagement.class, "ircident", String.class);
    public static final Key GROUPSLOTS = new Key(UserManagement.class, "groupslots", Integer.class);
    public static final Key LEECHSLOTS = new Key(UserManagement.class, "leechslots", Integer.class);
    public static final Key MAXLOGINS = new Key(UserManagement.class, "maxlogins", Integer.class);
    public static final Key MAXLOGINSIP = new Key(UserManagement.class, "maxloginsip", Integer.class);
    public static final Key MINRATIO = new Key(UserManagement.class, "minratio", Float.class);
    public static final Key MAXRATIO = new Key(UserManagement.class, "maxratio", Float.class);
    public static final Key MAXSIMUP = new Key(UserManagement.class, "maxsimup", Integer.class);
    public static final Key MAXSIMDN = new Key(UserManagement.class, "maxsimdn", Integer.class);
    public static final Key LASTSEEN = new Key(UserManagement.class, "lastseen", Date.class);
    public static final Key WKLY_ALLOTMENT = new Key(UserManagement.class, "wkly_allotment", Long.class);
    public static final Key BAN_TIME = new Key(UserManagement.class, "ban_time", Date.class);
    public static final Key BAN_REASON = new Key(UserManagement.class, "ban_reason", String.class);

    private Reply doSITE_ADDIP(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length < 2) {
            return new Reply(501,
                conn.jprintf(UserManagement.class, "addip.specify"));
        }

        Reply response = new Reply(200);
        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(args[0]);

            if (conn.getUserNull().isGroupAdmin() &&
                    !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }

            ReplacerEnvironment env = new ReplacerEnvironment();
            env.add("targetuser", myUser.getName());

            for (int i = 1; i < args.length; i++) {
                String string = args[i];
                env.add("mask", string);

                try {
                    myUser.addIPMask(string);
                    response.addComment(conn.jprintf(UserManagement.class,
                            "addip.success", env));
                    logger.info("'" + conn.getUserNull().getName() +
                        "' added ip '" + string + "' to '" +
                        myUser.getName() + "'");
                } catch (DuplicateElementException e) {
                    response.addComment(conn.jprintf(UserManagement.class,
                            "addip.dupe", env));
                }
            }

            myUser.commit(); // throws UserFileException

            //userManager.save(user2);
        } catch (NoSuchUserException ex) {
            return new Reply(452, "No such user: " + args[0]);
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
     * @throws ImproperUsageException
     */
    private Reply doSITE_ADDUSER(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();
        boolean isGAdduser = request.getCommand().equals("SITE GADDUSER");

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String newGroup = null;

        if (conn.getUserNull().isGroupAdmin()) {
            if (isGAdduser) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }

            int users;

            try {
                users = conn.getGlobalContext().getUserManager()
                            .getAllUsersByGroup(conn.getUserNull().getGroup())
                            .size();
                logger.debug("Group " + conn.getUserNull().getGroup() +
                    " is " +
                    conn.getGlobalContext().getUserManager().getAllUsersByGroup(conn.getUserNull()
                                                                                    .getGroup()));

                if (users >= conn.getUserNull().getKeyedMap().getObjectInt(UserManagement.GROUPSLOTS)) {
                    return new Reply(452,
                        conn.jprintf(UserManagement.class, "adduser.noslots"));
                }
            } catch (UserFileException e1) {
                logger.warn("", e1);

                return new Reply(452, e1.getMessage());
            }

            newGroup = conn.getUserNull().getGroup();
        } else if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        User newUser;
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        ReplacerEnvironment env = new ReplacerEnvironment();

        try {
            if (isGAdduser) {
                newGroup = st.nextToken();
            }

            String newUsername = st.nextToken();
            env.add("targetuser", newUsername);
            String pass = st.nextToken();

            // Changed To Read In From File  :)
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

                if (ratio == null) { throw new ImproperUsageException("Unspecified value 'ratio' in " + confFile); }                   
                if (max_logins == null) { throw new ImproperUsageException("Unspecified value 'max_logins' in " + confFile); }
                if (max_logins_ip == null) { throw new ImproperUsageException("Unspecified value 'max_logins_ip' in " + confFile); }
                if (max_uploads == null) { throw new ImproperUsageException("Unspecified value 'max_uploads' in " + confFile); }
                if (max_downloads == null) { throw new ImproperUsageException("Unspecified value 'max_downloads' in " + confFile); }
                if (wkly_allotment == null) { throw new ImproperUsageException("Unspecified value 'wkly_allotment' in " + confFile); }
                if (credits == null) { throw new ImproperUsageException("Unspecified value 'credits' in " + confFile); }
                if (idle_time == null) { throw new ImproperUsageException("Unspecified value 'idle_time' in " + confFile); }
                if (tagline == null) { throw new ImproperUsageException("Unspecified value 'tagline' in " + confFile); }
                
            } catch (FileNotFoundException e) {
            	logger.error("Error reading " + confFile,e);
            	throw new RuntimeException(e.getMessage());
            } catch (IOException e) {
            	logger.error("Error reading " + confFile,e);
            	throw new RuntimeException(e.getMessage());
            } finally {
            	try {
            		if (file != null) {
            			file.close();
            		}
            	} catch (IOException e) {
            	}
            }

            float ratioVal = Float.parseFloat( ratio );
            int max_loginsVal = Integer.parseInt( max_logins );
            int max_logins_ipVal = Integer.parseInt( max_logins_ip );
            int max_uploadsVal = Integer.parseInt( max_uploads );
            int max_downloadsVal = Integer.parseInt( max_downloads );
            int idle_timeVal = Integer.parseInt( idle_time );
            long creditsVal = Bytes.parseBytes(credits);
            long wkly_allotmentVal = Bytes.parseBytes(wkly_allotment);


            //action, no more NoSuchElementException below here
            newUser = conn.getGlobalContext().getUserManager().create(newUsername);
            newUser.setPassword(pass);
            newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
            response.addComment(conn.jprintf(UserManagement.class,
                    "adduser.success", env));
            newUser.getKeyedMap().setObject(UserManagement.COMMENT, "Added by " + conn.getUserNull().getName());
            newUser.getKeyedMap().setObject(UserManagement.GROUPSLOTS,0);
            newUser.getKeyedMap().setObject(UserManagement.LEECHSLOTS,0);
            newUser.getKeyedMap().setObject(UserManagement.MINRATIO,3F);
            newUser.getKeyedMap().setObject(UserManagement.MAXRATIO,3F);
            newUser.getKeyedMap().setObject(UserManagement.CREATED, new Date());
            newUser.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
            newUser.getKeyedMap().setObject(UserManagement.IRCIDENT, "N/A");
            newUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
            newUser.getKeyedMap().setObject(Statistics.LOGINS,0);
            newUser.getKeyedMap().setObject(Nuke.NUKED,0);
            newUser.getKeyedMap().setObject(Nuke.NUKEDBYTES,new Long(0));
            newUser.getKeyedMap().setObject(UserManagement.TAGLINE,tagline);
            newUser.getKeyedMap().setObject(UserManagement.RATIO, ratioVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXLOGINS,max_loginsVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXLOGINSIP,max_logins_ipVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXSIMUP,max_uploadsVal);
            newUser.getKeyedMap().setObject(UserManagement.MAXSIMDN,max_downloadsVal);
            newUser.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT, wkly_allotmentVal);
            
            newUser.setIdleTime(idle_timeVal);
            newUser.setCredits(creditsVal);
            

            if (newGroup != null) {
                newUser.setGroup(newGroup);
                logger.info("'" + conn.getUserNull().getName() +
                    "' added '" + newUser.getName() + "' with group " +
                    newUser.getGroup() + "'");
                env.add("primgroup", newUser.getGroup());
                response.addComment(conn.jprintf(UserManagement.class,
                        "adduser.primgroup", env));
            } else {
                logger.info("'" + conn.getUserNull().getName() +
                    "' added '" + newUser.getName() + "'");
            }
        } catch (NoSuchElementException ex) {
            return new Reply(501,
                conn.jprintf(UserManagement.class, "adduser.missingpass"));
        } catch (UserFileException ex) {
            return new Reply(452, ex.getMessage());
        } catch (ImproperUsageException e) {
        	return new Reply(501, e.getMessage());
        } catch (NumberFormatException e) {
        	return new Reply(501,e.getMessage());
        }

        try {
            while (st.hasMoreTokens()) {
                String string = st.nextToken();
                env.add("mask", string);
                new HostMask(string); // validate hostmask

                try {
                    newUser.addIPMask(string);
                    response.addComment(conn.jprintf(UserManagement.class,
                            "addip.success", env));
                    logger.info("'" + conn.getUserNull().getName() +
                        "' added ip '" + string + "' to '" +
                        newUser.getName() + "'");
                } catch (DuplicateElementException e1) {
                    response.addComment(conn.jprintf(UserManagement.class,
                            "addip.dupe", env));
                }
            }

            newUser.commit();
        } catch (UserFileException ex) {
            logger.warn("", ex);

            return new Reply(452, ex.getMessage());
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
     * @throws ImproperUsageException
     */
    private Reply doSITE_CHANGE(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        User userToChange;
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        ReplacerEnvironment env = new ReplacerEnvironment();

        StringTokenizer arguments = new StringTokenizer(request.getArgument());

        if (!arguments.hasMoreTokens()) {
        	throw new ImproperUsageException();
        }

        String username = arguments.nextToken();

        try {
            userToChange = conn.getGlobalContext().getUserManager()
                               .getUserByName(username);
        } catch (NoSuchUserException e) {
            return new Reply(550,
                "User " + username + " not found: " + e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.ERROR, "Error loading user", e);

            return new Reply(550, "Error loading user: " + e.getMessage());
        }

        if (!arguments.hasMoreTokens()) {
        	throw new ImproperUsageException();
        }

        String command = arguments.nextToken().toLowerCase();

        if (conn.getUserNull().isGroupAdmin() && !command.equals("ratio")) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        env.add("targetuser", userToChange.getName());

        //		String args[] = request.getArgument().split(" ");
        //		String command = args[1].toLowerCase();
        // 0 = user
        // 1 = command
        // 2- = argument
        String[] commandArguments = new String[arguments.countTokens()];
        String fullCommandArgument = "";

        for (int x = 0; arguments.hasMoreTokens(); x++) {
            commandArguments[x] = arguments.nextToken();
            fullCommandArgument = fullCommandArgument + " " +
                commandArguments[x];
        }

        fullCommandArgument = fullCommandArgument.trim();

        if ("ratio".equals(command)) {
            ////// Ratio //////
            if (commandArguments.length != 1) {
            	throw new ImproperUsageException();
            }

            float ratio = Float.parseFloat(commandArguments[0]);

            if (conn.getUserNull().isGroupAdmin() &&
                    !conn.getUserNull().isAdmin()) {
                ////// Group Admin Ratio //////
                if (!conn.getUserNull().getGroup().equals(userToChange.getGroup())) {
                    return Reply.RESPONSE_530_ACCESS_DENIED;
                }

                if (ratio == 0F) {
                    int usedleechslots = 0;

                    try {
                        for (Iterator iter = conn.getGlobalContext()
                                                 .getUserManager()
                                                 .getAllUsersByGroup(conn.getUserNull()
                                                                         .getGroup())
                                                 .iterator(); iter.hasNext();) {
                            if (((User) iter.next()).getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0F) {
                                usedleechslots++;
                            }
                        }
                    } catch (UserFileException e1) {
                        return new Reply(452,
                            "IO error reading userfiles: " + e1.getMessage());
                    }

                    if (usedleechslots >= conn.getUserNull().getKeyedMap().getObjectInt(UserManagement.LEECHSLOTS)) {
                        return new Reply(452,
                            conn.jprintf(UserManagement.class,
                                "changeratio.nomoreslots"));
                    }
                } else if (ratio < conn.getUserNull().getMinRatio()
                		|| ratio > conn.getUserNull().getMaxRatio()) {
                	env.add("minratio", conn.getUserNull().getMinRatio());
                	env.add("maxratio", conn.getUserNull().getMaxRatio());
                    return new Reply(452,
                        conn.jprintf(UserManagement.class,
                            "changeratio.invalidratio", env));
                }

                logger.info("'" + conn.getUserNull().getName() +
                    "' changed ratio for '" + userToChange.getName() +
                    "' from '" +
                    userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO) +
                    "' to '" + ratio + "'");
                userToChange.getKeyedMap().setObject(UserManagement.RATIO, new Float(ratio));
                env.add("newratio",
                    Float.toString(userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
                response.addComment(conn.jprintf(UserManagement.class,
                        "changeratio.success", env));
            } else {
                // Ratio changes by an admin //
                logger.info("'" + conn.getUserNull().getName() +
                    "' changed ratio for '" + userToChange.getName() +
                    "' from '" +
                    userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO) + " to '" +
                    ratio + "'");
                userToChange.getKeyedMap().setObject(UserManagement.RATIO, new Float(ratio));
                env.add("newratio",
                    Float.toString(userToChange.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
                response.addComment(conn.jprintf(UserManagement.class,
                        "changeratio.success", env));
            }
        } else if ("credits".equals(command)) {
            if (commandArguments.length != 1) {
            	throw new ImproperUsageException();
            }

            long credits = Bytes.parseBytes(commandArguments[0]);
            logger.info("'" + conn.getUserNull().getName() +
                "' changed credits for '" + userToChange.getName() +
                "' from '" + userToChange.getCredits() + " to '" + credits +
                "'");
            userToChange.setCredits(credits);
            env.add("newcredits", Bytes.formatBytes(userToChange.getCredits()));
            response.addComment(conn.jprintf(UserManagement.class,
                    "changecredits.success", env));
        } else if ("comment".equals(command)) {
            logger.info("'" + conn.getUserNull().getName() +
                "' changed comment for '" + userToChange.getName() +
                "' from '" +
                userToChange.getKeyedMap().getObjectString(UserManagement.COMMENT) + " to '" +
                fullCommandArgument + "'");
            userToChange.getKeyedMap().setObject(UserManagement.COMMENT, fullCommandArgument);
            env.add("comment",
                userToChange.getKeyedMap().getObjectString(UserManagement.COMMENT));
            response.addComment(conn.jprintf(UserManagement.class,
                    "changecomment.success", env));
        } else if ("idle_time".equals(command)) {
            if (commandArguments.length != 1) {
            	throw new ImproperUsageException();
            }

            int idleTime = Integer.parseInt(commandArguments[0]);
            logger.info("'" + conn.getUserNull().getName() +
                "' changed idle_time for '" + userToChange.getName() +
                "' from '" + userToChange.getIdleTime() + " to '" + idleTime +
                "'");
            userToChange.setIdleTime(idleTime);
            env.add("idletime", ""+userToChange.getIdleTime());
            response.addComment(conn.jprintf(UserManagement.class,
                    "changeidletime.success", env));
        } else if ("num_logins".equals(command)) {
            // [# sim logins] [# sim logins/ip]
            try {
                int numLogins;
                int numLoginsIP;

                if ((commandArguments.length < 1) ||
                        (commandArguments.length > 2)) {
                    return Reply.RESPONSE_501_SYNTAX_ERROR;
                }

                numLogins = Integer.parseInt(commandArguments[0]);

                if (commandArguments.length == 2) {
                    numLoginsIP = Integer.parseInt(commandArguments[1]);
                } else {
                    numLoginsIP = userToChange.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP);
                }

                logger.info("'" + conn.getUserNull().getName() +
                    "' changed num_logins for '" + userToChange.getName() +
                    "' from '" + userToChange.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) + "' '" +
                    userToChange.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP) + "' to '" + numLogins +
                    "' '" + numLoginsIP + "'");
                userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINS,numLogins);
                userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINSIP,numLoginsIP);
                env.add("numlogins", "" + numLogins);
				env.add("numloginsip", "" + numLoginsIP);
                response.addComment(conn.jprintf(UserManagement.class,
                        "changenumlogins.success", env));
            } catch (NumberFormatException ex) {
                return Reply.RESPONSE_501_SYNTAX_ERROR;
            }

            //} else if ("max_dlspeed".equalsIgnoreCase(command)) {
            //	myUser.setMaxDownloadRate(Integer.parseInt(commandArgument));
            //} else if ("max_ulspeed".equals(command)) {
            //	myUser.setMaxUploadRate(Integer.parseInt(commandArgument));
        } else if ("group_ratio".equals(command)) {
        	// [# min] [# max]
        	if (commandArguments.length != 2) {
        		return Reply.RESPONSE_501_SYNTAX_ERROR;
        	}
        	
        	try { 
        		float minRatio = Float.parseFloat(commandArguments[0]);
        		float maxRatio = Float.parseFloat(commandArguments[1]);
        		
                env.add("minratio", "" + minRatio);
				env.add("maxratio", "" + maxRatio);

				logger.info("'" + conn.getUserNull().getName() +
                        "' changed gadmin min/max ratio for user '" + userToChange.getName() +
                        "' group '" + userToChange.getGroup() + "' from '" + userToChange.getMinRatio() + "/" + userToChange.getMaxRatio() + 
                        "' to '" + minRatio +  "/" + maxRatio + "'");
        	    
        	    if ( minRatio < 1 || maxRatio < minRatio)
        	    	return Reply.RESPONSE_501_SYNTAX_ERROR;

        	    userToChange.setMinRatio(minRatio);
        	    userToChange.setMaxRatio(maxRatio);
        	    
                response.addComment(conn.jprintf(UserManagement.class,
                        "changegadminratio.success", env));
                
        	} catch (NumberFormatException ex) {
        		return Reply.RESPONSE_501_SYNTAX_ERROR;
        	}
        } else if ("max_sim".equals(command)) {
        // [# DN] [# UP]
            
        	try { 
        		int maxup;
        	    int maxdn;
        	    
        	    if (commandArguments.length != 2) {
                    return Reply.RESPONSE_501_SYNTAX_ERROR;
                }
        	    
        	    maxdn = Integer.parseInt(commandArguments[0]);
        	    maxup = Integer.parseInt(commandArguments[1]);
        	    
        	    logger.info("'" + conn.getUserNull().getName() +
                        "' changed max simultaneous download/upload slots for '" + userToChange.getName() +
                        "' from '" + userToChange.getMaxSimDown() + "' '" + userToChange.getMaxSimUp() + 
                        "' to '" + maxdn +  "' '" + maxup + "'");
        	    
        	    userToChange.getKeyedMap().setObject(UserManagement.MAXSIMDN, maxdn);
        	    userToChange.getKeyedMap().setObject(UserManagement.MAXSIMUP, maxup);
        	    userToChange.setMaxSimUp(maxup);
        	    userToChange.setMaxSimDown(maxdn);
                env.add("maxdn", "" + maxdn);
				env.add("maxup", "" + maxup);
                response.addComment(conn.jprintf(UserManagement.class,
                        "changemaxsim.success", env));
                
        	} catch (NumberFormatException ex) {
        		return Reply.RESPONSE_501_SYNTAX_ERROR;
        	} 
        } else if ("group".equals(command)) {
            if (commandArguments.length != 1) {
            	throw new ImproperUsageException();
            }

            logger.info("'" + conn.getUserNull().getName() +
                "' changed primary group for '" + userToChange.getName() +
                "' from '" + userToChange.getGroup() + "' to '" +
                commandArguments[0] + "'");
            userToChange.setGroup(commandArguments[0]);
            env.add("primgroup", userToChange.getGroup());
            response.addComment(conn.jprintf(UserManagement.class,
                    "changeprimgroup.success", env));

            //			group_slots Number of users a GADMIN is allowed to add.
            //					If you specify a second argument, it will be the
            //					number of leech accounts the gadmin can give (done by
            //					"site change user ratio 0") (2nd arg = leech slots)
        } else if ("group_slots".equals(command)) {
            try {
                if ((commandArguments.length < 1) ||
                        (commandArguments.length > 2)) {
                    return Reply.RESPONSE_501_SYNTAX_ERROR;
                }

                int groupSlots = Short.parseShort(commandArguments[0]);
                int groupLeechSlots;

                if (commandArguments.length >= 2) {
                    groupLeechSlots = Integer.parseInt(commandArguments[1]);
                } else {
                    groupLeechSlots = userToChange.getKeyedMap().getObjectInt(UserManagement.LEECHSLOTS);
                }

                logger.info("'" + conn.getUserNull().getName() +
                    "' changed group_slots for '" + userToChange.getName() +
                    "' from '" + userToChange.getKeyedMap().getObjectInt(UserManagement.GROUPSLOTS) + "' " +
                    userToChange.getKeyedMap().getObjectInt(UserManagement.LEECHSLOTS) + "' to '" + groupSlots +
                    "' '" + groupLeechSlots + "'");
                userToChange.getKeyedMap().setObject(UserManagement.GROUPSLOTS, groupSlots);
                userToChange.getKeyedMap().setObject(UserManagement.LEECHSLOTS, groupLeechSlots);
                env.add("groupslots", "" + userToChange.getKeyedMap().getObjectInt(UserManagement.GROUPSLOTS));
                env.add("groupleechslots", ""
						+ userToChange.getKeyedMap().getObjectInt(
								UserManagement.LEECHSLOTS));
                response.addComment(conn.jprintf(UserManagement.class,
                        "changegroupslots.success", env));
            } catch (NumberFormatException ex) {
                return Reply.RESPONSE_501_SYNTAX_ERROR;
            }
        } else if ("created".equals(command)) {
            Date myDate;

            if (commandArguments.length == 0) {
                try {
                    myDate = new SimpleDateFormat("yyyy-MM-dd").parse(commandArguments[0]);
                } catch (ParseException e1) {
                    logger.log(Level.INFO, e1);

                    return new Reply(452, e1.getMessage());
                }
            } else {
                myDate = new Date();
            }

            logger.info("'" + conn.getUserNull().getName() +
                "' changed created for '" + userToChange.getName() +
                "' from '" +
                new Date(userToChange.getKeyedMap().getObjectLong(UserManagement.CREATED)) +
                "' to '" + myDate + "'");
            userToChange.getKeyedMap().setObject(UserManagement.CREATED, myDate);

            response = new Reply(200,
                    conn.jprintf(UserManagement.class, "changecreated.success",
                        env));
        } else if ("wkly_allotment".equals(command)) {
            if (commandArguments.length != 1) {
            	throw new ImproperUsageException();
            }

            long weeklyAllotment = Bytes.parseBytes(commandArguments[0]);
            logger.info("'" + conn.getUserNull().getName() +
                "' changed wkly_allotment for '" + userToChange.getName() +
                "' from '" + userToChange.getKeyedMap().getObjectLong(UserManagement.WKLY_ALLOTMENT) + "' to " +
                weeklyAllotment + "'");
            userToChange.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT,weeklyAllotment);

            response = Reply.RESPONSE_200_COMMAND_OK;
        } else if ("tagline".equals(command)) {
            if (commandArguments.length < 1) {
            	throw new ImproperUsageException();
            }

            logger.info("'" + conn.getUserNull().getName() +
                "' changed tagline for '" + userToChange.getName() +
                "' from '" + userToChange.getKeyedMap().getObject(TAGLINE, "") + "' to '" +
                fullCommandArgument + "'");
            userToChange.getKeyedMap().setObject(UserManagement.TAGLINE, fullCommandArgument);

            response = Reply.RESPONSE_200_COMMAND_OK;
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
     * @throws ImproperUsageException
     */
    private Reply doSITE_CHGRP(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split("[ ,]");

        if (args.length < 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(args[0]);
        } catch (NoSuchUserException e) {
            return new Reply(452, "User not found: " + e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error reading user", e);

            return new Reply(452, "IO error reading user: " +
                e.getMessage());
        }

        Reply response = new Reply(200);

        for (int i = 1; i < args.length; i++) {
            String string = args[i];

            try {
                myUser.removeSecondaryGroup(string);
                logger.info("'" + conn.getUserNull().getName() +
                    "' removed '" + myUser.getName() + "' from group '" +
                    string + "'");
                response.addComment(myUser.getName() +
                    " removed from group " + string);
            } catch (NoSuchFieldException e1) {
                try {
                    myUser.addSecondaryGroup(string);
                    logger.info("'" + conn.getUserNull().getName() +
                        "' added '" + myUser.getName() + "' to group '" +
                        string + "'");
                    response.addComment(myUser.getName() +
                        " added to group " + string);
                } catch (DuplicateElementException e2) {
                    throw new RuntimeException("Error, user was not a member before",
                        e2);
                }
            }
        }
        try {
        	myUser.commit();
        } catch(UserFileException e) {
        	throw new ReplyException(e);
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
     * enough" error.
     *  * Denotes any password, ex. site chpass arch * This will allow arch to
     * login with any password
     * @throws ImproperUsageException
     *  @ Denotes any email-like password, ex. site chpass arch @ This will
     * allow arch to login with a@b.com but not ab.com
     */
    private Reply doSITE_CHPASS(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        try {
            User myUser = conn.getGlobalContext().getUserManager()
                              .getUserByName(args[0]);
            myUser.setPassword(args[1]);
            myUser.commit();
            logger.info("'" + conn.getUserNull().getName() +
                "' changed password for '" + myUser.getName() + "'");

            return Reply.RESPONSE_200_COMMAND_OK;
        } catch (NoSuchUserException e) {
            return new Reply(452, "User not found: " + e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "Error reading userfile", e);

            return new Reply(452, "Error reading userfile: " +
                e.getMessage());
        }
    }

    /**
     * USAGE: site delip <user><ident@ip>...
     *
     * @param request
     * @param out
     * @throws ImproperUsageException
     */
    private Reply doSITE_DELIP(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length < 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(args[0]);
        } catch (NoSuchUserException e) {
            return new Reply(452, e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error", e);

            return new Reply(452, "IO error: " + e.getMessage());
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        Reply response = new Reply(200);

        for (int i = 1; i < args.length; i++) {
            String string = args[i];

            try {
                myUser.removeIpMask(string);
                logger.info("'" + conn.getUserNull().getName() +
                    "' removed ip '" + string + "' from '" + myUser + "'");
                response.addComment("Removed " + string);
            } catch (NoSuchFieldException e1) {
                response.addComment("Mask " + string + " not found: " +
                    e1.getMessage());

                continue;
            }
        }

        return response;
    }

    private Reply doSITE_DELUSER(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        String delUsername = st.nextToken();
        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(delUsername);
        } catch (NoSuchUserException e) {
            return new Reply(452, e.getMessage());
        } catch (UserFileException e) {
            return new Reply(452, "Couldn't getUser: " + e.getMessage());
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        myUser.setDeleted(true);
        String reason = "";
        if(st.hasMoreTokens()) {
        	myUser.getKeyedMap().setObject(UserManagement.REASON, reason = st.nextToken("").substring(1));
        }
        try {
			myUser.commit();
		} catch (UserFileException e1) {
			logger.error("", e1);
			throw new ReplyException(e1);
		}
        logger.info("'" + conn.getUserNull().getName() +
            "' deleted user '" + myUser.getName() + "' with reason '"+reason+"'");
        logger.debug("reason "+myUser.getKeyedMap().getObjectString(UserManagement.REASON));
        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_GINFO(BaseFtpConnection conn) throws ImproperUsageException {
		FtpRequest request = conn.getRequest();
		//security
		if (!conn.getUserNull().isAdmin() && !conn.getUserNull().isGroupAdmin()) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}
		//syntax
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		//gadmin
		String group = request.getArgument();
		
		if (conn.getUserNull().isGroupAdmin()
				&& !conn.getUserNull().getGroup().equals(group)) {
			return Reply.RESPONSE_530_ACCESS_DENIED;
		}

		Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();

		ResourceBundle bundle = ResourceBundle.getBundle(UserManagement.class.getName());
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("group", group);
		env.add("sp", " ");
		
		//add header
		String head = bundle.getString("ginfo.head");
		try {
			response.addComment(SimplePrintf.jprintf(head, env));
		} catch (MissingResourceException e) {
			logger.warn("",e);
			response.addComment(e.getMessage());
		} catch (FormatterException e) {
			logger.warn("",e);
			response.addComment(e.getMessage());
		}

		//vars for total stats
		int numUsers = 0;
		int numLeechUsers = 0;
		int allfup = 0;
		int allfdn = 0;
		long allmbup = 0;
		long allmbdn = 0;
		
		Collection users;		
		try {
			users = conn.getGlobalContext().getUserManager()
					.getAllUsers();
		} catch (UserFileException e) {
			return new Reply(452, "IO error: " + e.getMessage());
		}
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
				String body = bundle.getString("ginfo.user");
				env.add("user", status + user.getName());
				env.add("fup", "" + user.getUploadedFiles());
				env.add("mbup", Bytes.formatBytes(user.getUploadedBytes()));
				env.add("fdn", "" + user.getDownloadedFiles());
				env.add("mbdn", Bytes.formatBytes(user.getDownloadedBytes()));
				env.add("ratio", "1:" + (int) user.getKeyedMap().getObjectFloat(UserManagement.RATIO));	
				env.add("wkly", Bytes.formatBytes(user.getKeyedMap().getObjectLong(UserManagement.WKLY_ALLOTMENT)));
				response.addComment(SimplePrintf.jprintf(body, env));
			} catch (MissingResourceException e) {
				response.addComment(e.getMessage());
			} catch (FormatterException e1) {
				response.addComment(e1.getMessage());
			}
			
			//update totals
			numUsers++;
			if ((int) user.getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0) {
				numLeechUsers++;
			}
			allfup += user.getUploadedFiles();
			allfdn += user.getDownloadedFiles();
			allmbup += user.getUploadedBytes();
			allmbdn += user.getDownloadedBytes();
		}

		//add tail
		env.add("allfup", "" + allfup);
		env.add("allmbup", Bytes.formatBytes(allmbup));
		env.add("allfdn", "" + allfdn);
		env.add("allmbdn", Bytes.formatBytes(allmbdn));
		env.add("numusers", "" + numUsers);
		env.add("numleech", "" + numLeechUsers);
		
		String tail = bundle.getString("ginfo.tail");
		try {
			response.addComment(SimplePrintf.jprintf(tail, env));
		} catch (MissingResourceException e) {
			logger.warn("",e);
			response.addComment(e.getMessage());
		} catch (FormatterException e) {
			logger.warn("",e);
			response.addComment(e.getMessage());
		}

		return response;
	}

    private Reply doSITE_GIVE(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getGlobalContext().getConfig().checkPermission("give", conn.getUserNull())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(st.nextToken());
        } catch (Exception e) {
            logger.warn("", e);

            return new Reply(200, e.getMessage());
        }

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }
        long credits = 0;
        String amt = null;
        try {
        	amt = st.nextToken();
        	credits = Bytes.parseBytes(amt);
        } catch (NumberFormatException ex) {
        	return new Reply(452, "The string " + amt + " cannot be interpreted");
        }
        
        if (0 > credits) {
            return new Reply(452, credits + " is not a positive number.");
        }

        if (!conn.getUserNull().isAdmin()) {
            if (credits > conn.getUserNull().getCredits()) {
                return new Reply(452,
                    "You cannot give more credits than you have.");
            }

            conn.getUserNull().updateCredits(-credits);
        }

        logger.info("'" + conn.getUserNull().getName() + "' transfered " +
            Bytes.formatBytes(credits) + " ('" + credits + "') to '" +
            myUser.getName() + "'");
        myUser.updateCredits(credits);

        return new Reply(200,
            "OK, gave " + Bytes.formatBytes(credits) + " of your credits to " +
            myUser.getName());
    }
    private Reply doSITE_GROUP(BaseFtpConnection conn) throws ImproperUsageException {
    	FtpRequest request = conn.getRequest();
    	
    	boolean ip = false;
    	float ratio = 0; 
    	int numLogin = 0, numLoginIP = 0, maxUp = 0, maxDn = 0;
    	String opt, group;
    	
    	if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        
        if (!st.hasMoreTokens()) { return Reply.RESPONSE_501_SYNTAX_ERROR; }
        group = st.nextToken();
        
        if (!st.hasMoreTokens()) { return Reply.RESPONSE_501_SYNTAX_ERROR; }
        opt = st.nextToken();

        if (!st.hasMoreTokens()) { return Reply.RESPONSE_501_SYNTAX_ERROR; }
        
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
        	return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        // getting data
        
        Reply response = new Reply(200);
        
        Collection users = null;
        
        try {
        	users = conn.getGlobalContext().getUserManager().getAllUsersByGroup(group);
        } catch (UserFileException ex) {
            logger.log(Level.FATAL,
                    "IO error from getAllUsersByGroup(" + group + ")", ex);

                return new Reply(200, "IO error: " + ex.getMessage());
        }
        response.addComment("Changing '" + group + "' members " + opt);
        
        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User userToChange = (User) iter.next();
            
            if (userToChange.getGroup().equals(group)) {
            	if (opt.equals("num_logins")) { 
            		userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINS, numLogin);
            		if (ip) { userToChange.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, numLoginIP); }
            	}
            	if (opt.equals("max_sim")) {
            		userToChange.setMaxSimDown(maxDn);
            		userToChange.setMaxSimUp(maxUp);
            	}
            	if (opt.equals("ratio")) {
            		userToChange.getKeyedMap().setObject(UserManagement.RATIO, new Float(ratio));
            	}
            	response.addComment("Changed " + userToChange.getName() + "!");
            }
        }
        
        response.addComment("Done!");
        
    	return response;
    }
    
    private Reply doSITE_GROUPS(BaseFtpConnection conn) {
        Collection groups;

        try {
            groups = conn.getGlobalContext().getUserManager().getAllGroups();
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error from getAllGroups()", e);

            return new Reply(452, "IO error: " + e.getMessage());
        }

        Reply response = new Reply(200);
        response.addComment("All groups:");

        for (Iterator iter = groups.iterator(); iter.hasNext();) {
            String element = (String) iter.next();
            response.addComment(element);
        }

        return response;
    }

    private Reply doSITE_GRPREN(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
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
        Collection users = null;

        try {
            if (!conn.getGlobalContext().getUserManager()
                         .getAllUsersByGroup(newGroup).isEmpty()) {
                return new Reply(500, newGroup + " already exists");
            }

            users = conn.getGlobalContext().getUserManager().getAllUsersByGroup(oldGroup);
        } catch (UserFileException e) {
            logger.log(Level.FATAL,
                "IO error from getAllUsersByGroup(" + oldGroup + ")", e);

            return new Reply(200, "IO error: " + e.getMessage());
        }

        Reply response = new Reply(200);
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
                    throw new RuntimeException("group " + newGroup +
                        " already exists");
                }
            }

            response.addComment("Changed user " + userToChange.getName());
        }

        return response;
    }

    private Reply doSITE_KICK(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String arg = request.getArgument();
        int pos = arg.indexOf(' ');
        String username;
        String message = "Kicked by " + conn.getUserNull().getName();

        if (pos == -1) {
            username = arg;
        } else {
            username = arg.substring(0, pos);
            message = arg.substring(pos + 1);
        }

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(conn.getGlobalContext()
                                            .getConnectionManager()
                                            .getConnections());

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

    private Reply doSITE_PASSWD(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        logger.info("'" + conn.getUserNull().getName() +
            "' changed his password");
        conn.getUserNull().setPassword(request.getArgument());

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_PURGE(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String delUsername = request.getArgument();
        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager()
                         .getUserByNameUnchecked(delUsername);
        } catch (NoSuchUserException e) {
            return new Reply(452, e.getMessage());
        } catch (UserFileException e) {
            return new Reply(452, "Couldn't getUser: " + e.getMessage());
        }

        if (!myUser.isDeleted()) {
            return new Reply(452, "User isn't deleted");
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        myUser.purge();
        logger.info("'" + conn.getUserNull().getName() + "' purged '" +
            myUser.getName() + "'");

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_READD(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager()
                         .getUserByNameUnchecked(request.getArgument());
        } catch (NoSuchUserException e) {
            return new Reply(452, e.getMessage());
        } catch (UserFileException e) {
            return new Reply(452, "IO error: " + e.getMessage());
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!myUser.isDeleted()) {
            return new Reply(452, "User wasn't deleted");
        }

        myUser.setDeleted(false);
        myUser.getKeyedMap().remove(UserManagement.REASON);
        logger.info("'" + conn.getUserNull().getName() + "' readded '" +
            myUser.getName() + "'");
        try {
			myUser.commit();
		} catch (UserFileException e1) {
			logger.error(e1);
			throw new ReplyException(e1);
		}
        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_RENUSER(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        try {
            User myUser = conn.getGlobalContext().getUserManager()
                              .getUserByName(args[0]);
            String oldUsername = myUser.getName();
            myUser.rename(args[1]);
            logger.info("'" + conn.getUserNull().getName() + "' renamed '" +
                oldUsername + "' to '" + myUser.getName() + "'");
        } catch (NoSuchUserException e) {
            return new Reply(452, "No such user: " + e.getMessage());
        } catch (UserExistsException e) {
            return new Reply(452, "Target username is already taken");
        } catch (UserFileException e) {
            return new Reply(452, e.getMessage());
        }

        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_SEEN(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        User user;

        try {
            user = conn.getGlobalContext().getUserManager().getUserByName(request.getArgument());
        } catch (NoSuchUserException e) {
            return new Reply(452, e.getMessage());
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "", e);

            return new Reply(452, "Error reading userfile: " +
                e.getMessage());
        }

        return new Reply(200,
            "User was last seen: " + user.getKeyedMap().getObjectDate(UserManagement.LASTSEEN));
    }

    private Reply doSITE_TAGLINE(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        try {
            conn.getUserNull().getKeyedMap().setObject(UserManagement.TAGLINE, request.getArgument());
			conn.getUserNull().commit();
	        logger.info("'" + conn.getUserNull().getName()
					+ "' changed his tagline from '"
					+ conn.getUserNull().getKeyedMap().getObject(TAGLINE, "")
					+ "' to '" + request.getArgument() + "'");
		} catch (UserFileException e) {
			throw new ReplyException(e);
		}
        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_DEBUG(BaseFtpConnection conn) throws ReplyException {
    	User user = conn.getUserNull();
    	if(!conn.getRequest().hasArgument()) {
    		user.getKeyedMap().setObject(UserManagement.DEBUG, Boolean.valueOf(!user.getKeyedMap().getObjectBoolean(UserManagement.DEBUG)));
    	} else {
    		String arg = conn.getRequest().getArgument();
    		user.getKeyedMap().setObject(UserManagement.DEBUG, Boolean.valueOf(arg.equals("true") || arg.equals("on")));
    	}
    	try {
    		user.commit();
    	} catch(UserFileException e) {
    		throw new ReplyException(e);
    	}
    	return new Reply(200, conn.jprintf(UserManagement.class, "debug"));
    }

    /**
     * USAGE: site take <user><kbytes>[ <message>] Removes credit from user
     *
     * ex. site take Archimede 100000 haha
     *
     * This will remove 100mb of credits from the user 'Archimede' and send the
     * message haha to him.
     * @throws ImproperUsageException
     */
    private Reply doSITE_TAKE(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getGlobalContext().getConfig().checkPermission("take", conn.getUserNull())) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;
        long credits;
        String amt = null;
        
        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(st.nextToken());

            if (!st.hasMoreTokens()) {
                return Reply.RESPONSE_501_SYNTAX_ERROR;
            }
            amt = st.nextToken();
            credits = Bytes.parseBytes(amt); // B, not KiB

            if (0 > credits) {
                return new Reply(452, "Credits must be a positive number.");
            }

            logger.info("'" + conn.getUserNull().getName() + "' took " +
                Bytes.formatBytes(credits) + " ('" + credits + "') from '" +
                myUser.getName() + "'");
            myUser.updateCredits(-credits);
        } catch (NumberFormatException ex) {
        	return new Reply(452, "The string " + amt + " cannot be interpreted");
        } catch (Exception ex) {
        	logger.debug("",ex);
            return new Reply(452, ex.getMessage());
        }

        return new Reply(200,
            "OK, removed " + credits + "b from " + myUser.getName() + ".");
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
     * @throws ImproperUsageException
     */
    private Reply doSITE_USER(BaseFtpConnection conn) throws ReplyException, ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin() &&
                !conn.getUserNull().isGroupAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        User myUser;

        try {
            myUser = conn.getGlobalContext().getUserManager()
                         .getUserByNameUnchecked(request.getArgument());
        } catch (NoSuchUserException ex) {
            response.setMessage("User " + request.getArgument() + " not found");

            return response;

            //return FtpResponse.RESPONSE_200_COMMAND_OK);
        } catch (UserFileException ex) {
            throw new ReplyException(ex);
        }

        if (conn.getUserNull().isGroupAdmin() &&
                !conn.getUserNull().getGroup().equals(myUser.getGroup())) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        //int i = (int) (myUser.getTimeToday() / 1000);
        //int hours = i / 60;
        //int minutes = i - hours * 60;
        //response.addComment("time on today: " + hours + ":" + minutes);
//        ReplacerEnvironment env = new ReplacerEnvironment();

//        env.add("username", myUser.getName());
//        env.add("created", new Date(myUser.getObjectLong(UserManagement.CREATED)));
//        env.add("comment", myUser.getObjectString(UserManagement.COMMENT));
//        env.add("lastseen", new Date(myUser.getLastAccessTime()));
//        env.add("totallogins", Long.toString(myUser.getLogins()));
//        env.add("idletime", Long.toString(myUser.getIdleTime()));
//        env.add("userratio",
//            Float.toString(myUser.getObjectFloat(UserManagement.RATIO)));
//        env.add("usercredits", Bytes.formatBytes(myUser.getCredits()));
//        env.add("maxlogins", Long.toString(myUser.getMaxLogins()));
//        env.add("maxloginsip", Long.toString(myUser.getMaxLoginsPerIP()));
//        env.add("groupslots", Long.toString(myUser.getGroupSlots()));
//        env.add("groupleechslots", Long.toString(myUser.getGroupLeechSlots()));
//        env.add("useruploaded", Bytes.formatBytes(myUser.getUploadedBytes()));
//        env.add("userdownloaded", Bytes.formatBytes(myUser.getDownloadedBytes()));

        //ReplacerEnvironment env = BaseFtpConnection.getReplacerEnvironment(null, myUser);
        response.addComment(BaseFtpConnection.jprintf(UserManagement.class, "user", null, myUser));
        return response;
    }

    private Reply doSITE_USERS(BaseFtpConnection conn) throws ReplyException {
        FtpRequest request = conn.getRequest();

        if (!conn.getUserNull().isAdmin()) {
            return Reply.RESPONSE_530_ACCESS_DENIED;
        }

        Reply response = new Reply(200);
        Collection myUsers;

        try {
            myUsers = conn.getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error reading all users", e);

            throw new ReplyException(e);
        }

        if (request.hasArgument()) {
            Permission perm = new Permission(FtpConfig.makeUsers(
                        new StringTokenizer(request.getArgument())), true);

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
    private Reply doSITE_WHO(BaseFtpConnection conn) {
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        long users = 0;
        long speedup = 0;
        long speeddn = 0;
        long speed = 0;

        try {
            ReplacerFormat formatup = ReplacerUtils.finalFormat(UserManagement.class,
                    "who.up");
            ReplacerFormat formatdown = ReplacerUtils.finalFormat(UserManagement.class,
                    "who.down");
            ReplacerFormat formatidle = ReplacerUtils.finalFormat(UserManagement.class,
                    "who.idle");
            ReplacerFormat formatcommand = ReplacerUtils.finalFormat(UserManagement.class,
                    "who.command");
            ReplacerEnvironment env = new ReplacerEnvironment();
            ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(conn.getGlobalContext()
                                                .getConnectionManager()
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

                    if (conn.getGlobalContext().getConfig().checkPathPermission("hideinwho", user, conn2.getCurrentDirectory())) {
                        continue;
                    }

                    //StringBuffer status = new StringBuffer();
                    env.add("idle",
                        Time.formatTime(System.currentTimeMillis() -
                            conn2.getLastActive()));
                    env.add("targetuser", user.getName());
                    synchronized (conn2.getDataConnectionHandler()) {
						if (!conn2.isExecuting()) {
							response.addComment(SimplePrintf.jprintf(
									formatidle, env));
						} else if (conn2.getDataConnectionHandler()
								.isTransfering()) {
							try {
								speed = conn2.getDataConnectionHandler()
										.getTransfer().getXferSpeed();
							} catch (ObjectNotFoundException e) {
								logger.debug("This is a bug, please report it",
										e);
								speed = 0;
							}
							env.add("speed", Bytes.formatBytes(speed) + "/s");
							env.add("file", conn2.getDataConnectionHandler()
									.getTransferFile().getName());
							env.add("slave", conn2.getDataConnectionHandler()
									.getTranferSlave().getName());

							if (conn2.getDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
								response.addComment(SimplePrintf.jprintf(
										formatup, env));
								speedup += speed;
							} else if (conn2.getDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
								response.addComment(SimplePrintf.jprintf(
										formatdown, env));
								speeddn += speed;
							}
						} else {
							env.add("command", conn2.getRequest().getCommand());
							response.addComment(SimplePrintf.jprintf(
									formatcommand, env));
						}
					}
                }
            }

            env.add("currentusers", "" + users);
            env.add("maxusers", ""
					+ conn.getGlobalContext().getConfig().getMaxUsersTotal());
            env.add("totalupspeed", Bytes.formatBytes(speedup) + "/s");
            env.add("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
            response.addComment("");
            response.addComment(conn.jprintf(UserManagement.class,
                    "who.statusspeed", env));
            response.addComment(conn.jprintf(UserManagement.class,
                    "who.statususers", env));

            return response;
        } catch (FormatterException e) {
            return new Reply(452, e.getMessage());
        }
    }

    private Reply doSITE_SWHO(BaseFtpConnection conn) {
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        long users = 0;
        long speedup = 0;
        long speeddn = 0;
        long speed = 0;

        try {
            ReplacerFormat formatup = ReplacerUtils.finalFormat(UserManagement.class,
                    "swho.up");
            ReplacerFormat formatdown = ReplacerUtils.finalFormat(UserManagement.class,
                    "swho.down");
            ReplacerFormat formatidle = ReplacerUtils.finalFormat(UserManagement.class,
                    "swho.idle");
            ReplacerFormat formatcommand = ReplacerUtils.finalFormat(UserManagement.class,
                    "swho.command");
            ReplacerEnvironment env = new ReplacerEnvironment();
            ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(conn.getGlobalContext()
                                                .getConnectionManager()
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

                    //if (conn.getGlobalContext().getConfig().checkPathPermission("hideinwho", user, conn2.getCurrentDirectory())) {
                    //    continue;
                    //}

                    //StringBuffer status = new StringBuffer();
                    env.add("idle",
                        Time.formatTime(System.currentTimeMillis() -
                            conn2.getLastActive()));
                    env.add("targetuser", user.getName());
                    env.add("ip", conn2.getClientAddress().getHostAddress());
                    
                    
                    synchronized (conn2.getDataConnectionHandler()) {
						if (!conn2.isExecuting()) {
							response.addComment(SimplePrintf.jprintf(
									formatidle, env));
						} else if (conn2.getDataConnectionHandler()
								.isTransfering()) {
							try {
								speed = conn2.getDataConnectionHandler()
										.getTransfer().getXferSpeed();
							} catch (ObjectNotFoundException e) {
								logger.debug("This is a bug, please report it",
										e);
							}
							env.add("speed", Bytes.formatBytes(speed) + "/s");
							env.add("file", conn2.getDataConnectionHandler()
									.getTransferFile().getName());
							env.add("slave", conn2.getDataConnectionHandler()
									.getTranferSlave().getName());
							
							if (conn2.getTransferDirection() == Transfer.TRANSFER_RECEIVING_UPLOAD) {
								response.addComment(SimplePrintf.jprintf(
										formatup, env));
								speedup += speed;
							} else if (conn2.getTransferDirection() == Transfer.TRANSFER_SENDING_DOWNLOAD) {
								response.addComment(SimplePrintf.jprintf(
										formatdown, env));
								speeddn += speed;
							}
						} else {
							env.add("command", conn2.getRequest().getCommand());
							response.addComment(SimplePrintf.jprintf(
									formatcommand, env));
						}
					}
                }
            }

            env.add("currentusers", "" + users);
            env.add("maxusers", ""
					+ conn.getGlobalContext().getConfig().getMaxUsersTotal());
            env.add("totalupspeed", Bytes.formatBytes(speedup) + "/s");
            env.add("totaldnspeed", Bytes.formatBytes(speeddn) + "/s");
            response.addComment("");
            response.addComment(conn.jprintf(UserManagement.class,
                    "swho.statusspeed", env));
            response.addComment(conn.jprintf(UserManagement.class,
                    "swho.statususers", env));

            return response;
        } catch (FormatterException e) {
            return new Reply(452, e.getMessage());
        }
    }    
    
    private Reply doSITE_BAN(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;
        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(st.nextToken());
        } catch (Exception e) {
            logger.warn("", e);
            return new Reply(200, e.getMessage());
        }

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }
        
        long banTime;
        try {
            banTime = Long.parseLong(st.nextToken());
        } catch (NumberFormatException e) {
            logger.warn("", e);
            return new Reply(200, e.getMessage());
        }
        
        String banMsg;
        if (st.hasMoreTokens()) {
            banMsg = "[" + conn.getUserNull().getName() + "]";
            while (st.hasMoreTokens())
                banMsg += " " + st.nextToken();
        } else {
            banMsg = "Banned by " + conn.getUserNull().getName() + " for " + banTime + "m";
        }
        
        myUser.getKeyedMap().setObject(UserManagement.BAN_TIME, 
                					new Date(System.currentTimeMillis() + (banTime*60000)));
        myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, banMsg);
        try {
            myUser.commit();
        } catch (UserFileException e) {
            logger.warn("", e);
            return new Reply(200, e.getMessage());
        }
        
        return Reply.RESPONSE_200_COMMAND_OK;
    }
    
    private Reply doSITE_UNBAN(BaseFtpConnection conn) throws ImproperUsageException {
        FtpRequest request = conn.getRequest();

        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());

        if (!st.hasMoreTokens()) {
            return Reply.RESPONSE_501_SYNTAX_ERROR;
        }

        User myUser;
        try {
            myUser = conn.getGlobalContext().getUserManager().getUserByName(st.nextToken());
        } catch (Exception e) {
            logger.warn("", e);
            return new Reply(200, e.getMessage());
        }

        myUser.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
        myUser.getKeyedMap().setObject(UserManagement.BAN_REASON, "");
        
        try {
            myUser.commit();
        } catch (UserFileException e) {
            logger.warn("", e);
            return new Reply(200, e.getMessage());
        }
        
        return Reply.RESPONSE_200_COMMAND_OK;
    }

    private Reply doSITE_BANS(BaseFtpConnection conn) throws ReplyException {
        Collection<User> myUsers;
        try {
            myUsers = conn.getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            logger.log(Level.FATAL, "IO error reading all users", e);
            throw new ReplyException(e);
        }
        
        Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
        for (User user : myUsers) {
            long timeleft = user.getKeyedMap().getObjectDate(UserManagement.BAN_TIME).getTime() -
            	System.currentTimeMillis();
            if (timeleft > 0) {
                ReplacerEnvironment env = new ReplacerEnvironment();
                env.add("timeleft",""+(timeleft/60000));
                response.addComment(BaseFtpConnection.jprintf(UserManagement.class, "bans", env, user));
            }
        }
        
        return response;
    }
    
    public Reply execute(BaseFtpConnection conn)
        throws ReplyException, ImproperUsageException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE ADDIP".equals(cmd)) {
            return doSITE_ADDIP(conn);
        }

        if ("SITE CHANGE".equals(cmd)) {
            return doSITE_CHANGE(conn);
        }

        if ("SITE CHGRP".equals(cmd)) {
            return doSITE_CHGRP(conn);
        }

        if ("SITE CHPASS".equals(cmd)) {
            return doSITE_CHPASS(conn);
        }

        if("SITE DEBUG".equals(cmd))
        	return doSITE_DEBUG(conn);

        if ("SITE DELIP".equals(cmd)) {
            return doSITE_DELIP(conn);
        }

        if ("SITE DELUSER".equals(cmd)) {
            return doSITE_DELUSER(conn);
        }

        if ("SITE ADDUSER".equals(cmd) || "SITE GADDUSER".equals(cmd)) {
            return doSITE_ADDUSER(conn);
        }

        if ("SITE GINFO".equals(cmd)) {
            return doSITE_GINFO(conn);
        }

        if ("SITE GIVE".equals(cmd)) {
            return doSITE_GIVE(conn);
        }
        
        if ("SITE GROUP".equals(cmd)) {
            return doSITE_GROUP(conn);
        }

        if ("SITE GROUPS".equals(cmd)) {
            return doSITE_GROUPS(conn);
        }

        if ("SITE GRPREN".equals(cmd)) {
            return doSITE_GRPREN(conn);
        }

        if ("SITE KICK".equals(cmd)) {
            return doSITE_KICK(conn);
        }

        if ("SITE PASSWD".equals(cmd)) {
            return doSITE_PASSWD(conn);
        }

        if ("SITE PURGE".equals(cmd)) {
            return doSITE_PURGE(conn);
        }

        if ("SITE READD".equals(cmd)) {
            return doSITE_READD(conn);
        }

        if ("SITE RENUSER".equals(cmd)) {
            return doSITE_RENUSER(conn);
        }

        if ("SITE SEEN".equals(cmd)) {
            return doSITE_SEEN(conn);
        }

        if ("SITE TAGLINE".equals(cmd)) {
            return doSITE_TAGLINE(conn);
        }

        if ("SITE TAKE".equals(cmd)) {
            return doSITE_TAKE(conn);
        }

        if ("SITE USER".equals(cmd)) {
            return doSITE_USER(conn);
        }

        if ("SITE USERS".equals(cmd)) {
            return doSITE_USERS(conn);
        }

        if ("SITE WHO".equals(cmd)) {
            return doSITE_WHO(conn);
        }
 
        if ("SITE SWHO".equals(cmd)) {
            return doSITE_SWHO(conn);
        }

        if ("SITE BAN".equals(cmd)) {
            return doSITE_BAN(conn);
        }

        if ("SITE UNBAN".equals(cmd)) {
            return doSITE_UNBAN(conn);
        }

        if ("SITE BANS".equals(cmd)) {
            return doSITE_BANS(conn);
        }

        throw UnhandledCommandException.create(UserManagement.class,
            conn.getRequest());
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
