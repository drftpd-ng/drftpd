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
package org.drftpd.commands.request;

import java.io.FileNotFoundException;
import java.io.IOException;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.master.Session;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @version $Id$
 */
public class Request extends CommandInterface {
    public static final Key REQUESTSFILLED = new Key(Request.class,
            "requestsFilled", Integer.class);
    public static final Key REQUESTS = new Key(Request.class, "requests",
            Integer.class);
    private static final String FILLEDPREFIX = "FILLED-for.";
    private static final Logger logger = Logger.getLogger(Request.class);
    private static final String REQPREFIX = "REQUEST-by.";

    public CommandResponse doSITE_REQFILLED(CommandRequest request) {
        if (!request.hasArgument()) {
        	return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        DirectoryHandle currdir = request.getCurrentDirectory();
        String reqname = request.getArgument().trim();
        try {
			for (DirectoryHandle dir : currdir.getDirectories()) {

			    if (!dir.getName().startsWith(REQPREFIX)) {
			        continue;
			    }

			    String username = dir.getName().substring(REQPREFIX.length());
			    String myreqname = username.substring(username.indexOf('-') + 1);
			    username = username.substring(0, username.indexOf('-'));

			    if (myreqname.equals(reqname)) {
			        String filledname = FILLEDPREFIX + username + "-" + myreqname;

			        try {
			            dir.renameTo(currdir.getNonExistentFileHandle(filledname));
			        } catch (IOException e) {
			            logger.warn("", e);
			            return new CommandResponse(200, e.getMessage());
			        }

			        //if (conn.getConfig().checkDirLog(conn.getUserNull(), file)) {
			        GlobalContext.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
			                request.getSession().getUserNull(request.getUser()), "REQFILLED", dir));

			        //}
			        request.getSession().getUserNull(request.getUser()).getKeyedMap().incrementObjectLong(REQUESTSFILLED);

			        return new CommandResponse(200,
			            "OK, renamed " + myreqname + " to " + filledname);
			    }
			}
		} catch (FileNotFoundException e) {
			return new CommandResponse(500, "Current directory does not exist, please CWD /");
		}

        return new CommandResponse(200, "Couldn't find a request named " + reqname);
    }

    public CommandResponse doSITE_REQUEST(CommandRequest request) {
    	Session session = request.getSession();
        if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("request",
                    session.getUserNull(request.getUser()), request.getCurrentDirectory())) {
        	return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        if (!request.hasArgument()) {
        	return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        String createdDirName = REQPREFIX + session.getUserNull(request.getUser()).getName() +
            "-" + request.getArgument().trim();

        try {
            DirectoryHandle createdDir;
			try {
				createdDir = request.getCurrentDirectory()
					.createDirectory(session.getUserNull(request.getUser())
				                                                       .getName(),
				        session.getUserNull(request.getUser()).getGroup(), createdDirName);
			} catch (FileNotFoundException e) {
				return new CommandResponse(500, "Current directory does not exist, please CWD /");
			}

            //if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
            GlobalContext.getGlobalContext().dispatchFtpEvent(new DirectoryFtpEvent(
                    session.getUserNull(request.getUser()), "REQUEST", createdDir));

            session.getUserNull(request.getUser()).getKeyedMap().incrementObjectLong(REQUESTS);

            //conn.getUser().addRequests();
            return new CommandResponse(257, "\"" + createdDir.getPath() +
                "\" created.");
        } catch (FileExistsException ex) {
            return new CommandResponse(550,
                "directory " + createdDirName + " already exists");
        }
    }
}
