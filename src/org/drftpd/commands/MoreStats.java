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
package org.drftpd.commands;

import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.plugins.Textoutput;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.RaceStatistics;

import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.tanesha.replacer.ReplacerEnvironment;

import java.io.IOException;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;


/*
 * @author iamn
 * @version $Id$
 */
public class MoreStats implements CommandHandler, CommandHandlerFactory {
    public static final int PERIOD_DAILY = Calendar.DAY_OF_MONTH; // = 5
    public static final short PERIOD_MONTHLY = Calendar.MONTH; // = 2
    public static final short PERIOD_WEEKLY = Calendar.WEEK_OF_YEAR; // = 3
    private static final Logger logger = Logger.getLogger(MoreStats.class);

    public void unload() {
    }

    public void load(CommandManagerFactory initializer) {
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public SlaveStatus getStatusAvailable(RemoteSlave rslave)
        throws SlaveUnavailableException {
        return rslave.getSlaveStatusAvailable();
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE TRAFFIC".equals(cmd)) {
            return doSITE_TRAFFIC(conn);
        }

        return doSITE_GROUPSTATS(conn);

        /* throw UnhandledCommandException.create(MoreStats.class,
             conn.getRequest());*/
    }

    public static int getPeriod(String strperiod) {
        int period = 0;

        if (strperiod.equals("AL")) {
            period = 0;
        }

        if (strperiod.equals("DAY")) {
            period = PERIOD_DAILY;
        }

        if (strperiod.equals("WK")) {
            period = PERIOD_WEEKLY;
        }

        if (strperiod.equals("MONTH")) {
            period = PERIOD_MONTHLY;
        }

        return period;
    }

    public static long getStats(String command, User user) {
        // AL MONTH WK DAY
        int period = getPeriod(command.substring(0, command.length() - 2));

        // UP DN
        String updn = command.substring(command.length() - 2).toUpperCase();

        if (updn.equals("UP")) {
            return user.getUploadedBytesForTrialPeriod(period);
        } else if (updn.equals("DN")) {
            return user.getDownloadedBytesForTrialPeriod(period);
        }

        throw new RuntimeException(UnhandledCommandException.create(
                MoreStats.class, command));
    }

    public static int getFiles(String command, User user) {
        // AL MONTH WK DAY
        int period = getPeriod(command.substring(0, command.length() - 2));

        // UP DN
        String updn = command.substring(command.length() - 2);

        if (updn.equals("UP")) {
            return user.getUploadedFilesForTrialPeriod(period);
        } else if (updn.equals("DN")) {
            return user.getDownloadedFilesForTrialPeriod(period);
        }

        throw new RuntimeException(UnhandledCommandException.create(
                MoreStats.class, command));
    }

    public static long getTime(String command, User user) {
        // AL MONTH WK DAY
        int period = getPeriod(command.substring(0, command.length() - 2));

        // UP DN
        String updn = command.substring(command.length() - 2);

        if (updn.equals("UP")) {
            return user.getUploadedTimeForTrialPeriod(period);
        } else if (updn.equals("DN")) {
            return user.getDownloadedTimeForTrialPeriod(period);
        }

        throw new RuntimeException(UnhandledCommandException.create(
                MoreStats.class, command));
    }

    private Reply doSITE_GROUPSTATS(BaseFtpConnection conn) {
        //if (!conn.getConfig().checkPermission("groupstats",conn.getUserNull())) {
        FtpRequest request = conn.getRequest();
        Reply response = new Reply(200, "OK");
        final String command = request.getCommand();
        String type = command.substring("SITE G".length()).toUpperCase();

        ArrayList grpList = new ArrayList();
        Collection users;

        try {
            users = conn.getGlobalContext().getConnectionManager()
                        .getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            logger.warn("", e);

            return new Reply(200, "IO error: " + e.getMessage());
        }

        MyGroupPosition stat = null;
        String groupname = "";

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User user = (User) iter.next();
            groupname = user.getGroup();

            for (Iterator iter2 = grpList.iterator(); iter2.hasNext();) {
                MyGroupPosition stat2 = (MyGroupPosition) iter2.next();

                if (stat2.getGroupname().equals(groupname)) {
                    stat = stat2;

                    break;
                }
            }

            if (stat == null) {
                stat = new MyGroupPosition(groupname, 0, 0, 0, 0, 0);
                grpList.add(stat);
            }

            stat.updateBytes(getStats(type, user));
            stat.updateFiles(getFiles(type, user));
            stat.updateXfertime(getTime(type, user));
            stat.updateRacesWon(user.getKeyedMap().getObjectInt(RaceStatistics.RACESWON));
            stat.updateMembers(1);
            stat = null;
        }

        Collections.sort(grpList);

        ReplacerEnvironment env = new ReplacerEnvironment();
        int count = 10;

        //morestats.grpstats=| ${grp,-15} |${grpname,7} |${files,8} | ${megs,9} | ${members,9} |
        try {
            Textoutput.addTextToResponse(response,
                "g" + type.toLowerCase() + "_header");
        } catch (IOException ioe) {
            logger.warn("Error reading " + "g" + type.toLowerCase() +
                "_header");
        }

        int i = 0;

        for (Iterator iter = grpList.iterator(); iter.hasNext();) {
            if (++i > count) {
                break;
            }

            MyGroupPosition grp = (MyGroupPosition) iter.next();
            env.add("none", "");
            env.add("pos", "" + i);
            env.add("grp", grp.getGroupname());
            env.add("files", Integer.toString(grp.getFiles()));
            env.add("megs", Bytes.formatBytes(grp.getBytes()));

            double avrage = grp.getXfertime();
            double s = avrage / (double) 1000.0;

            if (s <= 0) {
                avrage = 0;
            } else {
                avrage = grp.getBytes() / s;
            }

            env.add("average", Bytes.formatBytes((long) avrage));
            env.add("raceswon", Integer.toString(grp.getRacesWon()));

            env.add("members", Integer.toString(grp.getMembers()));
            response.addComment(ReplacerUtils.jprintf("morestats.grpstats",
                    env, MoreStats.class));
        }

        try {
            Textoutput.addTextToResponse(response,
                "g" + type.toLowerCase() + "_footer");
        } catch (IOException ioe) {
            logger.warn("Error reading " + "g" + type.toLowerCase() +
                "_footer");
        }

        return response;
    }

    public static String getUpRate(User user, int period) {
        double s = user.getUploadedTimeForTrialPeriod(period) / (double) 1000.0;

        if (s <= 0) {
            return "- k/s";
        }

        double rate = user.getUploadedBytesForTrialPeriod(period) / s;

        return Bytes.formatBytes((long) rate) + "/s";
    }

    public static String getDownRate(User user, int period) {
        double s = user.getDownloadedTimeForTrialPeriod(period) / (double) 1000.0;

        if (s <= 0) {
            return "- k/s";
        }

        double rate = user.getDownloadedBytesForTrialPeriod(period) / s;

        return Bytes.formatBytes((long) rate) + "/s";
    }

    private void AddTrafficComment(String type, double avrage, long megs,
        int files, Reply response) {
        double s = avrage / (double) 1000.0;

        if (s <= 0) {
            avrage = 0;
        } else {
            avrage = megs / s;
        }

        ReplacerEnvironment env = new ReplacerEnvironment();
        env.add("stat", type);
        env.add("average", Bytes.formatBytes((long) avrage));
        env.add("megs", Bytes.formatBytes(megs));
        env.add("files", Integer.toString(files));
        response.addComment(ReplacerUtils.jprintf("morestats.trafficstats",
                env, MoreStats.class));
    }

    private Reply doSITE_TRAFFIC(BaseFtpConnection conn) {
        Reply response = new Reply(200, "OK");

        Collection users;

        try {
            users = conn.getGlobalContext().getConnectionManager()
                        .getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            logger.warn("", e);

            return new Reply(200, "IO error: " + e.getMessage());
        }

        double MonthUpAvrage = 0;
        double WeekUpAvrage = 0;
        double DayUpAvrage = 0;
        double TotalUpAvrage = 0.0;

        double MonthDnAvrage = 0;
        double WeekDnAvrage = 0;
        double DayDnAvrage = 0;
        double TotalDnAvrage = 0;

        int MonthFilesUp = 0;
        int WeekFilesUp = 0;
        int DayFilesUp = 0;
        int TotalFilesUp = 0;

        int MonthFilesDn = 0;
        int WeekFilesDn = 0;
        int DayFilesDn = 0;
        int TotalFilesDn = 0;

        long MonthUp = 0;
        long WeekUp = 0;
        long DayUp = 0;
        long TotalUp = 0;

        long MonthDn = 0;
        long WeekDn = 0;
        long DayDn = 0;
        long TotalDn = 0;

        for (Iterator iter = users.iterator(); iter.hasNext();) {
            User user = (User) iter.next();

            MonthFilesUp += user.getUploadedFilesMonth();
            WeekFilesUp += user.getUploadedFilesWeek();
            DayFilesUp += user.getUploadedFilesDay();
            TotalFilesUp += user.getUploadedFiles();

            MonthFilesDn += user.getDownloadedFilesMonth();
            WeekFilesDn += user.getDownloadedFilesWeek();
            DayFilesDn += user.getDownloadedFilesDay();
            TotalFilesDn += user.getDownloadedFiles();

            DayUp += user.getUploadedBytesDay();
            WeekUp += user.getUploadedBytesWeek();
            MonthUp += user.getUploadedBytesMonth();
            TotalUp += user.getUploadedBytes();

            DayDn += user.getDownloadedBytesDay();
            WeekDn += user.getDownloadedBytesWeek();
            MonthDn += user.getDownloadedBytesMonth();
            TotalDn += user.getDownloadedBytes();

            MonthUpAvrage += user.getUploadedTimeForTrialPeriod(PERIOD_MONTHLY);
            WeekUpAvrage += user.getUploadedTimeForTrialPeriod(PERIOD_WEEKLY);
            DayUpAvrage += user.getUploadedTimeForTrialPeriod(PERIOD_DAILY);
            TotalUpAvrage += user.getUploadedTime();

            MonthDnAvrage += user.getDownloadedTimeForTrialPeriod(PERIOD_MONTHLY);
            WeekDnAvrage += user.getDownloadedTimeForTrialPeriod(PERIOD_WEEKLY);
            DayDnAvrage += user.getDownloadedTimeForTrialPeriod(PERIOD_DAILY);
            TotalDnAvrage += user.getDownloadedTime();
        }

        try {
            Textoutput.addTextToResponse(response, "traffic_header");
        } catch (IOException ioe) {
            logger.warn("Error reading traffic_header - " + ioe.getMessage());
        }

        AddTrafficComment("Total Uploads", TotalUpAvrage, TotalUp,
            TotalFilesUp, response);
        AddTrafficComment("Total Downloads", TotalDnAvrage, TotalDn,
            TotalFilesDn, response);
        AddTrafficComment("Month Uploads", MonthUpAvrage, MonthUp,
            MonthFilesUp, response);
        AddTrafficComment("Month Downloads", MonthDnAvrage, MonthDn,
            MonthFilesDn, response);
        AddTrafficComment("Week Uploads", WeekUpAvrage, WeekUp, WeekFilesUp,
            response);
        AddTrafficComment("Week Downloads", WeekDnAvrage, WeekDn, WeekFilesDn,
            response);
        AddTrafficComment("Day Uploads", DayUpAvrage, DayUp, DayFilesUp,
            response);
        AddTrafficComment("Day Downloads", DayDnAvrage, DayDn, DayFilesDn,
            response);

        try {
            Textoutput.addTextToResponse(response, "traffic_footer");
        } catch (IOException ioe) {
            logger.warn("Error reading traffic_footer - " + ioe.getMessage());
        }

        return response;
    }

    public class MyGroupPosition extends GroupPosition {
        int members;
        int racesWon;

        public MyGroupPosition(String groupname, long bytes, int files,
            long xfertime, int members, int racesWon) {
            super(groupname, bytes, files, xfertime);
            this.members = members;
            this.racesWon = racesWon;

            //this.groupname = groupname;

            /*this.bytes = bytes;
            this.files = files;
            this.xfertime = xfertime;*/
        }

        public int getMembers() {
            return this.members;
        }

        public void updateMembers(int members) {
            this.members += members;
        }

        public int getRacesWon() {
            return this.racesWon;
        }

        public void updateRacesWon(int races) {
            this.racesWon += races;
        }
    }
}
