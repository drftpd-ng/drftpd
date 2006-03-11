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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Random;
import java.util.TreeMap;

import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Logger;
import org.drftpd.commands.UserManagement;
import org.drftpd.irc.SiteBot;
import org.drftpd.irc.SiteBot.WhoisEntry;
import org.drftpd.irc.utils.Channel;
import org.drftpd.irc.utils.CommandList;
import org.drftpd.irc.utils.IRCPermission;
import org.drftpd.irc.utils.MessageCommand;
import org.drftpd.sitebot.IRCCommand;
import org.drftpd.usermanager.UserFileException;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCModeParser;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.IRCUtil;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * This is the default listener,<br>
 * without that, your SiteBot will not work properlly.<br>
 * DO NOT UNLOAD THIS.
 * @author fr0w
 */
public class Default extends IRCListener implements IRCEventListener {
	
	private static final Logger logger = Logger.getLogger(Default.class);
	
	public Default(SiteBot instance) {
		super(instance);
	}
	
	private TreeMap<String, CommandList> getMethodMap() {
		return getSiteBot().getMethodMap();
	}
	
	/**
	 * Handles Join/Part Events
	 * @param user
	 * @param chan
	 * @param join
	 */
	private void handleJoinPart(String user, String chan, boolean join) {
		Channel c = null;
		if ((user.equalsIgnoreCase(getBotNick()))) {
			c = getSiteBot().getChannelByName(chan);
			if (c == null) { 
				// removed from config
				return;
			}
			if (join) 
				c.setJoin();
			else {
				logger.debug("Parted from "+ chan);
				c.setPart();
				c.setOp(false);
				getIRCConnection().doJoin(c.getName(), c.getKey());
			}
		}
	}
	
	private void handleJoinPart(IRCUser user, String chan, boolean join) {
		handleJoinPart(user.getNick(), chan, join);
	}
	
	/**
	 * Handles mode events
	 * @param user
	 * @param modeParser
	 * @param chan
	 */
	private void handleMode(IRCUser user, IRCModeParser modeParser, String chan) {
		int iLength = modeParser.getCount();
		char mode; // [o|p|s|i|t|n|b|v]
		char operator; // [+|-]
		String arg; // must be sitebot nickname
		Channel c = getSiteBot().getChannelByName(chan);
		
		for (int i = 1; i <= iLength; i++) {
			arg = modeParser.getArgAt(i);
			operator = modeParser.getOperatorAt(i);
			mode = modeParser.getModeAt(i);
			
			if ((arg.equalsIgnoreCase(getBotNick())) &&
					mode == 'o') {	        		
				if (operator == '+')
					c.setOp(true);
				else
					c.setOp(false);
			}
		}
	}
	
	/**
	 * Handles WHOIS replies
	 * TODO Test!
	 * @param value
	 * @param msg
	 */
	private void handleWhois(String value, String msg) {
		String[] args = value.split(" ");
		String fullIdent = args[1]+"!"+args[2]+"@"+args[3]; 
		
		synchronized (getSiteBot().getIdentWhoisList()) {
			for (Iterator<WhoisEntry> iter = getSiteBot().getIdentWhoisList().iterator(); iter
			.hasNext();) {
				WhoisEntry we = iter.next();
				if (we.getNick().equals(args[1])) {
					we.getUser().getKeyedMap().setObject(
							UserManagement.IRCIDENT, fullIdent);
					
					try {
						we.getUser().commit();
					} catch (UserFileException e) {
					}
					
					iter.remove();
					continue;
				}
				if ((System.currentTimeMillis() - we.getCreated()) > 120 * 1000) {
					iter.remove(); // bad nickname or very slow whois reply
				}
			}
		}
	}
	/**
	 * Handles PRIVMSG events
	 * @param target
	 * @param user
	 * @param msg
	 */
	@SuppressWarnings("unchecked")
	//TODO check!
	private void handlePrivMsg(String target, IRCUser user, String msg) {
		
		boolean priv = SiteBot.isPrivate(target);
		
		if (!priv) {
			try {
				Channel cc = getSiteBot().getChannelByName(target);
				if (cc == null) {
					logger.debug("This is a bug! report me! -- channel=" + target + " ccMap=" + getSiteBot().getChannelMap(), new Throwable());
					return;
				}
				
				if (cc.getBlowKey() != null) {
					if (msg.startsWith("+OK ") || msg.startsWith("mcps ")) {					
						// only decrypt the encrypted msgs.
						msg = cc.decrypt(msg).trim();					
					} else  {
						// this message should've been encrypted, althought it wasnt
						return;
					}
				}
				
			} catch (UnsupportedEncodingException e) {
				logger.warn("Unable to decrypt '"
						+ msg + "'", e);
				return; // should not accept plaintext messages
				// encrypted channels
			}
		}
		
		int index = msg.indexOf(" ");
		String args = "";
		String trigger = "";
		if (index == -1) {
			trigger = msg.toLowerCase();
		} else {
			trigger = msg.substring(0, index);
			args = msg.substring(index + 1).trim();
		}
		
		if (getMethodMap().containsKey(trigger)) { // is a recognized
			// command
			
			ArrayList<Object[]> cList = getMethodMap().get(trigger).getCommandList();
			Object[] objArray = null;
			String errorMsg = null;
			String dest = null;
			String source = null;
			
			// this will count how many times checkPerms return true;
			int validPerm = 0;			
			// this will count how many times checkSource return true;
			int validSource = 0;
			
			boolean checkPerms;
			boolean checkSource;
			
			for (int i = 0; i < cList.size(); i++) {
				//System.out.println("!!!!!!INSIDE THE LOOP!!!!!!!!!");

				//System.out.println("Count #"+i);
				Object[] array = cList.get(i);
				
				IRCPermission perm = (IRCPermission) array[IRCCommand.IRCPERMISSION];
				source = (priv ? "private" : target);
				//System.out.println("Source = " + source);
				dest = perm.getDestination().equals("source") ? target : perm.getDestination();
				//System.out.println("Destination = " + dest);
				
				checkSource = perm.checkSource(source);				
				if (!checkSource) {
					// not a valid source, not going to check if it's a valid perm.
					//System.out.println("Not a valid source: " + source);
					continue;		
				}
				else {
					//System.out.println("Valid Source: " + source);
					validSource++;
				}
				
				checkPerms = perm.checkPermission(user);
				if (!checkPerms) {
					// not allowed
					//System.out.println(user.getNick() + " not allowed");
					continue;
				}
				else {
					//System.out.println(user.getNick() + " is allowed");
					validPerm++;
				}
				
				// matched, stop.
				if (checkSource && checkPerms) {
					//System.out.println("checkSource && checkPerms = true" + "\n");
					objArray = array;
					break;
				}
			}
			
			MessageCommand msgc = new MessageCommand(user, target, msg);
			
			// objArray still null, means that nothing matched.
			if (objArray == null) {
				String notInScope = trigger + " is not in scope - " + source;
				String notAllowed = "Not enough permissions for user to execute "+ trigger + " on " + target;
				
				//the highest value here will 'own' the errorMsg.
				//ex: (possible conditions)
				
				//validSource = 0 -> Not in scope. (validPerm will be 0 too).
				//validSource >= validPerm -> Surelly not allowed.
				
				if (validSource == 0)
					errorMsg = notInScope;
				else if (validSource >= validPerm) {					
					ReplacerEnvironment env = new ReplacerEnvironment(
							SiteBot.GLOBAL_ENV);
					env.add("ircnick", user.getNick());
					
					if (SiteBot.isPrivate(target))
						target = user.getNick();
					
					System.out.println("Trying to send ident.denymsg to target: " + target);
					getSiteBot().say(target, ReplacerUtils.jprintf(
							"ident.denymsg", env, SiteBot.class));
					
					errorMsg = notAllowed;
				}				
				logger.debug(errorMsg);
				return;
			}
			
			try {
				Method m = (Method) objArray[IRCCommand.METHOD];
				IRCCommand c = (IRCCommand) objArray[IRCCommand.INSTANCE];
				Object[] o = new Object[] { args, msgc };
				
				ArrayList<String> list = (ArrayList<String>) m.invoke(c, o);
				
				if (list == null || list.isEmpty()) {
					logger
					.debug("There is no direct output to return for command "
							+ trigger
							+ " by "
							+ user.getNick());
				} else {
					for (String output : list) {
						if (dest.equals("public"))
							getSiteBot().sayGlobal(output);
						else
							getSiteBot().say(dest, output);
					}
				}
			} catch (Exception e) {
				logger.error(
						"Error in method invocation on IRCCommand "
						+ trigger, e);
				getSiteBot().say(target, e.getMessage());
			}
		}
	}
	
	public void doAutoJoin() {
		ArrayList<Channel> chans = new ArrayList<Channel>(getSiteBot().getChannels());
		for (int i = 0; i < getSiteBot().getChannels().size(); i++) {
			Channel c = chans.get(i);
			getIRCConnection().doJoin(c.getName(),c.getKey());
		}
	}
	
	public void onRegistered() {
		logger.info("Performing auto-join...");
		getSiteBot().resetRetry();
		getSiteBot().perform();
		
		// already connected.
		getSiteBot().setAutoReconnect(false);		
		partAllChannels();
		
		doAutoJoin();
	}
	
	public void onJoin(String chan, IRCUser user) {
		handleJoinPart(user, chan, true);
	}
	
	public void onKick(String chan, IRCUser user, String nickPass, String msg) {
		// nickpass = nick of who have been kicked.
		handleJoinPart(nickPass, chan, false);
	}
	
	public void onMode(String chan, IRCUser user, IRCModeParser modeParser) {
		handleMode(user, modeParser, chan);		
	}
	
	public void onPart(String chan, IRCUser user, String msg) {
		handleJoinPart(user, chan, false);
	}
	
	public void onPrivmsg(String target, IRCUser user, String msg) {
		handlePrivMsg(target, user, msg);
	}
	
	public void onReply(int num, String value, String msg) {
		if (num == IRCUtil.RPL_WHOISUSER) {
			handleWhois(value, msg);
		}
	}
	
	public void onError(int num, String msg) {
		if (num == 433) {
			// handle 'nick already exists'
			getIRCConnection().doNick(getBotNick() + new Random().nextInt(10));
		}
	}
	
	public void onDisconnected() {
		getSiteBot().getIRCConnection().close();
		getSiteBot().setAutoReconnect(true);		
		partAllChannels();
	}
	
	/**
	 * Make sure no channels are marked as joined when they shouldn't be.
	 */
	public void partAllChannels() {
		for (Channel chan : getSiteBot().getChannels()) {
			chan.setPart();
			chan.setOp(false);
		}
	}
}
