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
package net.sf.drftpd.event.irc;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.listeners.Trial;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.UserComparator;

import org.drftpd.commands.TransferStatistics;

import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.StringTokenizer;


/**
 * @author zubov
  * @version $Id: Stats.java,v 1.15 2004/11/08 18:39:23 mog Exp $
 */
public class Stats extends GenericCommandAutoService
    implements IRCPluginInterface {
    private ConnectionManager _cm;

    public Stats(SiteBot ircListener) {
        super(ircListener.getIRCConnection());
        _cm = ircListener.getConnectionManager();
    }

    public String getCommands() {
        return "!{al,wk,month,day}{up,dn}";
    }

    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) command;
        StringTokenizer st = new StringTokenizer(msgc.getMessage());

        if (!st.hasMoreTokens()) {
            return;
        }

        String msg = st.nextToken();
        String type = null;

        if (msg.startsWith("!alup")) {
            type = "ALUP";
        } else if (msg.startsWith("!aldn")) {
            type = "ALDN";
        } else if (msg.startsWith("!wkup")) {
            type = "WKUP";
        } else if (msg.startsWith("!wkdn")) {
            type = "WKDN";
        } else if (msg.startsWith("!daydn")) {
            type = "DAYDN";
        } else if (msg.startsWith("!dayup")) {
            type = "DAYUP";
        } else if (msg.startsWith("!monthdn")) {
            type = "MONTHDN";
        } else if (msg.startsWith("!monthup")) {
            type = "MONTHUP";
        }

        if (type == null) {
            return; // msg is not for us
        }

        String destination = null;

        if (msgc.isPrivateToUs(getConnection().getClientState())) {
            //destination = msgc.getSource().getNick();
        } else {
            destination = msgc.getDest();
        }

        Collection users = null;

        try {
            users = _cm.getGlobalContext().getUserManager().getAllUsers();
        } catch (UserFileException e) {
            getConnection().sendCommand(new MessageCommand(destination,
                    "Error processing userfiles"));

            return;
        }

        int number = fixNumberAndUserlist(msgc.getMessage(), users);

        ArrayList users2 = new ArrayList(users);
        Collections.sort(users2, new UserComparator(type));

        int i = 0;

        for (Iterator iter = users2.iterator(); iter.hasNext();) {
            if (++i > number) {
                break;
            }

            //TODO .jprintf() has most of this afaik
            User user = (User) iter.next();
            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            env.add("pos", "" + i);
            env.add("user", user.getUsername());
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
                getConnection().sendCommand(new MessageCommand(destination,
                        SimplePrintf.jprintf(ReplacerUtils.jprintf(
                                "transferstatistics" + type, env, Stats.class),
                            env)));
            } catch (FormatterException e) {
                getConnection().sendCommand(new MessageCommand(destination,
                        "FormatterException for transferstatistics" + type));

                break;
            }
        }
    }

    public static int fixNumberAndUserlist(String params, Collection userList) {
        int number = 10;
        com.Ostermiller.util.StringTokenizer st = new com.Ostermiller.util.StringTokenizer(params);
        st.nextToken(); // !alup

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
