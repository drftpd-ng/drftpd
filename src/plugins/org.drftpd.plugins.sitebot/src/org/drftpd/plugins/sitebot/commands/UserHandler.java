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
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.plugins.sitebot.ServiceCommand;
import org.drftpd.plugins.sitebot.SiteBotWrapper;
import org.drftpd.plugins.sitebot.event.InviteEvent;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
public class UserHandler extends CommandInterface {

	private static final Logger logger = LogManager.getLogger(UserHandler.class);

	public CommandResponse doIdent(CommandRequest request) throws ImproperUsageException {
		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (st.countTokens() < 2) {
			throw new ImproperUsageException();
		}

		String username = st.nextToken();
		String password = st.nextToken();

		User user;
		try {
			user = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
		} catch (NoSuchUserException e) {
            logger.warn("{} {}", username, e.getMessage(), e);
			return null;
		} catch (UserFileException e) {
            logger.warn("Error loading userfile for {}", username, e);
			return null;
		}

		if (user.checkPassword(password)) {
			ServiceCommand commSession = (ServiceCommand) request.getSession();
			String ident = commSession.getIdent();
			String existIdentString = "";
			try {
				existIdentString = user.getKeyedMap().getObject(UserManagement.IRCIDENT);
			} catch (KeyNotFoundException e) {
				// Means no existing idents at all, safe to proceed
			}
			String[] existIdents = existIdentString.split(",");
			boolean foundOld = false;
			StringBuilder newIdents = new StringBuilder();
			String sourceBot = commSession.getBot().getBotName();
			for (int i = 0; i < existIdents.length;i++) {
				if (existIdents[i].startsWith(sourceBot + "|")) {
					newIdents.append(sourceBot);
					newIdents.append("|");
					newIdents.append(ident);
					foundOld = true;
				} else {
					newIdents.append(existIdents[i]);
				}
				if (i < existIdents.length - 1) {
					newIdents.append(",");
				}
			}
			if (!foundOld) {
				if (newIdents.length() > 0) {
					newIdents.append(",");
				}
				newIdents.append(sourceBot);
				newIdents.append("|");
				newIdents.append(ident);
			}
			user.getKeyedMap().setObject(UserManagement.IRCIDENT,newIdents.toString());
			user.commit();
            logger.info("Set IRC ident to '{}' for {} on bot {}", ident, user.getName(), sourceBot);
			request.getSession().printOutput("Set IRC ident to '"+ident+"' for "+user.getName()+" on bot "+sourceBot);
		}
		return null;
	}

        public CommandResponse doUNIdent(CommandRequest request) throws ImproperUsageException {
                StringTokenizer st = new StringTokenizer(request.getArgument());
                if (st.countTokens() < 1) {
                        throw new ImproperUsageException();
                }

                String username = st.nextToken();

                User user;
                try {
                        user = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
                } catch (NoSuchUserException e) {
                    logger.warn("{} {}", username, e.getMessage(), e);
                        return null;
                } catch (UserFileException e) {
                    logger.warn("Error loading userfile for {}", username, e);
                        return null;
                }

				user.getKeyedMap().setObject(UserManagement.IRCIDENT, "");
				user.commit();
            logger.info("Unset IRC ident for {}", user.getName());
				request.getSession().printOutput("Unset IRC ident for "+user.getName()+"");
                return null;
        }


	public CommandResponse doInvite(CommandRequest request) throws ImproperUsageException {
		StringTokenizer st = new StringTokenizer(request.getArgument());
		if (st.countTokens() < 2) {
			throw new ImproperUsageException();
		}

		String username = st.nextToken();
		String password = st.nextToken();

		User user;
		try {
			user = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
		} catch (NoSuchUserException e) {
            logger.warn("{} {}", username, e.getMessage(), e);
			return null;
		} catch (UserFileException e) {
            logger.warn("Error loading userfile for {}", username, e);
			return null;
		}
		ServiceCommand session = (ServiceCommand) request.getSession();
		boolean success = user.checkPassword(password);
		GlobalContext.getEventService().publish(
				new InviteEvent(success ? "INVITE" : "BINVITE", session.getIrcUser().getNick(), user,
						session.getBot().getBotName()));

		if (success) {
			String ident = session.getIdent();
			String existIdentString = "";
			try {
				existIdentString = user.getKeyedMap().getObject(UserManagement.IRCIDENT);
			} catch (KeyNotFoundException e) {
				// Means no existing idents at all, safe to proceed
			}
			boolean foundOld = false;
			String[] existIdents = existIdentString.split(",");
			StringBuilder newIdents = new StringBuilder();
			String sourceBot = session.getBot().getBotName();
			for (int i = 0; i < existIdents.length;i++) {
				if (existIdents[i].startsWith(sourceBot + "|")) {
					newIdents.append(sourceBot);
					newIdents.append("|");
					newIdents.append(ident);
					foundOld = true;
				} else {
					newIdents.append(existIdents[i]);
				}
				if (i < existIdents.length - 1) {
					newIdents.append(",");
				}
			}
			if (!foundOld) {
				if (newIdents.length() > 0) {
					newIdents.append(",");
				}
				newIdents.append(sourceBot);
				newIdents.append("|");
				newIdents.append(ident);
			}
            logger.info("Invited \"{}\" as user {}", session.getIdent(), user.getName());
			user.getKeyedMap().setObject(UserManagement.IRCIDENT,newIdents.toString());
			user.commit();
		} else {
            logger.warn("{} attempted invite with bad password: {} {}", session.getIrcUser().getNick(), request.getCommand(), request.getArgument());
		}
		return null;
	}

	public CommandResponse doSITE_INVITE(CommandRequest request) throws ImproperUsageException {
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
		
		String botname = null;
		if (st.countTokens() >= 2) {
			botname = st.nextToken();
		} else {
			botname = wrapper.getBots().get(0).getBotName();
		}
		String nickname = st.nextToken();
		if (multipleBots) {
            logger.info("Inviting {} with nickname {} using bot {}", request.getUser(), nickname, botname);
			GlobalContext.getEventService().publish(
					new InviteEvent("INVITE",nickname,request.getSession().getUserNull(request.getUser()),botname));
			return new CommandResponse(200, "Inviting "+nickname+" with bot "+botname);
		}
        logger.info("Inviting {} with nickname {}", request.getUser(), nickname);
		GlobalContext.getEventService().publish(
				new InviteEvent("INVITE",nickname,request.getSession().getUserNull(request.getUser()),botname));
		return new CommandResponse(200, "Inviting "+nickname);
	}
}
