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
package org.drftpd.sitebot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.UserComparator;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.permissions.Permission;
import org.drftpd.plugins.SiteBot;
import org.drftpd.plugins.Trial;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author zubov
  * @version $Id$
 */
public class Stats extends IRCCommand {
	private static final Logger logger = Logger.getLogger(Stats.class);
    
    public Stats() {
		super();
    }

    public ArrayList<String> doALUP(String args, MessageCommand msgc) { return doStats(args, "ALUP"); }
    public ArrayList<String> doALDN(String args, MessageCommand msgc) { return doStats(args, "ALDN"); }
    public ArrayList<String> doMONTHUP(String args, MessageCommand msgc) { return doStats(args, "MONTHUP"); }
    public ArrayList<String> doMONTHDN(String args, MessageCommand msgc) { return doStats(args, "MONTHDN"); }
    public ArrayList<String> doWKUP(String args, MessageCommand msgc) { return doStats(args, "WKUP"); }
    public ArrayList<String> doWKDN(String args, MessageCommand msgc) { return doStats(args, "WKDN"); }
    public ArrayList<String> doDAYUP(String args, MessageCommand msgc) { return doStats(args, "DAYUP"); }
    public ArrayList<String> doDAYDN(String args, MessageCommand msgc) { return doStats(args, "DAYDN"); }

	public ArrayList<String> doStats(String args, String type) {
	    ArrayList<String> out = new ArrayList<String>();
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

        Collection<User> users = null;
        try {
            users = getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            out.add("Error processing userfiles");
            logger.error("Error processing userfiles", e);
            return out;
        }

        int number = fixNumberAndUserlist(args, users);

        ArrayList<User> users2 = new ArrayList<User>(users);
        Collections.sort(users2, new UserComparator(type));

        int i = 0;
        for (Iterator iter = users2.iterator(); iter.hasNext();) {
            if (++i > number) {
                break;
            }

            //TODO .jprintf() has most of this afaik
            User user = (User) iter.next();
            env = BaseFtpConnection.getReplacerEnvironment(env,user);
            env.add("pos", "" + i);
            env.add("user", user.getName());
            env.add("upbytesday", Bytes.formatBytes(user.getUploadedBytesDay()));
            env.add("upfilesday", "" + user.getUploadedFilesDay());
            env.add("uprateday",
                TransferStatistics.getUpRate(user, Trial.PERIOD_DAILY));
            env.add("upbytesweek",
                Bytes.formatBytes(user.getUploadedBytesWeek()));
            env.add("upfilesweek", "" + user.getUploadedFilesWeek());
            env.add("uprateweek",
                TransferStatistics.getDownRate(user, Trial.PERIOD_WEEKLY));
            env.add("upbytesmonth",
                Bytes.formatBytes(user.getUploadedBytesMonth()));
            env.add("upfilesmonth", "" + user.getUploadedFilesMonth());
            env.add("upratemonth",
                TransferStatistics.getUpRate(user, Trial.PERIOD_MONTHLY));
            env.add("upbytes", Bytes.formatBytes(user.getUploadedBytes()));
            env.add("upfiles", "" + user.getUploadedFiles());
            env.add("uprate",
                TransferStatistics.getUpRate(user, Trial.PERIOD_ALL));

            env.add("dnbytesday",
                Bytes.formatBytes(user.getDownloadedBytesDay()));
            env.add("dnfilesday", "" + user.getDownloadedFilesDay());
            env.add("dnrateday",
                TransferStatistics.getDownRate(user, Trial.PERIOD_DAILY));
            env.add("dnbytesweek",
                Bytes.formatBytes(user.getDownloadedBytesWeek()));
            env.add("dnfilesweek", "" + user.getDownloadedFilesWeek());
            env.add("dnrateweek",
                TransferStatistics.getDownRate(user, Trial.PERIOD_WEEKLY));
            env.add("dnbytesmonth",
                Bytes.formatBytes(user.getDownloadedBytesMonth()));
            env.add("dnfilesmonth", "" + user.getDownloadedFilesMonth());
            env.add("dnratemonth",
                TransferStatistics.getDownRate(user, Trial.PERIOD_MONTHLY));
            env.add("dnbytes", Bytes.formatBytes(user.getDownloadedBytes()));
            env.add("dnfiles", "" + user.getDownloadedFiles());
            env.add("dnrate",
                TransferStatistics.getDownRate(user, Trial.PERIOD_ALL));
            type = type.toLowerCase();

            try {
                out.add(SimplePrintf.jprintf(ReplacerUtils.jprintf(
                                "transferstatistics" + type, env, Stats.class),
                            env));
            } catch (FormatterException e) {
                out.add("FormatterException for transferstatistics" + type);

                break;
            }
        }

		return out;
	}

	public static int fixNumberAndUserlist(String params, Collection userList) {
        int number = 10;
        com.Ostermiller.util.StringTokenizer st = new com.Ostermiller.util.StringTokenizer(params);
        //st.nextToken(); // !alup

        if (!st.hasMoreTokens()) {
            return 10;
        }

        if (st.hasMoreTokens()) {
            //StringTokenizer st2 = st.clone();
            try {
                number = Integer.parseInt(st.peek());
                st.nextToken();
            } catch (NumberFormatException ex) {
            }

            while (st.hasMoreTokens()) {
                Permission perm = new Permission(FtpConfig.makeUsers(st));

                for (Iterator iter = userList.iterator(); iter.hasNext();) {
                    User user = (User) iter.next();

                    if (!perm.check(user)) {
                        iter.remove();
                    }
                }
            }
        }

        return number;
    }
}
