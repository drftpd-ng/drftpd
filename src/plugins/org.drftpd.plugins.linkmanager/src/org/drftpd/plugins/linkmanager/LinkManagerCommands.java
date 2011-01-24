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

import java.io.FileNotFoundException;
import java.util.LinkedList;

import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author CyBeR
 * @version $Id: LinkManagerCommands.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class LinkManagerCommands extends CommandInterface {

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
		
		LinkManager _linkmanager = LinkManager.getLinkManager();
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		LinkedList<DirectoryHandle> dirs = new LinkedList<DirectoryHandle>();
		dirs.add(request.getCurrentDirectory());
		while (dirs.size() > 0) {
			DirectoryHandle workingDir = dirs.poll();
			
			for (LinkType link : _linkmanager.getLinks()) {
				link.doFixLink(workingDir);
			}
			
			try {
				dirs.addAll(workingDir.getDirectoriesUnchecked());
			}
			catch (FileNotFoundException e1) {
				response.addComment("Error recursively listing: "+workingDir.getPath());
			}
			
		}
		return response;
	}
	
	
}
