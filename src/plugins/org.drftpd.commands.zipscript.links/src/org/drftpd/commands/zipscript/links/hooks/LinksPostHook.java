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
package org.drftpd.commands.zipscript.links.hooks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.commands.zipscript.links.LinkUtils;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author djb61
 * @version $Id$
 */
public class LinksPostHook implements PostHookInterface {

	private static final Logger logger = Logger.getLogger(LinksPostHook.class);
	
	private ResourceBundle _bundle;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
	}

	public void doLinksSTORIncompleteHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort link
			return;
		}
		FileHandle transferFile;
		try {
			transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}
		String transferFileName = transferFile.getName();
		if (transferFileName.toLowerCase().endsWith(".sfv")) {
			LinkUtils.processLink(request, "create", _bundle);
		}
		ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
		try {
			SFVStatus sfvStatus = sfvData.getSFVStatus();
			if (sfvStatus.isFinished()) {
				// dir is complete, remove link
				LinkUtils.processLink(request, "delete", _bundle);
			}
		} catch (NoAvailableSlaveException e) {
			// Slave holding sfv is unavailable
		} catch (FileNotFoundException e) {
			// No sfv in dir
		} catch (IOException e) {
			// sfv not readable
		} catch (SlaveUnavailableException e) {
			// Slave holding sfv is unavailable
		}
		return;
	}

	public void doLinksDELECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// DELE failed, abort cleanup
			return;
		}
		String deleFileName;
		try {
			deleFileName = response.getObject(Dir.FILENAME);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}

		if (deleFileName.endsWith(".sfv")) {
			LinkUtils.processLink(request, "delete", _bundle);
		}
		else {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(request.getCurrentDirectory());
			try {
				SFVStatus sfvStatus = sfvData.getSFVStatus();
				if (!sfvStatus.isFinished()) {
					// dir is now incomplete, add link
					LinkUtils.processLink(request, "create", _bundle);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e) {
				// No sfv in dir
			} catch (IOException e) {
				// sfv not readable
			} catch (SlaveUnavailableException e) {
				// Slave holding sfv is unavailable
			}
		}
		return;
	}

	public void doLinksWIPECleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 200) {
			// WIPE failed, abort cleanup
			return;
		}
		String arg = request.getArgument();
		if (arg.startsWith("-r ")) {
			arg = arg.substring(3);
		}

		arg = VirtualFileSystem.fixPath(arg);
		
		DirectoryHandle wipeDir = request.getCurrentDirectory().getNonExistentDirectoryHandle(arg).getParent();
		if (!wipeDir.exists()) {
			return;
		}

		try {
			for (LinkHandle link : wipeDir.getLinksUnchecked()) {
				try {
					link.getTargetDirectoryUnchecked();
				} catch (FileNotFoundException e1) {
					// Link target no longer exists, remove it
					link.deleteUnchecked();
				} catch (ObjectNotValidException e1) {
					// Link target isn't a directory, delete the link as it is bad
					link.deleteUnchecked();
				}
			}
		} catch (FileNotFoundException e2) {
			logger.warn("Invalid Dir " + wipeDir.getPath(),e2);
		}
		
		// Have to check parent too to allow for the case of wiping a special subdir
		if (!wipeDir.isRoot()) {
			try {
				for (LinkHandle link : wipeDir.getParent().getLinksUnchecked()) {
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remove it
						link.deleteUnchecked();
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						link.deleteUnchecked();
					}
				}
			} catch (FileNotFoundException e2) {
				logger.warn("Invalid link in dir " + wipeDir.getParent().getPath(),e2);
			}
		}

		//check if arguement was a file
		DirectoryHandle oldrequestdir = request.getCurrentDirectory();
		if (arg.endsWith(".sfv")) {
			request.setCurrentDirectory(wipeDir);
			LinkUtils.processLink(request, "delete", _bundle);
			request.setCurrentDirectory(oldrequestdir);
		} else {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(wipeDir);
			try {
				SFVStatus sfvStatus = sfvData.getSFVStatus();
				if (!sfvStatus.isFinished()) {
					// dir is now incomplete, add link
					request.setCurrentDirectory(wipeDir);
					LinkUtils.processLink(request, "create", _bundle);
					request.setCurrentDirectory(oldrequestdir);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e) {
				// No sfv in dir
			} catch (IOException e) {
				// sfv not readable
			} catch (SlaveUnavailableException e) {
				// Slave holding sfv is unavailable
			}
		}
	}
	
	public void doLinksRNTOCleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// RNTO failed, abort cleanup
			return;
		}
		
		InodeHandle fromInode = request.getSession().getObject(Dir.RENAMEFROM, null);
		if ((fromInode == null) || (!fromInode.isDirectory())) {
			// RNFR Failed || inode is not a directory
			return;
		}	
		
		InodeHandle toInode = request.getSession().getObject(Dir.RENAMETO, null);
		if ((toInode == null) || (!toInode.isDirectory())) {
			// RNFR Failed || inode is not a directory
			return;
		}	

		DirectoryHandle fromDir = (DirectoryHandle) fromInode;
		DirectoryHandle toDir = (DirectoryHandle) toInode;
		
		try {
			for (LinkHandle link :  fromDir.getLinksUnchecked()) {
				try {
					link.getTargetDirectoryUnchecked();
				} catch (FileNotFoundException e1) {
					// Link target no longer exists, remove it
					link.deleteUnchecked();
					request.setCurrentDirectory(toDir);
					LinkUtils.processLink(request, "create", _bundle);
					
				} catch (ObjectNotValidException e1) {
					// Link target isn't a directory, delete the link as it is bad
					link.deleteUnchecked();
				}
			}
		} catch (FileNotFoundException e2) {
			//ignore - dir probably doesn't exist anymore as it was moved
		}
		// Have to check parent too to allow for the case of moving a special subdir
		if (!fromDir.isRoot()) {
			try {
				for (LinkHandle link : fromDir.getParent().getLinksUnchecked()) {
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remove it
						link.deleteUnchecked();
						request.setCurrentDirectory(toDir);
						LinkUtils.processLink(request, "create", _bundle);
						
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						link.deleteUnchecked();
					}
				}
			} catch (FileNotFoundException e2) {
				logger.warn("Invalid link in dir " + fromDir.getParent().getPath(),e2);
			}
		}
	}
}
