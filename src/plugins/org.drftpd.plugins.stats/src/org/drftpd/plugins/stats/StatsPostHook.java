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

import java.io.FileNotFoundException;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.slave.TransferStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * @author fr0w
 * @version $Id$
 */
public class StatsPostHook implements PostHookInterface {
	private static final Logger logger = Logger.getLogger(StatsPostHook.class);

	public void initialize(StandardCommandManager manager) {
	}

	public void doRETRPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// Transfer failed, abort update
			return;
		}
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		DirectoryHandle dir = conn.getCurrentDirectory();
		User user = conn.getUserNull(request.getUser());

		// creditloss routine.
		try {
			float ratio = StatsManager.getStatsManager().getCreditLossRatio(dir, user);
			long fileSize = dir.getFileUnchecked(request.getArgument()).getSize();
			long creditsLoss = (long) ratio * fileSize;
		
			user.updateCredits(-creditsLoss);
		} catch (FileNotFoundException e) {
			logger.debug("The file was just here, but it isn't anymore!", e);
		} catch (ObjectNotValidException e) {
			logger.error(e, e);
		}

		// nostatdn routine.
		if (!GlobalContext.getConfig().checkPathPermission("nostatsdn", user, dir)) {
			TransferStatus status = (TransferStatus) response.getObject(DataConnectionHandler.XFER_STATUS, null);
			if (status != null) {
				user.updateDownloadedBytes(status.getTransfered());
				user.updateDownloadedTime(status.getElapsed());
				user.updateDownloadedFiles(1);
			}
		}
		
		user.commit();
	}
	
	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// Transfer failed, abort update
			return;
		}
		BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
		DirectoryHandle dir = conn.getCurrentDirectory();
		User user = conn.getUserNull(request.getUser());

		// creditcheck routine.
		try {
			float ratio = StatsManager.getStatsManager().getCreditCheckRatio(dir, user);
			long fileSize = dir.getFileUnchecked(request.getArgument()).getSize();
			long creditsCheck = (long) ratio * fileSize;
		
			user.updateCredits(creditsCheck);
		} catch (FileNotFoundException e) {
			logger.debug("The file was just here, but it isn't anymore!", e);
		} catch (ObjectNotValidException e) {
			logger.error(e, e);
		}

		// nostatup routine.
		if (!GlobalContext.getConfig().checkPathPermission("nostatsup", user, dir)) {
			TransferStatus status = (TransferStatus) response.getObject(DataConnectionHandler.XFER_STATUS, null);
			if (status != null) {
				conn.getUserNull().updateUploadedBytes(status.getTransfered());
				conn.getUserNull().updateUploadedTime(status.getElapsed());
				conn.getUserNull().updateUploadedFiles(1);
			}
		}
		
		user.commit();
	}
	
	public void doDELEPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// Transfer failed, abort update
			return;
		}
		String userName = (String) response.getObject(Dir.USERNAME, null);
		long fileSize  = response.getObjectLong(Dir.FILESIZE);

		try {
			DirectoryHandle dir = request.getCurrentDirectory();
			User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(userName);
			
			// updating credits
			float ratio = StatsManager.getStatsManager().getCreditCheckRatio(dir, user);
			long creditsCheck = (long) ratio * fileSize;
			user.updateCredits(creditsCheck);
			
			
			// updating stats
			if (!GlobalContext.getConfig().checkPathPermission("nostatsup", user, dir)) {
				user.updateUploadedBytes(-fileSize);
			}

			user.commit();
		} catch (UserFileException e) {
			response.addComment("Error updating credits & stats: "+ e.getMessage());
		} catch (NoSuchUserException e) {
			response.addComment("User " +userName+ " does not exist, cannot remove credits on deletion");
		}
	}

}
