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
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ListUtils;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
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

    private String _requestPath;
    private boolean _createRequestPath;

    private String _reqFilledPrefix;
    private String _requestPrefix;
    private String _requestDateFormat;

    private ArrayList<Pattern> _requestDenyRegex;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);

        // Subscribe to events
        AnnotationProcessor.process(this);

        _bundle = cManager.getResourceBundle();


        _requestDenyRegex = new ArrayList<>();

        readConfig();

        createDirectory();
    }

    /**
     * Reads 'config/plugins/request.conf'
     */
    private void readConfig() {
        Properties props = ConfigLoader.loadPluginConfig("request.conf");
        _requestPath = props.getProperty("request.dirpath", "/REQUESTS/");
        _createRequestPath = Boolean.parseBoolean(props.getProperty("request.createpath", "false"));

        _reqFilledPrefix = props.getProperty("reqfilled.prefix", "FILLED-for.");
        _requestPrefix = props.getProperty("request.prefix", "REQUEST-by.");
        _requestDateFormat = props.getProperty("request.dateformat", "yyyy-MM-dd @ HH:mm");

        _requestDenyRegex.clear();
        int i = 1;
        String regex = props.getProperty("request.deny." + i);
        while (regex != null) {
            Pattern p = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);
            _requestDenyRegex.add(p);
            regex = props.getProperty("request.deny." + i++);
        }
    }

    /**
     * Create the request directory if it does not exist and 'request.createpath' is {@code true}
     */
    private void createDirectory() {
        DirectoryHandle requestDir = new DirectoryHandle(_requestPath);

        if (_createRequestPath && !requestDir.exists()) {
            try {
                requestDir.getParent().createDirectoryRecursive(requestDir.getName(), true);
            } catch (FileExistsException e) {
                logger.error("Tried to create a directory that already exists during request plugin initialization.", e);
            } catch (FileNotFoundException e) {
                logger.error("How did this happened? It was there couple lines above", e);
            }
        }
    }

    /**
     * If the commands has a 'request.dirpath' set we will use this one
     * otherwise we will use the fallback/default path set in 'config/plugins/request.conf'
     * <p>
     * This allows multiple request dirs.
     *
     * @param request
     * @return a {@link DirectoryHandle} representing the correct request dir.
     */
    private DirectoryHandle getRequestDirectory(CommandRequest request) {
        String requestDirProp = request.getProperties().getProperty("request.dirpath");
        if (requestDirProp == null) {
            return new DirectoryHandle(_requestPath);
        }
        return new DirectoryHandle(requestDirProp);
    }

    public CommandResponse doREQFILLED(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        DirectoryHandle requestDir = getRequestDirectory(request);
        String requestName = request.getArgument().trim();

        Map<String, Object> env = new HashMap<>();
        env.put("user", user);
        env.put("request.name", requestName);

        try {
            for (DirectoryHandle dir : requestDir.getDirectoriesUnchecked()) {

                if (!dir.getName().startsWith(_requestPrefix)) {
                    continue;
                }

                RequestParser parser = new RequestParser(dir.getName(), dir.getUsername());

                env.put("request.owner", parser.getUser());

                if (parser.getRequestName().equals(requestName)) {
                    String filledname = _reqFilledPrefix + parser.getUser() + "-" + parser.getRequestName();

                    try {
                        dir.renameToUnchecked(requestDir.getNonExistentDirectoryHandle(filledname));
                    } catch (FileExistsException e) {
                        return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.exists", env, request.getUser()));
                    } catch (FileNotFoundException e) {
                        logger.error("File was just here but it vanished", e);
                        return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.error", env, request.getUser()));
                    }

                    GlobalContext.getEventService().publishAsync(new RequestEvent("reqfilled", user, requestDir, session.getUserNull(parser.getUser()), requestName));

                    if (session instanceof BaseFtpConnection) {
                        return new CommandResponse(200, session.jprintf(_bundle, "reqfilled.success", env, request.getUser()));
                    }
                    // Return ok status to IRC so we know the command was successful
                    return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
                }
            }
        } catch (FileNotFoundException e) {
            return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.root.notfound", env, request.getUser()));
        }

        return new CommandResponse(500, session.jprintf(_bundle, "reqfilled.notfound", env, request.getUser()));
    }

    public CommandResponse doREQUEST(CommandRequest request) throws ImproperUsageException {
        Session session = request.getSession();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        User user = session.getUserNull(request.getUser());
        String requestName = request.getArgument().trim();
        if (!ListUtils.isLegalFileName(requestName)) {
            return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }
        for (Pattern regex : _requestDenyRegex) {
            Matcher m = regex.matcher(requestName);
            if (m.find()) {
                return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
        }
        String createdDirName = _requestPrefix + user.getName() + "-" + requestName;
        DirectoryHandle requestDir = getRequestDirectory(request);

        Map<String, Object> env = new HashMap<>();
        env.put("user", request.getUser());
        env.put("request.name", requestName);
        env.put("request.root", requestDir.getPath());

        try {
            requestDir.createDirectoryUnchecked(createdDirName, user.getName(), user.getGroup().getName());
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

    public CommandResponse doREQUESTS(CommandRequest request) throws ImproperUsageException {
        if (request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Map<String, Object> env = new HashMap<>();
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        response.addComment(request.getSession().jprintf(_bundle, "requests.header", env, request.getUser()));
        int i = 1;

        User user = request.getSession().getUserNull(request.getUser());

        SimpleDateFormat sdf = new SimpleDateFormat(_requestDateFormat);

        try {

                ArrayList<RequestsSort> ReqSort = new ArrayList<>();

                for (DirectoryHandle dir : getRequestDirectory(request).getDirectories(user)) {
                if (!dir.getName().startsWith(_requestPrefix)) {
                    continue;
                }

                RequestParser parser = new RequestParser(dir.getName(), dir.getUsername());

                ReqSort.add(new RequestsSort(parser.getRequestName(), dir.getUsername(), dir.getInode().getCreationTime()));
                
            }

            Collections.sort(ReqSort);
            for(RequestsSort rs:ReqSort){

                Date requestDate = new Date(rs.getRequestTime());

                env.put("num", Integer.toString(i));
                env.put("request.user", rs.getRequestUser());
                env.put("request.name", rs.getRequestName());
                env.put("request.date", sdf.format(requestDate));
                i++;

                response.addComment(request.getSession().jprintf(_bundle, "requests.list", env, request.getUser()));
            }
        } catch (FileNotFoundException e) {
            response.addComment(request.getSession().jprintf(_bundle, "request.error", env, request.getUser()));
        }
        response.addComment(request.getSession().jprintf(_bundle, "requests.footer", env, request.getUser()));
        return response;
    }

    public CommandResponse doREQDELETE(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());
        String requestName = request.getArgument().trim();
        String deleteOthers = request.getProperties().getProperty("request.deleteOthers", "=siteop");
        DirectoryHandle requestDir = getRequestDirectory(request);

        Map<String, Object> env = new HashMap<>();
        env.put("user", user.getName());
        env.put("request.name", requestName);
        env.put("request.root", requestDir.getPath());

        boolean requestNotFound = true;

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        try {
            for (DirectoryHandle dir : requestDir.getDirectories(user)) {

                if (!dir.getName().startsWith(_requestPrefix)) {
                    continue;
                }

                RequestParser parser = new RequestParser(dir.getName(), dir.getUsername());

                if (parser.getRequestName().equals(requestName)) {
                    requestNotFound = false;

                    // checking if the user trying to delete this request
                    // is either the owner or has "super-powers"
                    if (parser.getUser().equals(user.getName()) ||
                            new Permission(deleteOthers).check(user)) {

                        dir.deleteUnchecked();

                        if (session instanceof BaseFtpConnection) {
                            response.addComment(session.jprintf(_bundle, "reqdel.success", env, request.getUser()));
                        }

                        GlobalContext.getEventService().publishAsync(new RequestEvent("reqdel", user, requestDir, session.getUserNull(parser.getUser()), requestName));

                        break;
                    }
                    return new CommandResponse(550, session.jprintf(_bundle, "reqdel.notowner", env, request.getUser()));
                }
            }

            if (requestNotFound) {
                return new CommandResponse(550, session.jprintf(_bundle, "reqdel.notfound", env, request.getUser()));
            }

        } catch (FileNotFoundException e) {
            return new CommandResponse(550, session.jprintf(_bundle, "reqdel.root.notfound", env, request.getUser()));
        }

        return response;
    }

    private class RequestsSort implements Comparable<RequestsSort> {
        private String _requestName;
        private String _requestUser;
        private Long _requestTime;

        RequestsSort(String requestName, String requestUser, Long requestTime){
            _requestName = requestName;
            _requestUser = requestUser;
            _requestTime = requestTime;
        }

        public String getRequestUser() {
            return _requestUser;
        }

        public String getRequestName() {
            return _requestName;
        }

        public Long getRequestTime()  {
            return _requestTime;
        }

        public int compareTo(RequestsSort rs){
            if(_requestTime==rs._requestTime)
                return 0;
            else if(_requestTime>rs._requestTime)
                return 1;
            else
                return -1;
        }
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        readConfig();
    }

    /**
     * Class to centralize how requests are parsed.
     * <p>
     * Transforms a 'request.prefix'-'user'-'requestname' in
     * a nice looking data structure instead of simple strings.
     */
    private class RequestParser {
        private String _user;
        private final String _requestName;

        public RequestParser(String dirname, String owner) {
            _user = owner;
            _requestName = dirname.substring(_requestPrefix.length()).substring(owner.length()+1);
        }

        public String getUser() {
            return _user;
        }

        public String getRequestName() {
            return _requestName;
        }
    }

}
