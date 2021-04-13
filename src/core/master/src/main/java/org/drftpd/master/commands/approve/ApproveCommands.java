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
package org.drftpd.master.commands.approve;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.approve.metadata.Approve;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.IndexEngineInterface;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class ApproveCommands extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(ApproveCommands.class);

    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    public CommandResponse doREMAPPROVE(CommandRequest request) {
        return doApprove(request, true);
    }

    public CommandResponse doAPPROVE(CommandRequest request) {
        return doApprove(request, false);
    }

    public CommandResponse doApprove(CommandRequest request, boolean doremove) {
        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        DirectoryHandle dir = request.getCurrentDirectory();
        boolean remove = doremove;
        if (request.hasArgument() && (user != null)) {
            String path = request.getArgument();
            if (path.startsWith("-r ")) {
                // remove option specified
                remove = true;
                path = path.substring(3);
            }
            if (path.charAt(0) != '/') {
                // Full path not given, try to get it with index system
                IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();

                Map<String, String> inodes;

                AdvancedSearchParams params = new AdvancedSearchParams();
                params.setExact(path);
                params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
                params.setSortField("lastmodified");
                params.setSortOrder(true);

                try {
                    String caller = doremove ? "doREMAPPROVE" : "doAPPROVE";
                    inodes = ie.advancedFind(dir, params, caller);
                } catch (IndexException e) {
                    logger.error("Index Exception: {}", e.getMessage());
                    return new CommandResponse(500, "Index Exception: " + e.getMessage());
                }

                ArrayList<DirectoryHandle> dirsToApprove = new ArrayList<>();

                for (Map.Entry<String, String> item : inodes.entrySet()) {
                    try {
                        DirectoryHandle inode = new DirectoryHandle(VirtualFileSystem.fixPath(item.getKey()));
                        if (!inode.isHidden(user)) {
                            dirsToApprove.add(inode);
                        }
                    } catch (FileNotFoundException e) {
                        logger.warn("Index contained an unexistent inode: {}", item.getKey());
                    }
                }

                Map<String, Object> env = new HashMap<>();
                env.put("user", user.getName());
                env.put("searchstr", path);
                if (dirsToApprove.isEmpty()) {
                    return new CommandResponse(550, session.jprintf(_bundle, "approve.search.empty", env, user));
                } else if (dirsToApprove.size() == 1) {
                    path = dirsToApprove.get(0).getPath();
                } else {
                    CommandResponse response = new CommandResponse(200);
                    response.addComment(session.jprintf(_bundle, "approve.search.start", env, user));
                    int count = 1;
                    for (DirectoryHandle foundDir : dirsToApprove) {
                        try {
                            env.put("name", foundDir.getName());
                            env.put("path", foundDir.getPath());
                            env.put("owner", foundDir.getUsername());
                            env.put("group", foundDir.getGroup());
                            env.put("num", count++);
                            env.put("size", Bytes.formatBytes(foundDir.getSize()));
                            response.addComment(session.jprintf(_bundle, "approve.search.item", env, user));
                        } catch (FileNotFoundException e) {
                            logger.warn("Dir deleted after index search?, skip and continue: {}", foundDir.getPath());
                        }
                    }

                    response.addComment(session.jprintf(_bundle, "approve.search.end", env, user));

                    // Return matching dirs and let user decide what to approve
                    return response;
                }
            }
            dir = new DirectoryHandle(path);
            if (!dir.exists()) {
                Map<String, Object> env = new HashMap<>();
                env.put("path", path);
                return new CommandResponse(500, session.jprintf(_bundle, "approve.error.path", env, user));
            }
        }

        if ((user != null) && (!dir.isRoot())) {
            Map<String, Object> env = new HashMap<>();
            env.put("path", dir.getPath());

            // Mark or remove dir as approved!
            try {
                if (remove) {
                    dir.removePluginMetaData(Approve.APPROVE);
                    return new CommandResponse(200, session.jprintf(_bundle, "approve.remove", env, user));
                }

                try {
                    if (!dir.getPluginMetaData(Approve.APPROVE)) {
                        throw new KeyNotFoundException();
                    }
                    return new CommandResponse(200, session.jprintf(_bundle, "approve.approved", env, user));
                } catch (KeyNotFoundException e) {
                    dir.addPluginMetaData(Approve.APPROVE, true);
                    return new CommandResponse(200, session.jprintf(_bundle, "approve.success", env, user));
                }

            } catch (FileNotFoundException e) {
                logger.error("Dir was just here but now its gone, {}", dir.getPath());
                return new CommandResponse(500, "Dir was just here but now its gone, " + dir.getPath());
            }
        }
        return new CommandResponse(500, "Cannot Approve Directory");
    }
}
