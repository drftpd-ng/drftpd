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
package org.drftpd.request.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ListUtils;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.request.master.event.RequestEvent;
import org.drftpd.request.master.metadata.RequestData;
import org.drftpd.request.master.metadata.RequestEntry;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mog
 * @version $Id$
 */
public class Request extends CommandInterface {

    private static final Logger logger = LogManager.getLogger(Request.class);

    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);

        // Subscribe to events
        AnnotationProcessor.process(this);

        _bundle = cManager.getResourceBundle();

        // Load our config
        getSettings();

        createDirectory();
    }

    private RequestSettings getSettings() {
        return RequestSettings.getSettings();
    }

    /**
     * Create the request directory if it does not exist and 'request.createpath' is {@code true}
     */
    private void createDirectory() {
        DirectoryHandle requestDir = new DirectoryHandle(getSettings().getRequestPath());

        if (RequestSettings.getSettings().getCreateRequestPath()) {
            if (requestDir.exists()) {
                logger.debug("Not creating request directory {} as it already exists", requestDir.getName());
            } else {
                logger.debug("Creating request directory {} as it does not exist", requestDir.getName());
                try {
                    requestDir.getParent().createDirectoryRecursive(requestDir.getName(), true);
                } catch (FileExistsException e) {
                    logger.error("Tried to create a directory that already exists during request plugin initialization.", e);
                } catch (FileNotFoundException e) {
                    logger.error("How did this happened? It was there couple lines above", e);
                }
            }
        } else {
            logger.debug("Skipping creation of request dir {} as creation is disabled", requestDir.getName());
        }
    }

    /**
     * If the commands has a 'request.dirpath' set we will use this one
     * otherwise we will use the fallback/default path set in 'config/plugins/request.conf'
     *
     * This allows multiple request dirs.
     *
     * @param request The request send to one of the below doSITE_*** Commands
     *
     * @return a {@link DirectoryHandle} representing the correct request dir
     */
    private DirectoryHandle getRequestDirectory(CommandRequest request) {
        String requestDirProp = request.getProperties().getProperty("request.dirpath");
        if (requestDirProp == null) {
            requestDirProp = getSettings().getRequestPath();
        }
        return new DirectoryHandle(requestDirProp);
    }

    /**
     * For a given directory handle (should be provided by getRequestDirectory) return the requestData object
     *
     * @return a {@link RequestData} Representing all requests registered in the VFS for this directory Handle
     */
    private RequestData getRequestData(DirectoryHandle dirHandle) {
        RequestData reqData;
        try {
            try {
                reqData = dirHandle.getPluginMetaData(RequestData.REQUESTS);
            } catch (KeyNotFoundException e1) {
                logger.debug("Setting up new RequestData for {}", dirHandle.getName());
                reqData = new RequestData();
                storeRequestData(dirHandle, reqData);
            }
        } catch(FileNotFoundException e) {
            logger.error("Something is wrong with VFS path {}", dirHandle.getName(), e);
            return null;
        }
        return reqData;
    }

    private void storeRequestData(DirectoryHandle dirHandle, RequestData reqData) throws FileNotFoundException {
        dirHandle.addPluginMetaData(RequestData.REQUESTS, reqData);
    }

    public CommandResponse doSITE_REQFILLED(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        if (st.countTokens() != 1) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        DirectoryHandle requestDir = getRequestDirectory(request);
        RequestData requests = getRequestData(requestDir);

        if (requests == null) {
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }

        String requestName = st.nextToken();

        Map<String, Object> env = new HashMap<>();
        env.put("user", user);
        env.put("request.name", requestName);

        RequestEntry reqEntry = findRequestByName(requests, requestName);
        if (reqEntry == null) {
            return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.notfound", env, request.getUser()));
        }

        DirectoryHandle reqEntryDirHandle;
        try {
            reqEntryDirHandle = requestDir.getDirectoryUnchecked(reqEntry.getDirectoryName());
        } catch (FileNotFoundException e) {
            logger.error("Request {} exists in metadata, but not in VFS", reqEntry.getDirectoryName(), e);
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        } catch (ObjectNotValidException e) {
            logger.error("Request {} exists in metadata, but has an error in VFS", reqEntry.getDirectoryName(), e);
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }
        env.put("request.owner", reqEntry.getUser());
        String filledDirectoryName = reqEntry.getFilledDirectoryName(getSettings().getRequestFilledPrefix());
        logger.debug("Request {} is being filled. Moving directory from {} to {}", reqEntry.getName(), reqEntryDirHandle.getName(), filledDirectoryName);

        try {
            reqEntryDirHandle.renameToUnchecked(requestDir.getNonExistentDirectoryHandle(filledDirectoryName));
            requests.delRequest(reqEntry);
            storeRequestData(requestDir, requests);
        } catch (FileExistsException e) {
            return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.exists", env, request.getUser()));
        } catch (FileNotFoundException e) {
            logger.error("File was just here but it vanished", e);
            return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.error", env, request.getUser()));
        }

        GlobalContext.getEventService().publishAsync(new RequestEvent("reqfilled", user, requestDir, session.getUserNull(reqEntry.getUser()), requestName));

        if (session instanceof BaseFtpConnection) {
            return new CommandResponse(200, session.jprintf(_bundle, "reqfilled.success", env, request.getUser()));
        }

        // Return ok status to IRC so we know the command was successful
        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

    }

    public CommandResponse doSITE_REQUEST(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        if (st.countTokens() != 1) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        DirectoryHandle requestDir = getRequestDirectory(request);
        RequestData requests = getRequestData(requestDir);

        if (requests == null) {
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }

        String requestName = st.nextToken();

        if (!ListUtils.isLegalFileName(requestName)) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        for (Pattern regex : getSettings().getRequestDenyRegex()) {
            Matcher m = regex.matcher(requestName);
            if (m.find()) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
        }

        RequestEntry reqEntry = findRequestByName(requests, requestName);
        if (reqEntry != null) {
            return new CommandResponse(500, "Request already exists");
        }
        reqEntry = new RequestEntry(requestName, user.getName(), getSettings().getRequestPrefix(), Instant.now().getEpochSecond());

        Map<String, Object> env = new HashMap<>();
        env.put("user", request.getUser());
        env.put("request.name", requestName);
        env.put("request.root", requestDir.getPath());

        try {
            logger.debug("Creating directory: {}", reqEntry.getDirectoryName());
            requestDir.createDirectoryUnchecked(reqEntry.getDirectoryName(), user.getName(), user.getGroup().getName());
            requests.addRequest(reqEntry);
            storeRequestData(requestDir, requests);
        } catch (FileExistsException e) {
            return new CommandResponse(550, session.jprintf(_bundle, "request.exists", env, user.getName()));
        } catch (FileNotFoundException e) {
            logger.error("File was just here but it vanished", e);
            return new CommandResponse(550, session.jprintf(_bundle, "request.error", env, user.getName()));
        }

        GlobalContext.getEventService().publishAsync(new RequestEvent("request", requestDir, user, requestName));

        if (session instanceof BaseFtpConnection) {
            return new CommandResponse(257, session.jprintf(_bundle, "request.success", env, user.getName()));
        }

        // Return ok status to IRC so we know the command was successful
        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    public CommandResponse doSITE_REQUESTS(CommandRequest request) throws ImproperUsageException {

        // Command has no arguments
        if (request.hasArgument()) {
            throw new ImproperUsageException();
        }

        DirectoryHandle requestDir = getRequestDirectory(request);
        RequestData requests = getRequestData(requestDir);

        if (requests == null) {
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        response.addComment(request.getSession().jprintf(_bundle, "requests.header", request.getUser()));

        SimpleDateFormat sdf = new SimpleDateFormat(getSettings().getRequestDateFormat());

        int i = 1;
        for (RequestEntry reqEntry : requests.getRequests()) {
            Map<String, Object> env = new HashMap<>();
            env.put("num", Integer.toString(i++));
            env.put("request.name", reqEntry.getName());
            env.put("request.user", reqEntry.getUser());
            env.put("request.date", sdf.format(reqEntry.getCreationTime()));
            response.addComment(request.getSession().jprintf(_bundle, "requests.list", env, request.getUser()));
        }

        // Make sure we report that we did not have find any requests
        if (i == 1) {
            response.addComment("Currently no requests");
        }

        response.addComment(request.getSession().jprintf(_bundle, "requests.footer", request.getUser()));

        return response;
    }

    public CommandResponse doSITE_REQDELETE(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(request.getArgument());
        if (st.countTokens() != 1) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        DirectoryHandle requestDir = getRequestDirectory(request);
        RequestData requests = getRequestData(requestDir);

        if (requests == null) {
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }

        String requestName = st.nextToken();

        Permission deleteOthersPerm = new Permission(request.getProperties().getProperty("request.deleteOthers", "=siteop"));

        Map<String, Object> env = new HashMap<>();
        env.put("user", user.getName());
        env.put("request.name", requestName);
        env.put("request.root", requestDir.getPath());

        RequestEntry reqEntry = findRequestByName(requests, requestName);
        if (reqEntry == null) {
            return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.notfound", env, request.getUser()));
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        if (reqEntry.getUser().equals(user.getName()) || deleteOthersPerm.check(user)) {
            try {
                logger.debug("Deleting directory: {}", reqEntry.getDirectoryName());
                requestDir.getDirectoryUnchecked(reqEntry.getDirectoryName()).deleteUnchecked();
                requests.delRequest(reqEntry);
                storeRequestData(requestDir, requests);
            } catch (FileNotFoundException e) {
                logger.error("Request {} exists in metadata, but not in VFS", reqEntry.getDirectoryName(), e);
                return new CommandResponse(500, "Internal Server error occurred, stopping execution");
            } catch (ObjectNotValidException e) {
                logger.error("Request {} exists in metadata, but has an error in VFS", reqEntry.getDirectoryName(), e);
                return new CommandResponse(500, "Internal Server error occurred, stopping execution");
            }

            if (session instanceof BaseFtpConnection) {
                response.addComment(session.jprintf(_bundle, "reqdel.success", env, request.getUser()));
            }

            GlobalContext.getEventService().publishAsync(new RequestEvent("reqdel", user, requestDir, session.getUserNull(reqEntry.getUser()), reqEntry.getName()));

        } else {
            return new CommandResponse(550, session.jprintf(_bundle, "reqdel.notowner", env, request.getUser()));
        }

        return response;
    }

    public CommandResponse doSITE_FIXREQUESTS(CommandRequest request) throws ImproperUsageException {

        // Command has no arguments
        if (request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        DirectoryHandle requestDir = getRequestDirectory(request);
        RequestData requests = getRequestData(requestDir);

        if (requests == null) {
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }

        int fixed = 0;
        logger.debug("Trying to fix request dirs in {}", requestDir.getName());
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        List<String> dirNames = new ArrayList<>();
        for (RequestEntry reqEntry : requests.getRequests()) {
            try {
                requestDir.getDirectoryUnchecked(reqEntry.getDirectoryName());
            } catch (FileNotFoundException e) {
                logger.debug("Creating directory: {}", reqEntry.getDirectoryName());
                User u = session.getUserNull(reqEntry.getUser());
                if (u == null) {
                    logger.error("Request {} has been created by {}, which no longer seems to exist", reqEntry.getName(), reqEntry.getUser());
                    response.addComment("Request " + reqEntry.getName() + " created by unknown user " + reqEntry.getUser() + ", deleting");
                    requests.delRequest(reqEntry);
                    try {
                        storeRequestData(requestDir, requests);
                    } catch(FileNotFoundException e2) {
                        logger.error("Unable to update plugin metadata for request directory handle {}", reqEntry.getDirectoryName(), e2);
                        return new CommandResponse(500, "Internal Server error occurred, stopping execution");
                    }
                    continue;
                }
                try {
                    response.addComment("Found missing directory " + reqEntry.getDirectoryName() + ", Creating");
                    requestDir.createDirectoryUnchecked(reqEntry.getDirectoryName(), u.getName(), u.getGroup().getName());
                    fixed++;
                } catch (FileNotFoundException | FileExistsException e2) {
                    logger.error("Unable to create request dir {} in {}", reqEntry.getDirectoryName(), requestDir.getName(), e);
                    return new CommandResponse(500, "Internal Server error occurred, stopping execution");
                }
            } catch (ObjectNotValidException e) {
                logger.error("Unknown exception while trying to fix requests", e);
                return new CommandResponse(500, "Internal Server error occurred, stopping execution");
            }
            dirNames.add(reqEntry.getDirectoryName());
        }
        try {
            for (DirectoryHandle dirHandle : requestDir.getDirectoriesUnchecked()) {
                if (!dirNames.contains(dirHandle.getName())) {
                    logger.warn("Directory handle {} should not exist", dirHandle.getName());
                    if (dirHandle.getSize() == 0L) {
                        response.addComment("Found unexpected directory " + dirHandle.getName() + ", which is empty. Removing");
                        try {
                            requestDir.getDirectoryUnchecked(dirHandle.getName()).deleteUnchecked();
                            fixed++;
                        } catch (ObjectNotValidException e) {
                            logger.error("Unknown exception happened during deletion of {}", dirHandle.getName(), e);
                            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
                        }
                    } else {
                        response.addComment("Found unexpected directory " + dirHandle.getName() + ", which is not empty. Please check manually");
                    }
                }
            }
        } catch(FileNotFoundException e) {
            logger.error("An unexpected error happened while browsing directories in {}", requestDir.getName(), e);
            return new CommandResponse(500, "Internal Server error occurred, stopping execution");
        }
        response.addComment("We have successfully fixed " + fixed + " directories");
        return response;
    }

    private RequestEntry findRequestByName(RequestData requests, String requestName) {
        logger.debug("Trying to find [{}] in {} requests", requestName, requests.getRequests().size());
        for (RequestEntry req : requests.getRequests()) {
            logger.debug("[{}] <=> [{}]", requestName, req.getName());
            if (req.getName().equals(requestName)) {
                return req;
            }
        }
        return null;
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        getSettings().reload();
    }
}
