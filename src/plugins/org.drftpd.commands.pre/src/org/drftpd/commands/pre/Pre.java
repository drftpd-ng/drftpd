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
package org.drftpd.commands.pre;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.PreEvent;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.plugins.stats.StatsManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public class Pre extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(Pre.class);

    public static final Key<DirectoryHandle> PREDIR = new Key<>(Pre.class, "predir");

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    }

	public CommandResponse doSITE_PRE(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		
		String[] args = request.getArgument().split(" ");

        if (args.length != 2) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }
		
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(args[1]);

        if (section.getName().equals("")) {
            return new CommandResponse(500, "Invalid section, see SITE SECTIONS for a list of available sections");
        }

		User user = request.getSession().getUserNull(request.getUser());
		
        DirectoryHandle preDir;
		
		String path = VirtualFileSystem.fixPath(args[0]);
		if (!(path.startsWith(VirtualFileSystem.separator))) {
			// Not a full path, let's make it one
			if (request.getCurrentDirectory().isRoot()) {
				path = VirtualFileSystem.separator + path;
			} else {
				path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + path;
			}
		}
		
		try {
			preDir = request.getCurrentDirectory().getDirectory(path, user);
		} catch (FileNotFoundException e) {
			return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
		} catch (ObjectNotValidException e) {
			return StandardCommandManager.genericResponse("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS");
		}

        ConfigInterface config = GlobalContext.getConfig();
		if (!config.checkPathPermission("pre", user, preDir)) {
			return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
		}
		
		DirectoryHandle toInode = new DirectoryHandle(section.getCurrentDirectory().getPath() + VirtualFileSystem.separator + preDir.getName());

		if (toInode.exists()) {
			return new CommandResponse(500, "Directory already exist in target section");
		}

		CommandResponse response = new CommandResponse(250, request.getCommand().toUpperCase() + " command successful.");
		
		//AWARD CREDITS
        HashMap<User,Long> awards = new HashMap<>();
		preAwardCredits(preDir, awards);

        for (Map.Entry<User,Long> entry : awards.entrySet()) {
            User owner = entry.getKey();
            if (StatsManager.getStatsManager().getCreditCheckRatio(preDir, owner) == 0) {
                Long award = entry.getValue();
                owner.updateCredits(award);
                response.addComment("Awarded " + Bytes.formatBytes(award) + " to " + owner.getName());
            }
        }
		
		recursiveRemoveOwnership(preDir, System.currentTimeMillis());
		
		int files = getFiles(preDir);
		long bytes = 0;
		try {
			bytes = preDir.getSize();
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException ", e);
		}	

		try {
			preDir.renameToUnchecked(toInode);
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException on renameTo()", e);
			return new CommandResponse(500, "FileNotFound - " + e.getMessage());
		} catch (IOException e) {
			logger.warn("IOException on renameTo()", e);
			return new CommandResponse(500, "IOException - " + e.getMessage());
		}

		preDir = toInode;
		
		GlobalContext.getEventService().publishAsync(new PreEvent(preDir, section, 
				Integer.toString(files), Bytes.formatBytes(bytes)));

        response.setObject(PREDIR, preDir);

		return response;

	}
	
	private static void recursiveRemoveOwnership(DirectoryHandle dir, long lastModified) {
        try {
			dir.setUsername("drftpd");
            dir.setGroup("drftpd");
			dir.setLastModified(lastModified);
	        for (InodeHandle file : dir.getInodeHandlesUnchecked()) {
	            file.setUsername("drftpd");
                file.setGroup("drftpd");
	            file.setLastModified(lastModified);
				if (file.isDirectory())
					recursiveRemoveOwnership((DirectoryHandle) file, lastModified);
	        }
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException on recursiveRemoveOwnership()", e);
		}
    }

	private void preAwardCredits(DirectoryHandle preDir, HashMap<User,Long> awards) {
		try {
	        for (InodeHandle file : preDir.getInodeHandlesUnchecked()) {
	            if (file.isFile()) {
					User owner;

		            try {
		                owner = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(file.getUsername());
		            } catch (NoSuchUserException e) {
		                logger.warn("PRE: Cannot award credits to non-existing user", e);
		                continue;
		            } catch (UserFileException e) {
		                logger.warn("", e);
		                continue;
		            } catch (FileNotFoundException e) {
		                logger.warn("", e);
		                continue;
		            }

		            Long total = awards.get(owner);
		            if (total == null) {
		                total = 0L;
		            }
		            total = (total + (long)(file.getSize() * owner.getKeyedMap().getObjectFloat(UserManagement.RATIO)));
		            awards.put(owner, total);
				}
				
				if (file.isDirectory()) {
					preAwardCredits((DirectoryHandle) file, awards);
				}
	        }
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException on preAwardCredits()", e);
		}
    }
	
	private int getFiles(DirectoryHandle preDir) {
		int files = 0;
		try {
			files = preDir.getFilesUnchecked().size();
			for (DirectoryHandle dir : preDir.getDirectoriesUnchecked()) {
				files += getFiles(dir);
			}
		} catch (FileNotFoundException e) {
			logger.warn("FileNotFoundException ", e);
		}
		return files;
	}

}
