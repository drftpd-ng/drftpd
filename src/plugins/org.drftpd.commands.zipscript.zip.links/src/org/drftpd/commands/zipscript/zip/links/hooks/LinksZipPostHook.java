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
package org.drftpd.commands.zipscript.zip.links.hooks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ResourceBundle;

import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.links.LinkUtils;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.commands.zipscript.zip.DizStatus;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author CyBeR
 * @version $Id: LinksZipPostHook.java 2076 2010-09-19 18:31:19Z djb61 $
 */
public class LinksZipPostHook implements PostHookInterface {

	private ResourceBundle _bundle;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
	}

	public void doLinksZipSTORIncompleteHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort link
			return;
		}

		ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(request.getCurrentDirectory());
		try {
			DizStatus dizStatus = zipData.getDizStatus();
			if (dizStatus.isFinished()) {
				// dir is complete, remove link
				LinkUtils.processLink(request, "delete", _bundle);
			} else {
				LinkUtils.processLink(request, "create", _bundle);
			}
		} catch (NoAvailableSlaveException e) {
			// Slave holding zip is unavailable
		} catch (FileNotFoundException e) {
			// No zip in dir
		} catch (IOException e) {
			// zip not readable
		}
		return;
	}

	public void doLinksZipDELECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// DELE failed, abort cleanup
			return;
		}

		ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(request.getCurrentDirectory());
		try {
			DizStatus dizStatus = zipData.getDizStatus();
			if (!dizStatus.isFinished()) {
				// dir is now incomplete, add link
				LinkUtils.processLink(request, "create", _bundle);
			}
		} catch (NoAvailableSlaveException e) {
			// Slave holding zip is unavailable
		} catch (FileNotFoundException e) {
			// No zip in dir
			// We have to make sure there is a .sfv in this dir before deleting
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
				sfvData.getSFVStatus();
			} catch (NoAvailableSlaveException e1) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e1) {
				// No SFV in dir - now we can delete link
				LinkUtils.processLink(request, "delete", _bundle);
			} catch (IOException e1) {
				// SFV not readable
			} catch (SlaveUnavailableException e1) {
				// No Slave with SFV available
			}			
		} catch (IOException e) {
			// zip not readable
		}
		return;
	}
	
	
	public void doLinksZipWIPECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 200) {
			// WIPE failed, abort cleanup
			return;
		}
		String arg = request.getArgument();
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
		}
		if (arg.endsWith(VirtualFileSystem.separator)) {
			arg.substring(0,arg.length()-1);
		}
		DirectoryHandle wipeDir = request.getCurrentDirectory().getNonExistentDirectoryHandle(arg).getParent();
		if (!wipeDir.exists()) {
			return;
		}
		DirectoryHandle oldrequestdir = request.getCurrentDirectory();
		ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(wipeDir);
		try {
			DizStatus dizStatus = zipData.getDizStatus();
			if (!dizStatus.isFinished()) {
				// dir is now incomplete, add link
				request.setCurrentDirectory(wipeDir);
				LinkUtils.processLink(request, "create", _bundle);
				request.setCurrentDirectory(oldrequestdir);
			}
		} catch (NoAvailableSlaveException e) {
			// Slave holding zip is unavailable
		} catch (FileNotFoundException e) {
			// No zip in dir
			// We have to make sure there is a .sfv in this dir before deleting
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(wipeDir);
				sfvData.getSFVStatus();
			} catch (NoAvailableSlaveException e1) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e1) {
				// No SFV in dir - now we can delete link
				request.setCurrentDirectory(wipeDir);
				LinkUtils.processLink(request, "delete", _bundle);
				request.setCurrentDirectory(oldrequestdir);
			} catch (IOException e1) {
				// SFV not readable
			} catch (SlaveUnavailableException e1) {
				// No Slave with SFV available
			}			
		} catch (IOException e) {
			// zip not readable
		}
	}
}
