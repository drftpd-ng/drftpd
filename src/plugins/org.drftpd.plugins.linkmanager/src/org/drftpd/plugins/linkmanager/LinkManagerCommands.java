/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.linkmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commandmanager.*;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.LinkedList;

/**
 * @author CyBeR
 * @version $Id: LinkManagerCommands.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class LinkManagerCommands extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(LinkManagerCommands.class);

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
	}

	/*
	 * Used to fix links that are either missing or have been deleted.
	 */
	public CommandResponse doSITE_FIXLINKS(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}

		runFixLinks rfl = new runFixLinks();
		rfl.dir = request.getCurrentDirectory();
		rfl.start();
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		return response;
	}
	
	
	private static class runFixLinks extends Thread {
		public DirectoryHandle dir;
		
		public void run() {
			if (dir != null) {
				LinkManager _linkmanager = LinkManager.getLinkManager();
				LinkedList<DirectoryHandle> dirs = new LinkedList<>();
				dirs.add(dir); 
				while (dirs.size() > 0) {
					DirectoryHandle workingDir = dirs.poll();
					
					for (LinkType link : _linkmanager.getLinks()) {
						link.doFixLink(workingDir);
					}
					
					try {
						dirs.addAll(workingDir.getDirectoriesUnchecked());
					}
					catch (FileNotFoundException e1) {
						// ignore - dir no longer exists
					}
				}
				logger.info("Site Fixlinks - Finished");
			}
		}
	}
	
}
