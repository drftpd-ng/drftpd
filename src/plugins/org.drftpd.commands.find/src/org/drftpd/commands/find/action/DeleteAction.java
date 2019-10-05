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
package org.drftpd.commands.find.action;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.UserManagement;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;

/**
 * @author scitz0
 * @version $Id$
 */
public class DeleteAction implements ActionInterface {
	public static final Logger logger = LogManager.getLogger(DeleteAction.class);

	private boolean _failed;

	@Override
	public void initialize(String action, String[] args) throws ImproperUsageException {
	}

	private String doDELE(CommandRequest request, InodeHandle inode) {
		String reply = "";
		try {
			// check permission
			User user = request.getSession().getUserNull(request.getUser());
			FileHandle file = (FileHandle) inode;

			try {
				file.delete(user);
			} catch (PermissionDeniedException e) {
				_failed = true;
				return "Access denied for " + file.getPath();
			}

			reply = "Deleted " + file.getPath();

			User uploader = GlobalContext.getGlobalContext().getUserManager().getUserByName(file.getUsername());
			uploader.updateCredits((long) -(file.getSize() * uploader.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
		} catch (UserFileException e) {
			reply += " - Error removing credits: " + e.getMessage();
			_failed = true;
		} catch (NoSuchUserException e) {
			reply += " - Error removing credits: " + e.getMessage();
			_failed = true;
		} catch (FileNotFoundException e) {
			logger.error("The file was there and now it's gone, how?", e);
		}

		return reply;
	}

	@Override
	public boolean execInDirs() {
		return false;
	}

	@Override
	public boolean execInFiles() {
		return true;
	}

	@Override
	public boolean failed() {
		return _failed;
	}

	@Override
	public String exec(CommandRequest request, InodeHandle file) {
		return doDELE(request, file);
	}
}
