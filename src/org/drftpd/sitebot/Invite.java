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

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

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
    private ConnectionManager _cm;
    private SiteBot _irc;
    private String _trigger;

    public Invite(SiteBot ircListener) {
        super(ircListener.getIRCConnection());
        _cm = ircListener.getConnectionManager();
        _irc = ircListener;
        _trigger = _irc.getMessageCommandPrefix();
    }

    public String getCommands() {
        return _trigger + "invite(msg)";
    }

    public String getCommandsHelp() {
    	return _trigger + "invite <user> <pass> : Invite yourself to site channel.";
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
                user = _cm.getGlobalContext().getUserManager().getUserByName(args[1]);
            } catch (NoSuchUserException e) {
                logger.log(Level.WARN, args[1] + " " + e.getMessage(), e);

                return;
            } catch (UserFileException e) {
                logger.log(Level.WARN, "", e);

                return;
            }

            boolean success = user.checkPassword(args[2]);
            getConnectionManager().dispatchFtpEvent(new InviteEvent(success
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

    private ConnectionManager getConnectionManager() {
        return _cm;
    }
}
