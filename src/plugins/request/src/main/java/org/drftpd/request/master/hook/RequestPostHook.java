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
package org.drftpd.request.master.hook;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.request.master.ConfigRequestData;
import org.drftpd.request.master.RequestSettings;
import org.drftpd.request.master.metadata.RequestData;
import org.drftpd.request.master.metadata.RequestEntry;
import org.drftpd.request.master.metadata.RequestUserData;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestPostHook {

    private static final Logger logger = LogManager.getLogger(RequestPostHook.class);

    @CommandHook(commands = "doSITE_REQUEST", priority = 10, type = HookType.POST)
    public void doREQUESTIncrement(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 257 && response.getCode() != 200) {
            // Request failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (user == null) {
            logger.error("[doSITE_REQUEST::doREQUESTIncrement][Post-hook] User {} does not exists, this should not be possible", request.getUser());
            return;
        }
        user.getConfigHelper().incrementInt(RequestUserData.REQUESTS);
        user.getConfigHelper().incrementInt(RequestUserData.WEEKREQS);
        user.commit();
    }

    @CommandHook(commands = "doSITE_REQFILLED", priority = 10, type = HookType.POST)
    public void doREQFILLEDIncrement(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Reqfilled failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (user == null) {
            logger.error("[doSITE_REQUEST::doREQFILLEDIncrement][Post-hook] User {} does not exists, this should not be possible", request.getUser());
            return;
        }
        user.getConfigHelper().incrementInt(RequestUserData.REQUESTSFILLED);
        user.commit();
    }

    @CommandHook(commands = "doSITE_REQDELETE", priority = 10, type = HookType.POST)
    public void doWklyAllotmentDecrease(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Reqdel failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (user == null) {
            logger.error("[doSITE_REQUEST::doWklyAllotmentDecrease][Post-hook] User {} does not exists, this should not be possible", request.getUser());
            return;
        }
        if (RequestSettings.getSettings().getRequestDecreaseWeekReqs() && user.getConfigHelper().get(RequestUserData.WEEKREQS, 0) > 0) {
            user.getConfigHelper().incrementInt(RequestUserData.WEEKREQS, -1);
            user.commit();
        }
    }

    @CommandHook(commands = "doSITE_RENUSER", type = HookType.POST)
    public void doPostRequestRenuser(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Renuser failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (user == null) {
            logger.error("[doSITE_RENUSER::doPostRequestRenuser][Post-hook] User {} does not exists, this should not be possible", request.getUser());
            return;
        }
        try {
            String from = request.getSession().getObject(RequestUserData.RENUSER_FROM);
            String to = request.getSession().getObject(RequestUserData.RENUSER_TO);
            DirectoryHandle requestDirHandle = new DirectoryHandle(RequestSettings.getSettings().getRequestPath());
            RequestData reqData = requestDirHandle.getPluginMetaData(RequestData.REQUESTS);
            int requestsUpdated = 0;
            for (RequestEntry reqEntry : reqData.getRequests()) {
                if (reqEntry.getUser().equalsIgnoreCase(from)) {
                    String srcDir = reqEntry.getDirectoryName();
                    reqEntry.setUser(to);
                    String destDir = reqEntry.getDirectoryName();
                    logger.info("[doSITE_RENUSER::doPostRequestRenuser][Post-hook] Changing request directory name from [{}] to [{}]", srcDir, destDir);
                    DirectoryHandle reqEntryDirHandle;
                    try {
                        reqEntryDirHandle = requestDirHandle.getDirectoryUnchecked(srcDir);
                    } catch (FileNotFoundException e) {
                        logger.error("Request {} exists in metadata, but not in VFS", reqEntry.getDirectoryName(), e);
                        continue;
                    } catch (ObjectNotValidException e) {
                        logger.error("Request {} exists in metadata, but has an error in VFS", reqEntry.getDirectoryName(), e);
                        continue;
                    }
                    try {
                        reqEntryDirHandle.renameToUnchecked(requestDirHandle.getNonExistentDirectoryHandle(destDir));
                    } catch (FileExistsException e) {
                        logger.error("Destination directory [{}] exists, this should not be possible", destDir);
                        continue;
                    } catch (FileNotFoundException e) {
                        logger.error("Request source directory [{}] was just here but it vanished", srcDir, e);
                    }
                    requestsUpdated++;
                }
            }
            logger.debug("[doSITE_RENUSER::doPostRequestRenuser][Post-hook] Updated {} requests", requestsUpdated);
            // Only store the requests data if we actually changed anything
            if (requestsUpdated > 0) {
                requestDirHandle.addPluginMetaData(RequestData.REQUESTS, new ConfigRequestData(reqData));
            }
        } catch (KeyNotFoundException e) {
            logger.error("[doSITE_RENUSER::doPostRequestRenuser][Post-hook] Something went wrong in the pre hook as the Object are not registered");
        } catch (FileNotFoundException e) {
            logger.error("[doSITE_RENUSER::doPostRequestRenuser][Post-hook] Something went wrong While trying to fix user references or directory names");
        }
    }

    @CommandHook(commands = "doSITE_DELUSER", type = HookType.POST)
    public void doPostRequestDeluser(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Deluser failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (user == null) {
            logger.error("[doSITE_DELUSER::doPostRequestDeluser][Post-hook] User {} does not exists, this should not be possible", request.getUser());
            return;
        }
        StringTokenizer st = new StringTokenizer(request.getArgument());
        String delUsername = st.nextToken();
        try {
            DirectoryHandle requestDirHandle = new DirectoryHandle(RequestSettings.getSettings().getRequestPath());
            RequestData reqData = requestDirHandle.getPluginMetaData(RequestData.REQUESTS);
            List<RequestEntry> deletedRequests = new ArrayList<>();
            for (RequestEntry reqEntry : reqData.getRequests()) {
                if (reqEntry.getUser().equalsIgnoreCase(delUsername)) {
                    try {
                        logger.debug("[doSITE_DELUSER::doPostRequestDeluser][Post-hook] Removing request {}", reqEntry.getDirectoryName());
                        requestDirHandle.getDirectoryUnchecked(reqEntry.getDirectoryName()).deleteUnchecked();
                        deletedRequests.add(reqEntry);
                    } catch (FileNotFoundException e) {
                        logger.error("Request {} exists in metadata, but not in VFS", reqEntry.getDirectoryName(), e);
                    } catch (ObjectNotValidException e) {
                        logger.error("Request {} exists in metadata, but has an error in VFS", reqEntry.getDirectoryName(), e);
                    }
                }
            }
            logger.debug("[doSITE_DELUSER::doPostRequestDeluser][Post-hook] Deleted {} requests", deletedRequests.size());

            // Only update and store the requests data if we actually changed anything
            if (deletedRequests.size() > 0) {
                for (RequestEntry reqEntry : deletedRequests) {
                    reqData.delRequest(reqEntry);
                }
                requestDirHandle.addPluginMetaData(RequestData.REQUESTS, new ConfigRequestData(reqData));
            }
        } catch (KeyNotFoundException e) {
            logger.error("[doSITE_DELUSER::doPostRequestDeluser][Post-hook] Something went wrong in the pre hook as the Object are not registered");
        } catch (FileNotFoundException e) {
            logger.error("[doSITE_DELUSER::doPostRequestDeluser][Post-hook] Something went wrong While trying to fix user references or directory names");
        }
    }
}
