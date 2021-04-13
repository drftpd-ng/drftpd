/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.sitebot.commands;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.network.Session;
import org.drftpd.master.sitebot.OutputWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.SiteBotWrapper;
import org.drftpd.master.sitebot.UserDetails;
import org.drftpd.master.sitebot.config.ChannelConfig;
import org.drftpd.master.usermanager.User;

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

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();
    }

    public CommandResponse doBLOWFISH(CommandRequest request) throws ImproperUsageException {
        Session session = request.getSession();
        // We require a secure session
        if (!session.isSecure()) {
            return new CommandResponse(530, session.jprintf(_bundle, "blowfish.reject", request.getUser()));
        }

        // Get our sitebot(s) and inform if we have no bots loaded
        SiteBotWrapper wrapper = getSiteBotWrapper();
        if (wrapper == null) {
            return new CommandResponse(500, "No SiteBots loaded");
        }

        User user = session.getUserNull(request.getUser());

        String botName = wrapper.getBots().get(0).getBotName();
        if (request.hasArgument()) {
            if (wrapper.getBots().size() > 1) {
                StringTokenizer st = new StringTokenizer(request.getArgument());
                if (st.countTokens() != 1) {
                    throw new ImproperUsageException();
                }
                botName = st.nextToken();
            } else {
                throw new ImproperUsageException();
            }
        }

        // Find the requested bot
        SiteBot bot = null;
        for (SiteBot currBot : wrapper.getBots()) {
            String currBotName = currBot.getBotName();
            if (currBotName != null && currBotName.equalsIgnoreCase(botName)) {
                bot = currBot;
                break;
            }
        }

        // Bot is not found or correctly loaded
        if (bot == null) {
            return new CommandResponse(500, "SiteBot could not be found or failed to load");
        }

        ArrayList<String> outputKeys = new ArrayList<>();
        for (ChannelConfig chan : bot.getConfig().getChannels()) {
            if (chan.getBlowKey() != null) {
                if (chan.isPermitted(user) && !chan.getBlowKey().equals("")) {
                    outputKeys.add(chan.getName() + " - " + chan.getBlowMode() + " - " + chan.getBlowKey());
                }
            }
        }
        if (outputKeys.size() == 0) {
            return new CommandResponse(500, "No Blowfish keys found");
        }
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        response.addComment(bot.getBotName() + ":");
        Collections.sort(outputKeys);
        for (String chanKey : outputKeys) {
            response.addComment(chanKey);
        }
        return response;
    }

    public CommandResponse doSETBLOWFISH(CommandRequest request) throws ImproperUsageException {
        // We expect arguments
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        // We require a secure session
        if (!session.isSecure()) {
            return new CommandResponse(530, session.jprintf(_bundle, "blowfish.reject", request.getUser()));
        }

        // Get our sitebot(s) and inform if we have no bots loaded
        SiteBotWrapper wrapper = getSiteBotWrapper();
        if (wrapper == null) {
            return new CommandResponse(500, "No SiteBots loaded");
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        // Which bot (name) are we looking for
        String botName = wrapper.getBots().get(0).getBotName();
        if (wrapper.getBots().size() > 1) {
            // Make sure the bot name has been provided
            if (st.countTokens() < 2) {
                return new CommandResponse(500, "Not enough arguments");
            }
            botName = st.nextToken();
        }

        // Find the requested bot
        SiteBot bot = null;
        for (SiteBot currBot : wrapper.getBots()) {
            String currBotName = currBot.getBotName();
            if (currBotName != null && currBotName.equalsIgnoreCase(botName)) {
                bot = currBot;
                break;
            }
        }

        // Bot is not found or correctly loaded
        if (bot == null) {
            return new CommandResponse(500, "SiteBot could not be found or failed to load");
        }

        String blowKey = st.nextToken();
        User user = session.getUserNull(request.getUser());

        String userKeysString = "";
        try {
            userKeysString = user.getKeyedMap().getObject(UserDetails.BLOWKEY);
        } catch (KeyNotFoundException ignore) {
            // Means this user has never set a blowfish key, is safe to proceed
        }
        String[] userKeys = userKeysString.split(",");
        boolean foundOld = false;
        StringBuilder userKey = new StringBuilder();
        for (int i = 0; i < userKeys.length; i++) {
            // Try to match the bot name include the separator
            if (userKeys[i].startsWith(bot.getBotName()+"|")) {
                userKey.append(bot.getBotName());
                userKey.append("|");
                userKey.append(blowKey);
                foundOld = true;
            } else {
                userKey.append(userKeys[i]);
            }
            // Append the delimiter
            if (i < userKeys.length - 1) {
                userKey.append(",");
            }
        }
        // No entry found to replace, so append it as new
        if (!foundOld) {
            // Make sure we add the delimiter first
            if (userKey.length() > 0) {
                userKey.append(",");
            }
            userKey.append(bot.getBotName());
            userKey.append("|");
            userKey.append(blowKey);
        }
        // Store the new blowfish key data for the user
        user.getKeyedMap().setObject(UserDetails.BLOWKEY, userKey.toString());
        user.commit();
        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doIRC(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }
        SiteBotWrapper wrapper = getSiteBotWrapper();
        if (wrapper == null) {
            return new CommandResponse(500, "No SiteBots loaded");
        }
        StringTokenizer st = new StringTokenizer(request.getArgument());

        // Which bot (name) are we looking for
        String botName = wrapper.getBots().get(0).getBotName();
        if (wrapper.getBots().size() > 1) {
            // Make sure the bot name has been provided
            if (st.countTokens() < 2) {
                return new CommandResponse(500, "Not enough arguments");
            }
            botName = st.nextToken();
        }

        // Find the requested bot
        SiteBot bot = null;
        for (SiteBot currBot : wrapper.getBots()) {
            String currBotName = currBot.getBotName();
            if (currBotName != null && currBotName.equalsIgnoreCase(botName)) {
                bot = currBot;
                break;
            }
        }

        // Bot is not found or correctly loaded
        if (bot == null) {
            return new CommandResponse(500, "SiteBot could not be found or failed to load");
        }

        // Get the command name
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
                logger.warn("Error connecting to IRC server", e);
                return new CommandResponse(500, "Error when connecting to IRC server");
            }
        } else if (command.equalsIgnoreCase("disconnect")) {
            bot.setDisconnected(true);
            if (st.hasMoreTokens()) {
                bot.quitServer("Disconnected by " + request.getUser() + " - " + st.nextToken());
            } else {
                bot.quitServer("Disconnected by " + request.getUser());
            }
        } else if (command.equalsIgnoreCase("reconnect")) {
            if (st.hasMoreTokens()) {
                bot.quitServer("Reconnect requested by " + request.getUser() + " - " + st.nextToken());
            } else {
                bot.quitServer("Reconnect requested by " + request.getUser());
            }
        } else {
            // We require more arguments here, complain if they are missing
            if (!st.hasMoreTokens()) {
                return new CommandResponse(500, "Not enough arguments");
            }
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
            } else if (command.equalsIgnoreCase("raw")) {
                bot.sendRawLine(commandArgs.toString());
            } else {
                // If we get here the command we got is unsupported
                throw new ImproperUsageException();
            }
        }
        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public SiteBotWrapper getSiteBotWrapper() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof SiteBotWrapper) {
                return (SiteBotWrapper) plugin;
            }
        }
        return null;
    }
}
