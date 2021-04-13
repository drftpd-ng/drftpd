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
package org.drftpd.master.commands.transferstatistics;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.network.Session;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.usermanager.UserManager;
import org.drftpd.master.usermanager.util.UserComparator;
import org.drftpd.master.usermanager.util.UserTransferStats;

import java.io.IOException;
import java.util.*;


/**
 * @version $Id$
 */
public class TransferStatistics extends CommandInterface {
    public static final int PERIOD_ALL = 0;
    public static final int PERIOD_MONTHLY = 1;
    public static final int PERIOD_WEEKLY = 2;
    public static final int PERIOD_DAILY = 3;

    private static final Logger logger = LogManager.getLogger(TransferStatistics.class);

    private ResourceBundle _bundle;

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

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    /**
     * USAGE: site stats [<user>]
     * Display a user's upload/download statistics.
     */
    public CommandResponse doSTATS(CommandRequest request) {

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

        Map<String, Object> env = new HashMap<>();

        env.put("created", user.getKeyedMap().getObject(UserManagement.CREATED, new Date(0L)));

        env.put("aluprank", UserTransferStats.getStatsPlace("ALUP", user, userman));
        env.put("aldnrank", UserTransferStats.getStatsPlace("ALDN", user, userman));
        env.put("mnuprank", UserTransferStats.getStatsPlace("MONTHUP", user, userman));
        env.put("mndnrank", UserTransferStats.getStatsPlace("MONTHDN", user, userman));
        env.put("wkuprank", UserTransferStats.getStatsPlace("WKUP", user, userman));
        env.put("wkdnrank", UserTransferStats.getStatsPlace("WKDN", user, userman));
        env.put("dayuprank", UserTransferStats.getStatsPlace("DAYUP", user, userman));
        env.put("daydnrank", UserTransferStats.getStatsPlace("DAYDN", user, userman));

        env.put("alupfiles", user.getUploadedFiles());
        env.put("alupbytes", Bytes.formatBytes(user.getUploadedBytes()));
        env.put("aldnfiles", user.getDownloadedFiles());
        env.put("aldnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
        env.put("mnupfiles", user.getUploadedFilesMonth());
        env.put("mnupbytes", Bytes.formatBytes(user.getUploadedBytesMonth()));
        env.put("mndnfiles", user.getDownloadedFilesMonth());
        env.put("mndnbytes", Bytes.formatBytes(user.getDownloadedBytesMonth()));
        env.put("wkupfiles", user.getUploadedFilesWeek());
        env.put("wkupbytes", Bytes.formatBytes(user.getUploadedBytesWeek()));
        env.put("wkdnfiles", user.getDownloadedFilesWeek());
        env.put("wkdnbytes", Bytes.formatBytes(user.getDownloadedBytesWeek()));
        env.put("dayupfiles", user.getUploadedFilesDay());
        env.put("dayupbytes", Bytes.formatBytes(user.getUploadedBytesDay()));
        env.put("daydnfiles", user.getDownloadedFilesDay());
        env.put("daydnbytes", Bytes.formatBytes(user.getDownloadedBytesDay()));

        response.addComment(session.jprintf(_bundle, "stats", env, request.getUser()));

        return response;
    }

    public CommandResponse doALUP(CommandRequest request) {
        return execute(request, "alup");
    }

    public CommandResponse doALDN(CommandRequest request) {
        return execute(request, "aldn");
    }

    public CommandResponse doMONTHUP(CommandRequest request) {
        return execute(request, "monthup");
    }

    public CommandResponse doMONTHDN(CommandRequest request) {
        return execute(request, "monthdn");
    }

    public CommandResponse doWKUP(CommandRequest request) {
        return execute(request, "wkup");
    }

    public CommandResponse doWKDN(CommandRequest request) {
        return execute(request, "wkdn");
    }

    public CommandResponse doDAYUP(CommandRequest request) {
        return execute(request, "dayup");
    }

    public CommandResponse doDAYDN(CommandRequest request) {
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
        Map<String, Object> env = new HashMap<>();

        String headerBundleKey = type + ".header";
        String headerText = request.getSession().jprintf(_bundle, headerBundleKey, env,
                request.getUser());
        if (headerText.equals(headerBundleKey)) {
            try {
                response.addComment(ConfigLoader.loadTextFile(type + "_header.txt"));
            } catch (IOException ioe) {
                logger.warn("Error reading {}_header.txt", type, ioe);
            }
        } else {
            response.addComment(headerText);
        }

        int i = 0;

        for (User user : users2) {
            if (++i > count) {
                break;
            }

            env.put("pos", "" + i);

            env.put("upbytesday", Bytes.formatBytes(user.getUploadedBytesDay()));
            env.put("upfilesday", "" + user.getUploadedFilesDay());
            env.put("uprateday", getUpRate(user, PERIOD_DAILY));
            env.put("upbytesweek",
                    Bytes.formatBytes(user.getUploadedBytesWeek()));
            env.put("upfilesweek", "" + user.getUploadedFilesWeek());
            env.put("uprateweek", getUpRate(user, PERIOD_WEEKLY));
            env.put("upbytesmonth",
                    Bytes.formatBytes(user.getUploadedBytesMonth()));
            env.put("upfilesmonth", "" + user.getUploadedFilesMonth());
            env.put("upratemonth", getUpRate(user, PERIOD_MONTHLY));
            env.put("upbytes", Bytes.formatBytes(user.getUploadedBytes()));
            env.put("upfiles", "" + user.getUploadedFiles());
            env.put("uprate", getUpRate(user, PERIOD_ALL));

            env.put("dnbytesday",
                    Bytes.formatBytes(user.getDownloadedBytesDay()));
            env.put("dnfilesday", "" + user.getDownloadedFilesDay());
            env.put("dnrateday", getDownRate(user, PERIOD_DAILY));
            env.put("dnbytesweek",
                    Bytes.formatBytes(user.getDownloadedBytesWeek()));
            env.put("dnfilesweek", "" + user.getDownloadedFilesWeek());
            env.put("dnrateweek", getDownRate(user, PERIOD_WEEKLY));
            env.put("dnbytesmonth",
                    Bytes.formatBytes(user.getDownloadedBytesMonth()));
            env.put("dnfilesmonth", "" + user.getDownloadedFilesMonth());
            env.put("dnratemonth", getDownRate(user, PERIOD_MONTHLY));
            env.put("dnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
            env.put("dnfiles", "" + user.getDownloadedFiles());
            env.put("dnrate", getDownRate(user, PERIOD_ALL));

            response.addComment(request.getSession().jprintf(_bundle, type, env, user.getName()));
        }

        String footerBundleKey = type + ".footer";
        String footerText = request.getSession().jprintf(_bundle, footerBundleKey, env,
                request.getUser());
        if (footerText.equals(footerBundleKey)) {
            try {
                response.addComment(ConfigLoader.loadTextFile(type + "_footer.txt"));
            } catch (IOException ioe) {
                logger.warn("Error reading {}_footer.txt", type, ioe);
            }
        } else {
            response.addComment(footerText);
        }

        return response;
    }
}
