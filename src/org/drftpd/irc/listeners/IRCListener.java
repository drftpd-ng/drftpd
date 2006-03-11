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
import org.drftpd.GlobalContext;
import org.drftpd.irc.SiteBot;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;

/**
 * DrFTPd own EventAdapter.<br>
 * Instead of having lots of equal methods<br>
 * it's much nicer having a super class.
 * @author fr0w
 */
public class IRCListener {
	private static final Logger logger = Logger.getLogger(IRCListener.class); 
	
	private SiteBot _instance;
	
	public IRCListener(SiteBot instance) {
		_instance = instance;
		logger.debug("Adding IRCEventListener: " + getClass().getName());
	}
	
	public SiteBot getSiteBot() {
		return _instance;
	}
	
	public String getBotNick() {
		return getSiteBot().getIRCConnection().getNick();
	}
	
	public IRCConnection getIRCConnection() {
		return getSiteBot().getIRCConnection();
	}
	
	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}
	
	public void onRegistered() {
		// nothing
	}
	
	public void onDisconnected() {
		// nothing
	}
	
	public void onError(String msg) {
		// nothing
	}
	
	public void onError(int num, String msg) {
		// nothing
	}
	
	public void onInvite(String chan, IRCUser user, String passiveNick) {
		// nothing
	}
	
	public void onJoin(String chan, IRCUser user) {
		// nothing
	}
	
	public void onKick(String chan, IRCUser user, String passiveNick, 
			String msg) {
		// nothing
	}
	
	public void onMode(String chan, IRCUser user, IRCModeParser modeParser) {
		// nothing
	}
	
	public void onMode(IRCUser user, String passiveNick, String mode) {
		// nothing
	}
	
	public void onNick(IRCUser user, String newNick) {
		// nothing
	}
	
	public void onNotice(String target, IRCUser user, String msg) {
		// nothing
	}
	
	public void onPart(String chan, IRCUser user, String msg) {
		// nothing
	}
	
	public void onPing(String ping) {
		// nothing
	}
	
	public void onPrivmsg(String target, IRCUser user, String msg) {
		// nothing
	}
	
	public void onQuit(IRCUser user, String msg) {
		// nothing
	}
	
	public void onReply(int num, String value, String msg) {
		// nothing
	}
	
	public void onTopic(String chan, IRCUser user, String topic) {
		// nothing
	}
	
	public void unknown(String prefix, String command, String middle,
			String trailing) {
		// nothing
	}
}
