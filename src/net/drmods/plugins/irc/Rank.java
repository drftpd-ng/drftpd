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
package net.drmods.plugins.irc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.UserComparator;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.commands.TransferStatistics;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.plugins.Trial;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author kolor & Teflon
 * 
 */
public class Rank extends GenericCommandAutoService implements
        IRCPluginInterface {

    private static final Logger logger = Logger.getLogger(Approve.class);

    private SiteBot _listener;

    public String getCommands() {
        return _listener.getCommandPrefix() + "rank [user] " +
        		_listener.getCommandPrefix() + "failed";
    }

    private ConnectionManager getConnectionManager() {
        return _listener.getGlobalContext().getConnectionManager();
    }

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(
                _listener.getCommandPrefix() + "rank", user))
            help += _listener.getCommandPrefix()
                    + "rank [user] : Displays the rank of the identified user or the user specified\n";
        if (_listener.getIRCConfig().checkIrcPermission(
                _listener.getCommandPrefix() + "failed", user))
            help += _listener.getCommandPrefix()
                    + "failed : Displays the rank of the identified user or the user specified\n";
        return help;
    }

    public Rank(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
    }

    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }
        MessageCommand msgc = (MessageCommand) command;
        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
        env.add("ircnick", msgc.getSource().getNick());

        String msg = msgc.getMessage().trim();

        if (msg.startsWith(_listener.getCommandPrefix() + "rank")) {
			User user;
			try {
	            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
	        } catch (NoSuchUserException e) {
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
				return;
	        }
			env.add("ftpuser",user.getName());

			if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "rank",user)) {
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
				return;				
			}

            String lookupuser;
            try {
                lookupuser = msgc.getMessage().substring((_listener.getCommandPrefix() + "rank ").length());
            } catch (ArrayIndexOutOfBoundsException e) {
                lookupuser = user.getName();
            } catch (StringIndexOutOfBoundsException e) {
                lookupuser = user.getName();
            }

            String exemptgroups = ReplacerUtils.jprintf("top.exempt", env,
                    Rank.class);

            try {
                User luser = getConnectionManager().getGlobalContext().getUserManager()
                        .getUserByName(lookupuser);
                StringTokenizer st = new StringTokenizer(exemptgroups, " ");
                while (st.hasMoreTokens()) {
                    if (luser.isMemberOf(st.nextToken())) {
                        env.add("eusr", luser.getName());
                        env.add("egrp", luser.getGroup());
                        _listener.sayChannel(msgc.getDest(), ReplacerUtils
                                .jprintf("exempt", env, Rank.class));
                        return;
                    }
                }
            } catch (NoSuchUserException e2) {
                _listener.sayChannel(msgc.getDest(), "Not a valid username");
                return;
            } catch (UserFileException e2) {
                _listener.sayChannel(msgc.getDest(), "Error opening user file");
                return;
            }

            Collection<User> users = null;
            try {
                users = getConnectionManager().getGlobalContext().getUserManager().getAllUsers();
            } catch (UserFileException e) {
                getConnection().sendCommand(
                        new MessageCommand(msgc.getDest(),
                                "Error processing userfiles"));
                return;
            }
            String type = "MONTHUP";

            ArrayList<User> users2 = new ArrayList<User>(users);
            Collections.sort(users2, new UserComparator(type));

            int i = 0;
            int msgtype = 1;
            User user1 = null;
            User user2 = null;
            for (Iterator iter = users.iterator(); iter.hasNext();) {

                user1 = (User) iter.next();
                i = i + 1;
                env.add("ppos", "" + i);
                env.add("puser", user1.getName());
                env.add("pmnup", Bytes.formatBytes(user1
                        .getUploadedBytesMonth()));
                env.add("pupfilesmonth", "" + user1.getUploadedFilesMonth());
                env.add("pmnrateup", TransferStatistics.getUpRate(user1,
                        Trial.PERIOD_MONTHLY));
                env.add("pgroup", user1.getGroup());

                StringTokenizer st2 = new StringTokenizer(exemptgroups, " ");
                while (st2.hasMoreTokens()) {
                    if (user1.isMemberOf(st2.nextToken())) {
                        i = i - 1;
                        break;
                    }
                }

                if (user1.getName().equals(lookupuser)) {
                    break;
                } else
                    msgtype = 2;

                user2 = (User) iter.next();
                i = i + 1;
                env.add("pos", "" + i);
                env.add("user", user2.getName());
                env.add("mnup", Bytes
                        .formatBytes(user2.getUploadedBytesMonth()));
                env.add("upfilesmonth", "" + user2.getUploadedFilesMonth());
                env.add("mnrateup", TransferStatistics.getUpRate(user2,
                        Trial.PERIOD_MONTHLY));
                env.add("toup", Bytes.formatBytes(user1.getUploadedBytesMonth()
                        - user2.getUploadedBytesMonth()));
                env.add("group", user2.getGroup());

                StringTokenizer st3 = new StringTokenizer(exemptgroups, " ");
                while (st3.hasMoreTokens()) {
                    if (user2.isMemberOf(st3.nextToken())) {
                        i = i - 1;
                        break;
                    }
                }

                if (user2.getName().equals(lookupuser)) {
                    break;
                } else
                    msgtype = 3;

            }

            if (msgtype == 1)
                _listener.sayChannel(msgc.getDest(), ReplacerUtils.jprintf(
                        "msg1", env, Rank.class));
            else if (msgtype == 2) {
                if (!user1.equals(null) && !user2.equals(null))
                    env.add("toup", Bytes.formatBytes(user1
                            .getUploadedBytesMonth()
                            - user2.getUploadedBytesMonth()));
                _listener.sayChannel(msgc.getDest(), ReplacerUtils.jprintf(
                        "msg2", env, Rank.class));
            } else if (msgtype == 3) {
                if (!user1.equals(null) && !user2.equals(null))
                    env.add("toup", Bytes.formatBytes(user2
                            .getUploadedBytesMonth()
                            - user1.getUploadedBytesMonth()));
                _listener.sayChannel(msgc.getDest(), ReplacerUtils.jprintf(
                        "msg3", env, Rank.class));
            }

        }

        if (msg.equals(_listener.getCommandPrefix() + "failed")) {

			User user;
			try {
	            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
	        } catch (NoSuchUserException e) {
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
				return;
	        }
			env.add("ftpuser",user.getName());

			if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "failed",user)) {
				_listener.sayChannel(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
				return;				
			}

            String lookupuser;
            try {
                lookupuser = msgc.getMessage().substring((_listener.getCommandPrefix() + "failed").length());
            } catch (ArrayIndexOutOfBoundsException e) {
                lookupuser = user.getName();
            } catch (StringIndexOutOfBoundsException e) {
                lookupuser = user.getName();
            }

            Collection<User> users = null;
            try {
                users = getConnectionManager().getGlobalContext().getUserManager().getAllUsers();
            } catch (UserFileException e) {
                getConnection().sendCommand(
                        new MessageCommand(msgc.getDest(),
                                "Error processing userfiles"));
                return;
            }
            String type = "MONTHUP";
            String failed = "";
            ArrayList<User> users2 = new ArrayList<User>(users);
            Collections.sort(users2, new UserComparator(type));
            int limit = Integer.parseInt(ReplacerUtils.jprintf("top.limit",
                    env, Rank.class));
            int i = 0;
            User checkuser = null;
            for (Iterator iter = users.iterator(); iter.hasNext();) {
                i = i + 1;
                checkuser = (User) iter.next();
                String upped = Bytes.formatBytes(checkuser
                        .getUploadedBytesMonth());
                String userc = checkuser.getName();

                String exemptgroups = ReplacerUtils.jprintf("top.exempt", env,
                        Rank.class);
                StringTokenizer st = new StringTokenizer(exemptgroups, " ");
                boolean exempted = false;
                while (st.hasMoreTokens()) {
                    if (checkuser.isMemberOf(st.nextToken())) {
                        i = i - 1;
                        exempted = true;
                        break;
                    }
                }

                if (i > limit && !exempted) {
                    failed += userc + " (" + upped + ") - ";
                }
            }

            _listener.sayChannel(msgc.getDest(), failed);

        }

    }

}
