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

import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.SiteBot;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author mog
 * @version $Id$
 */
public class Invite extends GenericCommandAutoService
    implements IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Invite.class);
    private SiteBot _listener;
    private String _trigger;
	private GlobalContext _gctx;

    public Invite(SiteBot ircListener) {
        super(ircListener.getIRCConnection());
        _gctx = ircListener.getGlobalContext();
        _listener = ircListener;
        _trigger = _listener.getMessageCommandPrefix();
    }

    public String getCommands() {
        return _trigger + "invite(msg)";
    }

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "invite", user))
            help += _listener.getCommandPrefix() + "invite <user> <pass> : Invite yourself to site channel.\n";
    	return help;
    }
    
    protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
            return;
        }

        MessageCommand msgc = (MessageCommand) command;
        String msg = msgc.getMessage();

        if (msg.startsWith(_trigger + "invite ") &&
                msgc.isPrivateToUs(this.getConnection().getClientState())) {
            String[] args = msg.split(" ");
            User user;

            try {
                user = getGlobalContext().getUserManager().getUserByName(args[1]);
            } catch (NoSuchUserException e) {
                logger.log(Level.WARN, args[1] + " " + e.getMessage(), e);

                return;
            } catch (UserFileException e) {
                logger.log(Level.WARN, "", e);

                return;
            }

            ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
    		env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
    		env.add("ircnick",msgc.getSource().getNick());	
    		try {
                if (!_listener.getIRCConfig().checkIrcPermission(
                        _listener.getCommandPrefix() + "invite",msgc.getSource())) {
                	_listener.say(msgc.getDest(), 
                			ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
                	return;				
                }
            } catch (NoSuchUserException e) {
    			_listener.say(msgc.getDest(), 
    					ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
    			return;
            }

            boolean success = user.checkPassword(args[2]);
            getGlobalContext().dispatchFtpEvent(new InviteEvent(success
                    ? "INVITE" : "BINVITE", msgc.getSource().getNick(), user));

           	String ident = msgc.getSource().getNick() + "!" 
							+ msgc.getSource().getUser() + "@" 
							+ msgc.getSource().getHost();
           	    		
			if (success) {
			    logger.info("Invited \"" + ident + "\" as user " + user.getName());
			} else {
			    logger.log(Level.WARN,
			        msgc.getSourceString() +
			        " attempted invite with bad password: " + msgc);
			}
        }
    }

    private GlobalContext getGlobalContext() {
        return _gctx;
    }
}
