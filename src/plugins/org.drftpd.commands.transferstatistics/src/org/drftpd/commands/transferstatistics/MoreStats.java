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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.usermanager.User;
import org.drftpd.util.GroupPosition;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.ResourceBundle;


/**
 * @author Unknown
 * @author fr0w
 * @version $Id$
 */
public class MoreStats extends CommandInterface {
	public static final short PERIOD_ALL = 0;
    public static final short PERIOD_MONTHLY = 1;
    public static final short PERIOD_WEEKLY = 2;
    public static final short PERIOD_DAILY = 3;
	
    private static final Logger logger = LogManager.getLogger(MoreStats.class);
    
	private ResourceBundle _bundle;

	private String _keyPrefix;
    
    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();
    	_keyPrefix = this.getClass().getName()+".";
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

        if (updn.equalsIgnoreCase("UP")) {
            return user.getUploadedBytesForPeriod(period);
        } else if (updn.equalsIgnoreCase("DN")) {
            return user.getDownloadedBytesForPeriod(period);
        }

        throw new IllegalArgumentException("Unable to parse string");
    }

    public static int getFiles(String command, User user) {
        // AL MONTH WK DAY
        int period = getPeriod(command.substring(0, command.length() - 2));

        // UP DN
        String updn = command.substring(command.length() - 2);

        if (updn.equalsIgnoreCase("UP")) {
            return user.getUploadedFilesForPeriod(period);
        } else if (updn.equalsIgnoreCase("DN")) {
            return user.getDownloadedFilesForPeriod(period);
        }

        throw new IllegalArgumentException("Unable to parse string");
    }

    public static long getTime(String command, User user) {
        // AL MONTH WK DAY
        int period = getPeriod(command.substring(0, command.length() - 2));

        // UP DN
        String updn = command.substring(command.length() - 2);

        if (updn.equalsIgnoreCase("UP")) {
            return user.getUploadedTimeForPeriod(period);
        } else if (updn.equalsIgnoreCase("DN")) {
            return user.getDownloadedTimeForPeriod(period);
        }

        throw new IllegalArgumentException("Unable to parse string");
    }

    public CommandResponse doGroupStats(CommandRequest request, String type) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        
        type = type.substring("G".length());
        
        int count = 10;
        if (request.hasArgument()) {
            try {
                count = Integer.parseInt(request.getArgument());
            } catch (NumberFormatException e) {
                //ignore input and use 10 as default
            }
        }
        
        ArrayList<MyGroupPosition> grpList = new ArrayList<>();
        Collection<User> users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();

        MyGroupPosition stat = null;
        String groupname = "";

        for (User user : users) {
            groupname = user.getGroup();

            for (MyGroupPosition stat2 : grpList) {
            	if (stat2.getGroupname().equals(groupname)) {
            		stat = stat2;
            		break;
            	}
            }

            if (stat == null) {
                stat = new MyGroupPosition(groupname, 0, 0, 0, 0, 0);
                grpList.add(stat);
            }

            logger.debug("Type: {}", type);
            stat.updateBytes(getStats(type, user));
            stat.updateFiles(getFiles(type, user));
            stat.updateXfertime(getTime(type, user));
            stat.updateMembers(1);
            stat = null;
        }

        Collections.sort(grpList);

        ReplacerEnvironment env = new ReplacerEnvironment();

        //morestats.grpstats=| ${grp,-15} |${grpname,7} |${files,8} | ${megs,9} | ${members,9} |
        try {
        	addTextToResponse(response, "userdata/text/g" + type.toLowerCase() + "_header.txt");
        } catch (IOException ioe) {
            logger.warn("Error reading userdata/text/g{}_header.txt", type.toLowerCase());
        }

        int i = 0;

        for (MyGroupPosition grp : grpList) {
            if (++i > count) {
                break;
            }

            env.add("none", "");
            env.add("pos", i);
            env.add("grp", grp.getGroupname());
            env.add("files", grp.getFiles());
            env.add("megs", Bytes.formatBytes(grp.getBytes()));

            double avrage = grp.getXfertime();
            double s = avrage / 1000.0;

            if (s <= 0) {
                avrage = 0;
            } else {
                avrage = grp.getBytes() / s;
            }

            env.add("average", Bytes.formatBytes((long) avrage));

            env.add("members", grp.getMembers());
            response.addComment(ReplacerUtils.jprintf(_keyPrefix+"morestats.grpstats", env, _bundle));
        }

        try {
            addTextToResponse(response,"userdata/text/g" + type.toLowerCase() + "_footer.txt");
        } catch (IOException ioe) {
            logger.warn("Error reading userdata/text/{}_footer.txt", type.toLowerCase());
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

    private void addTrafficComment(String type, double avrage, long megs, int files, CommandResponse response) {
        double s = avrage / 1000;

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
        response.addComment(ReplacerUtils.jprintf(_keyPrefix+"morestats.trafficstats", env, _bundle));
    }

    public CommandResponse doSITE_TRAFFIC(CommandRequest request) {
    	CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        Collection<User> users = GlobalContext.getGlobalContext().getUserManager().getAllUsers();
        
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

        for (User user : users) {
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

            MonthUpAvrage += user.getUploadedTimeForPeriod(PERIOD_MONTHLY);
            WeekUpAvrage += user.getUploadedTimeForPeriod(PERIOD_WEEKLY);
            DayUpAvrage += user.getUploadedTimeForPeriod(PERIOD_DAILY);
            TotalUpAvrage += user.getUploadedTime();

            MonthDnAvrage += user.getDownloadedTimeForPeriod(PERIOD_MONTHLY);
            WeekDnAvrage += user.getDownloadedTimeForPeriod(PERIOD_WEEKLY);
            DayDnAvrage += user.getDownloadedTimeForPeriod(PERIOD_DAILY);
            TotalDnAvrage += user.getDownloadedTime();
        }

        try {
            addTextToResponse(response, "userdata/text/traffic_header.txt");
        } catch (IOException ioe) {
            logger.warn("Error reading userdata/text/traffic_header - {}", ioe.getMessage());
        }

        addTrafficComment("Total Uploads", TotalUpAvrage, TotalUp, TotalFilesUp, response);
        addTrafficComment("Total Downloads", TotalDnAvrage, TotalDn, TotalFilesDn, response);
        addTrafficComment("Month Uploads", MonthUpAvrage, MonthUp, MonthFilesUp, response);
        addTrafficComment("Month Downloads", MonthDnAvrage, MonthDn, MonthFilesDn, response);
        addTrafficComment("Week Uploads", WeekUpAvrage, WeekUp, WeekFilesUp, response);
        addTrafficComment("Week Downloads", WeekDnAvrage, WeekDn, WeekFilesDn, response);
        addTrafficComment("Day Uploads", DayUpAvrage, DayUp, DayFilesUp, response);
        addTrafficComment("Day Downloads", DayDnAvrage, DayDn, DayFilesDn, response);

        try {
            addTextToResponse(response, "userdata/text/traffic_footer.txt");
        } catch (IOException ioe) {
            logger.warn("Error reading userdata/text/traffic_footer - {}", ioe.getMessage());
        }

        return response;
    }

    static class MyGroupPosition extends GroupPosition {
        int members;

        public MyGroupPosition(String groupname, long bytes, int files,
            long xfertime, int members, int racesWon) {
            super(groupname, bytes, files, xfertime);
            this.members = members;
        }

        public int getMembers() {
            return this.members;
        }

        public void updateMembers(int updatemembers) {
            members += updatemembers;
        }
    }
    
    public CommandResponse doSITE_GALUP(CommandRequest request) {
    	return doGroupStats(request, "galup");
    }

    public CommandResponse doSITE_GALDN(CommandRequest request) {
    	return doGroupStats(request, "galdn");
    }

    public CommandResponse doSITE_GMONTHUP(CommandRequest request) {
    	return doGroupStats(request, "gmonthup");
    }

    public CommandResponse doSITE_GMONTHDN(CommandRequest request) {
    	return doGroupStats(request, "gmonthdn");
    }

    public CommandResponse doSITE_GWKUP(CommandRequest request) {
    	return doGroupStats(request, "gwkup");
    }

    public CommandResponse doSITE_GWKDN(CommandRequest request) {
    	return doGroupStats(request, "gwkdn");
    }

    public CommandResponse doSITE_GDAYUP(CommandRequest request) {
    	return doGroupStats(request, "gdayup");
    }

    public CommandResponse doSITE_GDAYDN(CommandRequest request) {
    	return doGroupStats(request, "gdaydn");
    }
}
