/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.sitebot;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author Teflon
 */
public class Ident extends GenericCommandAutoService implements
		IRCPluginInterface {
    private static final Logger logger = Logger.getLogger(Ident.class);
	private ConnectionManager _cm;
	private SiteBot _irc;
	public static final Key IDENT = new Key(Ident.class,"IRCIdent",String.class);

	public Ident(SiteBot ircListener) {
		super(ircListener.getIRCConnection());
		_cm = ircListener.getConnectionManager();
		_irc = ircListener;
	}

	public String getCommands() {
        return "!ident(msg)";
    }

	protected void updateCommand(InCommand command) {
        if (!(command instanceof MessageCommand)) {
        	//logger.info("not a MessageCommand");
            return;
        }

        MessageCommand msgc = (MessageCommand) command;
        String msg = msgc.getMessage();
        if (msg.startsWith("!ident ") &&
                msgc.isPrivateToUs(this.getConnection().getClientState())) {

        	String[] args = msg.split(" ");
            if (args.length < 2) {
             	return;
            }
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

            if (user.checkPassword(args[2])) {
             	String ident = msgc.getSource().getNick() + "!" 
								+ msgc.getSource().getUser() + "@" 
								+ msgc.getSource().getHost();
            	user.getKeyedMap().setObject(UserManagement.IRCIDENT,ident);
            	try {
					user.commit();
		           	logger.info("Set IRC ident to '"+ident+"' for "+user.getName());
	            	_irc.sayChannel(msgc.getSource().getNick(),"Set IRC ident to '"+ident+"' for "+user.getName());
				} catch (UserFileException e1) {
					logger.warn("Error saving userfile for "+user.getName(),e1);
					_irc.sayChannel(msgc.getSource().getNick(),"Error saving userfile for "+user.getName());
				}
             }
        }
	}
}
