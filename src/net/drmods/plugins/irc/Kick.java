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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.GenericAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.State;
import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author Teflon
 * @version $Id: Kick.java 886 2005-01-04 21:57:17Z teflon $
 */
public class Kick extends GenericAutoService implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Kick.class);
    private SiteBot _listener;
    private String _trigger;

    public Kick(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
        _trigger = _listener.getCommandPrefix();
    }

    public String getCommands() {
        return _trigger + "kick";
    }

    public String getCommandsHelp() {
    	return _trigger + "kick : Kicks idle users from the ftp server.";
    }

    protected void updateCommand(InCommand inCommand) {
        if (!(inCommand instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) inCommand;

        if (!msgc.getMessage().startsWith(_trigger + "kick")) {
            return;
        }

        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }

        String cmduser = msgc.getSource().getNick();
        String cmdchan = msgc.getDest();

        try {
            ReplacerFormat kickirc = ReplacerUtils.finalFormat(Kick.class,
                    "kick.irc");
            ReplacerFormat kickftp = ReplacerUtils.finalFormat(Kick.class,
                    "kick.ftp");

            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(getGlobalContext().getConnectionManager()
                                                .getConnections());

            for (BaseFtpConnection conn : conns) {
                User user;
                try {
                    user = conn.getUser();
                } catch (NoSuchUserException e) {
                    continue;
                }

                if (getGlobalContext().getConfig().checkPathPermission("hideinwho", user, conn.getCurrentDirectory())) {
                    continue;
                }

                env.add("idletime",
                    ((System.currentTimeMillis() - conn.getLastActive()) / 1000) +
                    "s");
                env.add("idleuser", user.getName());
                env.add("ircuser", cmduser);
                env.add("ircchan", cmdchan);

                if (!conn.getDataConnectionHandler().isTransfering()) {
                    conn.stop(SimplePrintf.jprintf(kickftp, env));
                    _listener.sayChannel(msgc.getDest(),
                        SimplePrintf.jprintf(kickirc, env));
                }
            }
        } catch (FormatterException e) {
            logger.warn("", e);
        }
    }

	private GlobalContext getGlobalContext() {
		return _listener.getGlobalContext();
	}

	protected void updateState(State state) {
    }
}
