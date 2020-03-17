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
package org.drftpd.commands.sitemanagement;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.plugins.util.PluginManager;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.plugins.commandmanager.CommandInterface;
import org.drftpd.plugins.commandmanager.CommandRequest;
import org.drftpd.plugins.commandmanager.CommandResponse;
import org.drftpd.plugins.commandmanager.StandardCommandManager;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SiteManagementHandler extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(SiteManagementHandler.class);

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
	}

	public CommandResponse doSITE_LIST(CommandRequest request) {

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		DirectoryHandle dir = request.getCurrentDirectory();
		InodeHandle target;
		User user = request.getSession().getUserNull(request.getUser());

		if (request.hasArgument()) {
			try {
				target = dir.getInodeHandle(request.getArgument(), user);
			} catch (FileNotFoundException e) {
				logger.debug("FileNotFound", e);
				return new CommandResponse(200, e.getMessage());
			}
		} else {
			target = dir;
		}

		List<InodeHandle> inodes;
		try {
			if (target.isFile()) {
				inodes = Collections.singletonList(dir);
			} else {
				inodes = new ArrayList<>(dir.getInodeHandles(user));
			}
			Collections.sort(inodes);

			for (InodeHandle inode : inodes) {
				response.addComment(inode.toString());
			}
		} catch (FileNotFoundException e) {
			logger.debug("FileNotFound", e);
			return new CommandResponse(200, e.getMessage());
		}
		return response;
	}

	public CommandResponse doSITE_RELOAD(CommandRequest request) {

		try {
			GlobalContext.getGlobalContext().getSectionManager().reload();
			GlobalContext.getGlobalContext().reloadFtpConfig();
			GlobalContext.getGlobalContext().loadPluginsConfig();
			GlobalContext.getGlobalContext().getSlaveSelectionManager().reload();

			 // GlobalContext.getEventService().publish(new ReloadEvent(CommonPluginUtils.getPluginIdForObject(this)));

		} catch (IOException e) {
			logger.log(Level.FATAL, "Error reloading config", e);

			return new CommandResponse(200, e.getMessage());
		}

		// Clear base system classloader also
		ResourceBundle.clearCache(ClassLoader.getSystemClassLoader());
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_SHUTDOWN(CommandRequest request) {

		String message;

		if (!request.hasArgument()) {
			message = "Service shutdown issued by "
				+ request.getUser();
		} else {
			message = request.getArgument();
		}

		GlobalContext.getGlobalContext().shutdown(message);

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}
}
