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

import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.UserManagement;
import org.drftpd.plugins.sitebot.ServiceCommand;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author djb61
 * @version $Id$
 */
public class UserHandler extends CommandInterface {

	private static final Logger logger = Logger.getLogger(UserHandler.class);

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
			logger.warn(username + " " + e.getMessage(), e);
			return null;
		} catch (UserFileException e) {
			logger.warn("Error loading userfile for "+username, e);
			return null;
		}

		if (user.checkPassword(password)) {
			String ident = ((ServiceCommand) request.getSession()).getIdent();
			user.getKeyedMap().setObject(UserManagement.IRCIDENT,ident);
			user.commit();
			logger.info("Set IRC ident to '"+ident+"' for "+user.getName());
			request.getSession().printOutput("Set IRC ident to '"+ident+"' for "+user.getName());
		}
		return null;
	}
}
