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
package org.drftpd.plugins.stats;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.slave.TransferStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

/**
 * @author fr0w
 * @version $Id$
 */
public class StatsPostHook implements PostHookInterface {

	public void initialize(StandardCommandManager manager) {
	}

	public void doRETRPostHook(CommandRequest request, CommandResponse response) {
		DirectoryHandle dir = request.getCurrentDirectory();
		User user = request.getSession().getUserNull(request.getUser());

		TransferStatus status = response.getObject(DataConnectionHandler.XFER_STATUS, null);
		if (status != null) {
			// creditloss routine.
			float ratio = StatsManager.getStatsManager().getCreditLossRatio(dir, user);
			long transferredSize = status.getTransfered();
			long creditsLoss = (long) ratio * transferredSize;
			user.updateCredits(-creditsLoss);
			
			// nostatdn routine.
			if (!GlobalContext.getConfig().checkPathPermission("nostatsdn", user, dir)) {
				user.updateDownloadedBytes(status.getTransfered());
				user.updateDownloadedTime(status.getElapsed());
				user.updateDownloadedFiles(1);
			}
			
			user.commit();
		}
	}
	
	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
		DirectoryHandle dir = request.getCurrentDirectory();
		User user = request.getSession().getUserNull(request.getUser());

		TransferStatus status = response.getObject(DataConnectionHandler.XFER_STATUS, null);
		FileHandle transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE, null);
		if (status != null && transferFile != null && transferFile.exists()) {
			// creditcheck routine.
			float ratio = StatsManager.getStatsManager().getCreditCheckRatio(dir, user);
			long transferredSize = status.getTransfered();
			long creditsCheck = (long) ratio * transferredSize;
			user.updateCredits(creditsCheck);
			
			// nostatdn routine.
			if (!GlobalContext.getConfig().checkPathPermission("nostatsup", user, dir)) {
				user.updateUploadedBytes(status.getTransfered());
				user.updateUploadedTime(status.getElapsed());
				user.updateUploadedFiles(1);
			}
			
			user.commit();
		}
	}
	
	public void doDELEPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// Delete failed, abort update
			return;
		}

		if (!response.getObjectBoolean(Dir.ISFILE)) {
			return;
		}

		String userName = response.getObject(Dir.USERNAME, null);
		long fileSize  = response.getObjectLong(Dir.FILESIZE);
		long xferTime = response.getObjectLong(Dir.XFERTIME);

		try {
			DirectoryHandle dir = request.getCurrentDirectory();
			User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(userName);
			
			// updating credits
			float ratio = StatsManager.getStatsManager().getCreditCheckRatio(dir, user);
			long creditsCheck = (long) ratio * fileSize;
			user.updateCredits(-creditsCheck);
			
			
			// updating stats
			if (!GlobalContext.getConfig().checkPathPermission("nostatsup", user, dir)) {
				user.updateUploadedBytes(-fileSize);
				user.updateUploadedFiles(-1);
				user.updateUploadedTime(-xferTime);
			}

			user.commit();
		} catch (UserFileException e) {
			response.addComment("Error updating credits & stats: "+ e.getMessage());
		} catch (NoSuchUserException e) {
			response.addComment("User " +userName+ " does not exist, cannot remove credits on deletion");
		}
	}

}
