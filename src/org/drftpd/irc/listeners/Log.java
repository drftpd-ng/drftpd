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

package org.drftpd.irc.listeners;

import org.apache.log4j.Logger;
import org.drftpd.irc.SiteBot;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;

/**
 * A simple log listener.<br>
 * Every event is logged, there's no 'debug level' yet.
 * @author fr0w
 */
public class Log extends IRCListener implements IRCEventListener {
	
	private static final Logger logger = Logger.getLogger(Log.class);

	public Log(SiteBot instance) {
		super(instance);
	}
	
	public void onError(String msg) {
		logger.debug("IRCd returned an error message ("+ msg +")");
	}
	
	public void onError(int num, String msg) {
		logger.debug("IRCd returned an error code #" + num + " with message ("+ msg +")");
	}
	
	public void onInvite(String chan, IRCUser user, String nickPass) {
		logger.debug("Invite: " + user.getNick() + "invites " + nickPass + " to " + chan);
	}
	
	public void onJoin(String chan, IRCUser user) {
		logger.debug("Join: " + user.getNick() + " has joined " + chan);
	}
	
	public void onKick(String chan, IRCUser user, String nickPass, String msg) {
		logger.debug("Kick: " + user.getNick() + " kicks " + nickPass + "(" + msg + ")");
	}
	
	public void onMode(String chan, IRCUser user, IRCModeParser modeParser) {
		logger.debug("Mode: " + user.getNick() + " changes modes in " + chan + ": " + modeParser.getLine());
	}
	
	public void onNick(IRCUser user, String nickNew) {
		logger.debug("Nick: " + user.getNick() + " is now known as "+ nickNew);
	}
	
	public void onPart(String chan, IRCUser user, String msg) {
		logger.debug("Part: " + user.getNick() + " parts from "+ chan + "(" + msg + ")");
	}
	
	public void onPrivmsg(String target, IRCUser user, String msg) {
		logger.debug("PrivMSG: " + user.getNick() + " to " + target + ": " + msg);
	}
	
	public void onQuit(IRCUser user, String msg) {
		logger.debug("Quit: " + user.getNick() + " ("+ user.getUsername() +"@"+ user.getHost() +") ("+ msg +")");
	}
	
	public void onReply(int num, String value, String msg) {
		logger.debug("Reply #" + num + ": Message: " + msg);
	}
	
	public void onTopic(String chan, IRCUser user, String topic) {
		logger.debug("Topic: " + user.getNick() + " changes topic of " + chan + " into: "+ topic);
	}

	public void onRegistered() {
		logger.debug("Connected and registered to " + getSiteBot().getIRCConnection().getHost());
	}

	public void onDisconnected() {
		logger.debug("Disconnected from " + getSiteBot().getIRCConnection().getHost());
	}

	public void onNotice(String target, IRCUser user, String msg) {
		logger.debug("Notice: " + user.getNick() + " to " + target + ": " + msg);		
	}

}
