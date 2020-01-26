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
package org.drftpd.commands.usermanagement.expireduser;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.Date;
import java.util.ResourceBundle;

/**
 * @author Scitz0
 */
public class ExpiredUserPostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(ExpiredUserPostHook.class);
	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(StandardCommandManager manager) {
		_bundle = manager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public void doExpiredUserPostHook(CommandRequest request, CommandResponse response) {
		User myUser;
		try {
			myUser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByNameUnchecked(request.getArgument());
		} catch (NoSuchUserException ex) {
			return;
		} catch (UserFileException ex) {
			return;
		}
		try {
			// Test if metadata exist for user and if so add to response
			Date expiredate = myUser.getKeyedMap().getObject(ExpiredUserData.EXPIRES);
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("expiredate", expiredate);
			response.addComment(request.getSession().jprintf(_bundle,
					_keyPrefix+"expireduser", env, myUser.getName()));
		} catch (KeyNotFoundException e) {
			// ignore
		}
	}
}