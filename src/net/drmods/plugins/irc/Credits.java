/*
*
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

import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.master.ConnectionManager;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sitebot.IRCPluginInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author Teflon
 * @version $Id$
 */
public class Credits extends GenericCommandAutoService implements IRCPluginInterface {

	private static final Logger logger = Logger.getLogger(Credits.class);

	private SiteBot _listener;

	public Credits(SiteBot listener) {
		super(listener.getIRCConnection());
		_listener = listener;
	}

	public String getCommands() {
		return _listener.getCommandPrefix() + "credits";
	}

    public String getCommandsHelp(User user) {
        String help = "";
        if (_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "credits", user))
            help += _listener.getCommandPrefix() 
            		+ "credits [\"*\"|username]: Displays the user's credits\n";
		return help;
    }

    private ConnectionManager getConnectionManager() {
		return _listener.getGlobalContext().getConnectionManager();
	}

	private UserManager getUserManager() {
		return _listener.getGlobalContext().getUserManager();
	}

	protected void updateCommand(InCommand command) {
		if (!(command instanceof MessageCommand)) {
			return;
		}
		MessageCommand msgc = (MessageCommand) command;
		if (msgc.isPrivateToUs(_listener.getIRCConnection().getClientState())) {
			return;
		}

		String msg = msgc.getMessage().trim();
		if (msg.startsWith(_listener.getCommandPrefix() + "credits")) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("botnick",_listener.getIRCConnection().getClientState().getNick().getNick());
			env.add("ircnick",msgc.getSource().getNick());

			User user;
			try {
	            user = _listener.getIRCConfig().lookupUser(msgc.getSource());
	        } catch (NoSuchUserException e) {
				_listener.say(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
				return;
	        }
			env.add("ftpuser",user.getName());

			if (!_listener.getIRCConfig().checkIrcPermission(_listener.getCommandPrefix() + "credits",user)) {
				_listener.say(msgc.getDest(), 
						ReplacerUtils.jprintf("ident.denymsg", env, SiteBot.class));
				return;				
			}

			User ftpuser = user;
			String args[] = msg.split(" ");
			if (args.length > 1) {
				if (args[1].equals("*")){
					showAllUserCredits(msgc);
					return;
				} else {
					try {
						ftpuser = getConnectionManager().getGlobalContext()
										.getUserManager()
											.getUserByName(args[1]);
					} catch (NoSuchUserException e) {
						env.add("user", args[1]);
						_listener.say(msgc.getDest(), 
							ReplacerUtils.jprintf("credits.error", env, Credits.class));
						return;
					} catch (UserFileException e) {
						logger.warn("", e);
						return;
					}
				}
			}
													
			env.add("user", ftpuser.getName());
			env.add("credits",Bytes.formatBytes(ftpuser.getCredits()));
			_listener.say(msgc.getDest(), 
				ReplacerUtils.jprintf("credits.user", env, Credits.class));			

		}
	}
	
	protected void showAllUserCredits(MessageCommand msgc) {
		long totalcredz = 0;
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		try {
			ArrayList<User> users = new ArrayList<User>(getUserManager().getAllUsers());
			for (Iterator iter = users.iterator(); iter.hasNext();) {
				User user = (User) iter.next();
				totalcredz += user.getCredits();
			}
			env.add("usercount",Integer.toString(users.size()));
			env.add("totalcredits",Bytes.formatBytes(totalcredz));
			_listener.say(msgc.getDest(), 
				ReplacerUtils.jprintf("credits.total", env, Credits.class));			
		} catch (UserFileException e) {
			logger.warn(e);
		}
	}

}
