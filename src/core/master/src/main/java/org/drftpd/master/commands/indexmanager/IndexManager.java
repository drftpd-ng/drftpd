/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.commands.indexmanager;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.event.MasterEvent;
import org.drftpd.master.indexation.IndexEngineInterface;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;

/**
 * @author fr0w
 * @version $Id$
 */
public class IndexManager extends CommandInterface {
    private ResourceBundle _bundle;


    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    public CommandResponse doRebuildIndex(CommandRequest request) {
        CommandResponse response = new CommandResponse(200, "Index rebuilt");
        GlobalContext.getEventService().publishAsync(new MasterEvent("MSGMASTER", "Started rebuilding index"));

        IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
        try {
            ie.rebuildIndex();
        } catch (IndexException | FileNotFoundException e) {
            return new CommandResponse(550, e.getMessage());
        }

        String message = ("Index rebuilt, entries in the index: " + ie.getStatus().get("inodes"));
        GlobalContext.getEventService().publishAsync(new MasterEvent("MSGMASTER", message));

        response.addComment("Entries in the index: " + ie.getStatus().get("inodes"));

        Session session = request.getSession();
        if (session instanceof BaseFtpConnection) {
            return response;
        }
        return null;
    }

    public CommandResponse doIndexStatus(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();

        if (request.getArgument().equalsIgnoreCase("full")) {
            for (Entry<String, String> entry : ie.getStatus().entrySet()) {
                env.put("key", entry.getKey());
                env.put("value", entry.getValue());
                response.addComment(request.getSession().jprintf(_bundle, "indexstatus", env, request.getUser()));
            }
        } else {
            response.addComment("Entries in the index: " + ie.getStatus().get("inodes"));
        }

        return response;
    }

    public CommandResponse doRefreshIndex(CommandRequest request) {
        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        CommandResponse response = new CommandResponse(200, "Index refreshed");
        GlobalContext.getEventService().publishAsync(new MasterEvent("MSGMASTER", "Started refreshing index"));
        boolean quiet = false;
        if (request.getArgument().equalsIgnoreCase("-q")) {
            quiet = true;
        }

        LinkedList<DirectoryHandle> dirs = new LinkedList<>();
        dirs.add(request.getCurrentDirectory());
        while (dirs.size() > 0 && !session.isAborted()) {
            DirectoryHandle workingDir = dirs.poll();
            try {
                workingDir.requestRefresh(true);
                if (!quiet) {
                    session.printOutput(200, "Refresh requested for " + workingDir.getPath());
                }
            } catch (FileNotFoundException e2) {
                if (!quiet) {
                    session.printOutput(200, "Skipping refresh of " + workingDir.getPath() + " as directory no longer exists");
                }
                // No point trying to list the directory as we know it doesn't exist, skip to the next
                continue;
            }
            try {
                for (InodeHandle inode : workingDir.getInodeHandles(user)) {
                    if (inode.isDirectory()) {
                        dirs.add((DirectoryHandle) inode);
                    } else if (inode.isFile()) {
                        try {
                            inode.requestRefresh(true);
                        } catch (FileNotFoundException e) {
                            // File no longer present, silently skip
                        }
                    }
                }
            } catch (FileNotFoundException e) {
                if (!quiet) {
                    session.printOutput(200, "Error recursively listing: " + workingDir.getPath());
                }
            }
        }

        IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
        String message = ("Index refreshed, entries in the index: " + ie.getStatus().get("inodes"));
        GlobalContext.getEventService().publishAsync(new MasterEvent("MSGMASTER", message));

        if (session instanceof BaseFtpConnection) {
            return response;
        }
        return null;
    }
}
