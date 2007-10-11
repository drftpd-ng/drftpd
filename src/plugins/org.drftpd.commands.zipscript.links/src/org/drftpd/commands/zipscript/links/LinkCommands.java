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
package org.drftpd.commands.zipscript.links;

import java.io.FileNotFoundException;
import java.util.LinkedList;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * @author djb61
 * @version $Id$
 */
public class LinkCommands extends CommandInterface {

	private static final Logger logger = Logger.getLogger(LinkCommands.class);

	private ResourceBundle _bundle;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
	}

	public CommandResponse doSITE_FIXLINKS(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		LinkedList<DirectoryHandle> dirs = new LinkedList<DirectoryHandle>();
		dirs.add(request.getCurrentDirectory());
		while (dirs.size() > 0) {
			DirectoryHandle workingDir = dirs.poll();
			try {
				for (LinkHandle link : workingDir.getLinks()) {
					try {
						link.getTargetDirectory().getPath();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remote it
						link.deleteUnchecked();
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						link.deleteUnchecked();
						continue;
					}
				}
			} catch (FileNotFoundException e2) {
				logger.warn("Invalid link in dir " + workingDir.getPath(),e2);
			}
			try {
				dirs.addAll(workingDir.getDirectoriesUnchecked());
			}
			catch (FileNotFoundException e1) {
				response.addComment("Error recursively listing: "+workingDir.getPath());
			}
			Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf");
			// Check if incomplete links are enabled
			if (cfg.getProperty("incomplete.links").equals("true")) {
				// check incomplete status and update links
				CommandRequest tempReq = (CommandRequest) request.clone();
				tempReq.setCurrentDirectory(workingDir);
				try {
					ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDir);
					SFVStatus sfvStatus = sfvData.getSFVStatus();
					if (sfvStatus.isFinished()) {
						// dir is complete, remove link if needed
						LinkUtils.processLink(tempReq, "delete", _bundle);
					}
					else {
						// dir is incomplete, add link if needed
						LinkUtils.processLink(tempReq, "create", _bundle);
					}
				} catch (NoAvailableSlaveException e) {
					// Slave holding sfv is unavailable
				} catch (FileNotFoundException e) {
					// No sfv in dir
				}
			}
		}
		return response;
	}
}
