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
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
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
 * @version $Id$
 */
public class Kick extends GenericAutoService implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Kick.class);
    private SiteBot _listener;

    public Kick(SiteBot listener) {
        super(listener.getIRCConnection());
        _listener = listener;
    }

    public String getCommands() {
        return _listener.getCommandPrefix() + "kick";
    }

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "kick", user))
            help += _listener.getCommandPrefix() + "kick : Kick idle users off the server.\n";
        return help;
    }

    private ConfigInterface getConfig() {
        return _listener.getGlobalContext().getConfig();
    }

    private ConnectionManager getConnectionManager() {
        return _listener.getGlobalContext().getConnectionManager();
    }

    protected void updateCommand(InCommand inCommand) {
        if (!(inCommand instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) inCommand;

        if (!msgc.getMessage().startsWith(_listener.getCommandPrefix() + "kick")) {
            return;
        }

        if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
            return;
        }
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
		env.add("ircnick",msgc.getSource().getNick());	
		try {
            if (!_listener.getIRCConfig().checkIrcPermission(
                    _listener.getCommandPrefix() + "kick",msgc.getSource())) {
            	_listener.say(msgc.getDest(), 
            			ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
            	return;				
            }
        } catch (NoSuchUserException e) {
			_listener.say(msgc.getDest(), 
					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			return;
        }

        String cmd = msgc.getMessage();
        String cmduser = msgc.getSource().getNick();
        String cmdchan = msgc.getDest();

        try {
            ReplacerFormat kickirc = ReplacerUtils.finalFormat(Kick.class,
                    "kick.ircmsg");
            ReplacerFormat kickftp = ReplacerUtils.finalFormat(Kick.class,
                    "kick.ftpmsg");
            ReplacerFormat userformat = ReplacerUtils.finalFormat(Kick.class,
                    "kick.userformat");
            //ResourceBundle bundle = ResourceBundle.getBundle(TDPKick.class.getName());
            //String userformat = bundle.getString("kick.userformat");
            
			long idlelimit = 0;
			try {
				idlelimit = Long.parseLong(ReplacerUtils.jprintf("kick.idlelimit", env, Kick.class));
			} catch (NumberFormatException e1) {
				logger.warn("kick.idlelimit in Kick.properties is not set to a valid integer value.");
				return;
			}
			env.add("idlelimit",Long.toString(idlelimit));

            ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(getConnectionManager()
                                                .getConnections());
            int count = 0;
            String msg = "";
            boolean found = false;
            for (Iterator iter = conns.iterator(); iter.hasNext();) {
                BaseFtpConnection conn = (BaseFtpConnection) iter.next();
                User cuser;

                try {
					cuser = conn.getUser();
                } catch (NoSuchUserException e) {
                    continue;
                }

				long idletime = (System.currentTimeMillis() - conn.getLastActive()) / 1000;
                env.add("idletime", idletime + "s");
                env.add("idleuser", cuser.getName());
                env.add("ircuser", cmduser);
                env.add("ircchan", cmdchan);

                if (!conn.getDataConnectionHandler().isTransfering()
                	&& idletime > idlelimit) {
                    conn.stop(SimplePrintf.jprintf(kickftp, env));
                    msg += SimplePrintf.jprintf(userformat, env) + " ";
                    count++;
                    found = true;
                }
                if ((count >= 10 || !iter.hasNext()) && !msg.trim().equals("")) {
                    env.add("users", msg.trim());
                    _listener.say(msgc.getDest(),SimplePrintf.jprintf(kickirc, env));
                    count = 0;
                    msg = "";
                }
            }
            if (!found) {
				_listener.say(msgc.getDest(),
					ReplacerUtils.jprintf("kick.none", env, Kick.class));
            }
        } catch (FormatterException e) {
            logger.warn("", e);
        }
    }

    protected void updateState(State state) {
    }
}
