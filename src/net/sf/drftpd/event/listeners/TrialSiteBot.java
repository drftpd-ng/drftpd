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
package net.sf.drftpd.event.listeners;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import net.sf.drftpd.event.listeners.Trial.Limit;
import net.sf.drftpd.master.BaseFtpConnection;

import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import org.tanesha.replacer.ReplacerEnvironment;

import java.util.Calendar;
import java.util.Iterator;


public class TrialSiteBot extends GenericCommandAutoService {
    private static final Logger logger = Logger.getLogger(TrialSiteBot.class);
    private final Trial _trial;
    private SiteBot _irc;

    protected TrialSiteBot(Trial trial, SiteBot irc) {
        super(irc.getIRCConnection());
        _trial = trial;
        _irc = irc;
    }

    protected String jprintf(String key, User user, Limit limit,
        long bytesleft, boolean unique) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("user", user.getUsername());

        if (limit != null) {
            env.add("period", Trial.getPeriodName(limit.getPeriod()));
            env.add("name", limit.getName());

            if (unique) {
                env.add("bonusexpires",
                    Trial.getCalendarForEndOfBonus(user, limit.getPeriod())
                         .getTime());
                env.add("uniqueexpires",
                    Trial.getCalendarForEndOfFirstPeriod(user, limit.getPeriod())
                         .getTime());
            }

            env.add("expires",
                Trial.getCalendarForEndOfPeriod(limit.getPeriod()).getTime());
        }

        env.add("bytesleft", Bytes.formatBytes(bytesleft));
        env.add("bytespassed", Bytes.formatBytes(-bytesleft));

        return BaseFtpConnection.jprintf(TrialSiteBot.class, key, env, user);
    }

    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) command;

        if (msgc.isPrivateToUs(_irc.getIRCConnection().getClientState())) {
            return;
        }

        String msg = msgc.getMessage();

        if (!msg.startsWith("!passed ")) {
            return;
        }

        String username = msg.substring("!passed ".length());
        User user;

        try {
            user = _trial.getConnectionManager().getGlobalContext()
                         .getUserManager().getUserByName(username);
        } catch (NoSuchUserException e) {
            _irc.sayChannel(msgc.getDest(), "[passed] No such user: " +
                username);
            logger.info("", e);

            return;
        } catch (UserFileException e) {
            logger.warn("", e);

            return;
        }

        int i = 0;

        for (Iterator iter = _trial.getLimits().iterator(); iter.hasNext();) {
            Limit limit = (Limit) iter.next();

            if (limit.getPerm().check(user)) {
                i++;

                Calendar endofbonus = Trial.getCalendarForEndOfBonus(user,
                        limit.getPeriod());

                if (System.currentTimeMillis() <= endofbonus.getTimeInMillis()) {
                    //in bonus or unique period
                    long bytesleft = limit.getBytes() -
                        user.getUploadedBytes();

                    if (bytesleft <= 0) {
                        //in bonus or passed
                        _irc.sayChannel(msgc.getDest(),
                            jprintf("passedunique", user, limit, bytesleft, true));
                    } else {
                        //in unique period
                        _irc.sayChannel(msgc.getDest(),
                            jprintf("trialunique", user, limit, bytesleft, true));
                    }
                } else {
                    //plain trial
                    long bytesleft = limit.getBytes() -
                        Trial.getUploadedBytesForPeriod(user, limit.getPeriod());

                    if (bytesleft <= 0) {
                        _irc.sayChannel(msgc.getDest(),
                            jprintf("passed", user, limit, bytesleft, false));
                    } else {
                        _irc.sayChannel(msgc.getDest(),
                            jprintf("trial", user, limit, bytesleft, false));
                    }
                }
            }
        }

        if (i == 0) {
            _irc.sayChannel(msgc.getDest(),
                jprintf("exempt", user, null, 0, false));
        }
    }
}
