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
package org.drftpd.plugins.sitebot.commands;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.commandmanager.*;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.master.Session;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.SiteBotWrapper;
import org.drftpd.plugins.sitebot.UserDetails;
import org.drftpd.plugins.sitebot.config.ChannelConfig;
import org.drftpd.usermanager.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotManagement extends CommandInterface {

	private static final Logger logger = LogManager.getLogger(SiteBotManagement.class);

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
    	_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSITE_BLOWFISH(CommandRequest request) {
		Session session = request.getSession();
		if (!session.isSecure()) {
			return new CommandResponse(530,
					session.jprintf(_bundle, _keyPrefix+"blowfish.reject", request.getUser()));
		}
		SiteBotWrapper wrapper = null;
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof SiteBotWrapper) {
				wrapper = (SiteBotWrapper) plugin;
				break;
			}
		}
		if (wrapper == null) {
			return new CommandResponse(500, "No SiteBots loaded");
		}
		String botName = null;
		if (request.hasArgument()) {
			StringTokenizer st = new StringTokenizer(request.getArgument());
			botName = st.nextToken();
		} else {
			botName = wrapper.getBots().get(0).getBotName();
		}
		User user = session.getUserNull(request.getUser());
		SiteBot bot = null;
		for (SiteBot currBot : wrapper.getBots()) {
			if (currBot.getBotName().equalsIgnoreCase(botName)) {
				bot = currBot;
				break;
			}
		}
		if (bot == null) {
			return new CommandResponse(500, "No Blowfish keys found");
		}
		ArrayList<String> outputKeys = new ArrayList<>();
		for (ChannelConfig chan : bot.getConfig().getChannels()) {
			if (chan.getBlowKey() != null) {
				if (chan.isPermitted(user) && !chan.getBlowKey().equals("")) {
					outputKeys.add(chan.getName()+" - "+chan.getBlowKey());
				}
			}
		}
		if (outputKeys.size() == 0) {
			return new CommandResponse(500, "No Blowfish keys found");
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment(bot.getBotName()+":");
		Collections.sort(outputKeys);
		for (String chanKey : outputKeys) {
			response.addComment(chanKey);
		}
		return response;
	}

	public CommandResponse doSITE_SETBLOWFISH(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		Session session = request.getSession();
		if (!session.isSecure()) {
			return new CommandResponse(530,
					session.jprintf(_bundle, _keyPrefix+"blowfish.reject", request.getUser()));
		}
		SiteBotWrapper wrapper = null;
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof SiteBotWrapper) {
				wrapper = (SiteBotWrapper) plugin;
				break;
			}
		}
		if (wrapper == null) {
			return new CommandResponse(500, "No SiteBots loaded");
		}
		StringTokenizer st = new StringTokenizer(request.getArgument());
		String botName = null;
		if (st.countTokens() > 1) {
			botName = st.nextToken();
		} else {
			botName = wrapper.getBots().get(0).getBotName();
		}
		String blowKey = st.nextToken();
		User user = session.getUserNull(request.getUser());
		SiteBot bot = null;
		for (SiteBot currBot : wrapper.getBots()) {
			if (currBot.getBotName().equalsIgnoreCase(botName)) {
				bot = currBot;
				break;
			}
		}
		if (bot == null) {
			return new CommandResponse(500, "SiteBot could not be found");
		}
		String userKeysString = "";
		try {
			userKeysString = user.getKeyedMap().getObject(UserDetails.BLOWKEY);
		}
		catch (KeyNotFoundException e1) {
			// Means this user has never set a blowfish key, is safe to proceed
		}
		String[] userKeys = userKeysString.split(",");
		boolean foundOld = false;
		StringBuilder userKey = new StringBuilder();
		for (int i = 0; i < userKeys.length;i++) {
			if (userKeys[i].startsWith(bot.getBotName())) {
				userKey.append(bot.getBotName());
				userKey.append("|");
				userKey.append(blowKey);
				foundOld = true;
			} else {
				userKey.append(userKeys[i]);
			}
			if (i < userKeys.length - 1) {
				userKey.append(",");
			}
		}
		if (!foundOld) {
			if (userKey.length() > 0) {
				userKey.append(",");
			}
			userKey.append(bot.getBotName());
			userKey.append("|");
			userKey.append(blowKey);
		}
		user.getKeyedMap().setObject(UserDetails.BLOWKEY, userKey.toString());
		user.commit();
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_IRC(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		SiteBotWrapper wrapper = null;
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof SiteBotWrapper) {
				wrapper = (SiteBotWrapper) plugin;
				break;
			}
		}
		if (wrapper == null) {
			return new CommandResponse(500, "No SiteBots loaded");
		}
		boolean multipleBots = wrapper.getBots().size() > 1;
		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (multipleBots && st.countTokens() < 2) {
			return new CommandResponse(500, "Not enough arguments");
		}
		String botname = null;
		if (multipleBots) {
			botname = st.nextToken();
		} else {
			botname = wrapper.getBots().get(0).getBotName();
		}
		SiteBot bot = null;
		for (SiteBot currBot : wrapper.getBots()) {
			if (currBot.getBotName().equalsIgnoreCase(botname)) {
				bot = currBot;
				break;
			}
		}
		if (bot == null) {
			return new CommandResponse(500, "SiteBot could not be found");
		}
		String command = st.nextToken();
		if (command.equalsIgnoreCase("connect")) {
			try {
				if (bot.isConnected()) {
					bot.reconnect();
				} else {
					bot.connect();
				}
				bot.setDisconnected(false);
			} catch (Exception e) {
				logger.warn("Error connecting to IRC server",e);
				return new CommandResponse(500, "Error when connecting to IRC server");
			}
		} else {
			if (command.equalsIgnoreCase("disconnect")) {
				bot.setDisconnected(true);
				bot.quitServer("Disconnected by "+request.getUser());
			} else {
				if (command.equalsIgnoreCase("reconnect")) {
					bot.quitServer("Reconnect requested by "+request.getUser());
				} else {
					StringBuilder commandArgs = new StringBuilder();
					while (st.hasMoreTokens()) {
						commandArgs.append(st.nextToken());
						if (st.hasMoreTokens()) {
							commandArgs.append(" ");
						}
					}
					if (command.equalsIgnoreCase("say")) {
						for (OutputWriter writer : bot.getWriters().values()) {
							writer.sendMessage(commandArgs.toString());
						}
					} else {
						if (command.equalsIgnoreCase("raw")) {
							bot.sendRawLine(commandArgs.toString());
						} else {
							throw new ImproperUsageException();
						}
					}
				}
			}
		}
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}
}
