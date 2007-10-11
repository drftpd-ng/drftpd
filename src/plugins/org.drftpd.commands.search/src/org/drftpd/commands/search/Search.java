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
package org.drftpd.commands.search;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;


/**
 * @author mog
 * @author fr0w
 * @version $Id$
 */
public class Search extends CommandInterface {
	private static final Logger logger = Logger.getLogger(Search.class);
	
    private static void findFile(CommandResponse response, DirectoryHandle dir, ArrayList<String> searchStrings, User user, boolean files, boolean dirs) 
    	throws FileNotFoundException {

    	for (InodeHandle inode : dir.getInodeHandles(user)) {    		
            if (inode.isDirectory()) { //recursive search.            	
                findFile(response, (DirectoryHandle) inode, searchStrings, user, files, dirs);
            }

            boolean found = false; // false if the inode does not match, true if it does.
            boolean matchesAll = true; // if searching for more than one string, this should be true
            							// otherwise the inode didn't match all patterns.

            if ((dirs && inode.isDirectory()) || (files && inode.isFile())) {
                for (String search : searchStrings) {
                    if (response.size() >= 100) {
                        return;
                    }

                    if (inode.getName().toLowerCase().indexOf(search) != -1)
                        found = true;
                    else
                    	matchesAll = false;
                }

                if (found && matchesAll) {
                    response.addComment(inode.getPath());

                    if (response.size() >= 100) {
                        response.addComment("<snip>");
                        return;
                    }
                }
            }
        }
    }

	public CommandResponse search(CommandRequest request, boolean search, boolean dupe) throws ImproperUsageException {
        if (!request.hasArgument()) {
        	throw new ImproperUsageException();
        }
        
        ArrayList<String> searchStrings = new ArrayList<String>();
        StringTokenizer st = new StringTokenizer(request.getArgument().toLowerCase());
        
        while (st.hasMoreTokens())
        	searchStrings.add(st.nextToken());
        
        searchStrings.trimToSize();
        
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK", 
        		request.getCurrentDirectory(), request.getUser());
        
        try {
			User u = request.getUserObject();
	        findFile(response, request.getCurrentDirectory(), searchStrings, u, dupe, search);
		} catch (FileNotFoundException e) {
			logger.error("The directory was just there, how come it vanished?", e);
		} catch (Exception e) {
			logger.fatal("Problems while fetching data from user", e);
		}
        
        return response;
	}
	
	public CommandResponse doSEARCH(CommandRequest request) throws ImproperUsageException {
		return search(request, true, false);
	}
	
	public CommandResponse doDUPE(CommandRequest request) throws ImproperUsageException {
		return search(request, false, true);
	}
}

