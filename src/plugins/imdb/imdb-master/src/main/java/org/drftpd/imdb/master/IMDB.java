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
package org.drftpd.imdb.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.imdb.master.hooks.IMDBPostHook;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class IMDB extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(IMDBPostHook.class);
    private ResourceBundle _bundle;


    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

        IMDBConfig.getInstance();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private void addTagToEnvironment(Map<String, Object> env, CommandRequest request, String tag, String key, boolean verbose) {
        if (verbose) {
            env.put(tag, request.getSession().jprintf(_bundle, key + ".verbose", env, request.getUser()));
        } else {
            env.put(tag, request.getSession().jprintf(_bundle, key, env, request.getUser()));
        }
    }

    private void addResponse(Map<String, Object> env, CommandRequest request, CommandResponse response, String key, boolean verbose) {
        if (verbose) {
            response.addComment(request.getSession().jprintf(_bundle, key + ".verbose", env, request.getUser()));
        } else {
            response.addComment(request.getSession().jprintf(_bundle, key, env, request.getUser()));
        }
    }

    public CommandResponse doIMDB(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String searchstring = request.getArgument().trim();

        boolean verbose = false;
        if (searchstring.toLowerCase().startsWith("-v")) {
            verbose = true;
            searchstring = searchstring.substring(2).trim();
        }

        searchstring = IMDBUtils.filterTitle(searchstring);

        IMDBParser imdb = new IMDBParser();
        imdb.doSEARCH(searchstring);

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = imdb.getEnv();
        if (imdb.foundMovie()) {
            if (IMDBConfig.getInstance().searchRelease()) {
                env.put("foundSD", "No");
                env.put("foundHD", "No");

                ArrayList<DirectoryHandle> results = new ArrayList<>();

                try {
                    for (SectionInterface section : IMDBConfig.getInstance().getHDSections()) {
                        results.addAll(IMDBUtils.findReleases("doIMDB",
                                section.getBaseDirectory(), request.getSession().getUserNull(request.getUser()),
                                imdb.getTitle(), imdb.getYear()));
                    }
                    for (SectionInterface section : IMDBConfig.getInstance().getSDSections()) {
                        results.addAll(IMDBUtils.findReleases("doIMDB",
                                section.getBaseDirectory(), request.getSession().getUserNull(request.getUser()),
                                imdb.getTitle(), imdb.getYear()));
                    }
                    for (DirectoryHandle dir : results) {
                        SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
                        if (IMDBUtils.containSection(sec, IMDBConfig.getInstance().getHDSections())) {
                            env.put("foundHD", "Yes");
                        }
                        if (IMDBUtils.containSection(sec, IMDBConfig.getInstance().getSDSections())) {
                            env.put("foundSD", "Yes");
                        }
                    }
                    env.put("results", results.size());
                    addTagToEnvironment(env, request, "release", "announce.release", verbose);
                } catch (Exception e) {
                    logger.error(e.getMessage(), e);
                }
            }
            addResponse(env, request, response, "announce", verbose);

        } else {
            response.addComment(request.getSession().jprintf(_bundle, "notfound", env, request.getUser()));
        }
        return response;
    }

    public CommandResponse doCREATEIMDB(CommandRequest request) throws ImproperUsageException {
        DirectoryHandle dir = request.getCurrentDirectory();
        if (request.hasArgument()) {
            try {
                dir = GlobalContext.getGlobalContext().getRoot().
                        getDirectory(request.getArgument(), request.getUserObject());
            } catch (Exception e) {
                return new CommandResponse(500, "Failed getting path, invalid or no permission!");
            }
        }

        Map<String, Object> env = new HashMap<>();
        env.put("dirname", dir.getName());
        env.put("dirpath", dir.getPath());

        Map<String, String> nfoFiles;
        try {
            nfoFiles = IMDBUtils.getNFOFiles(dir, "doCREATEIMDB");
        } catch (IndexException e) {
            return new CommandResponse(500, "Index Exception: " + e.getMessage());
        }

        if (nfoFiles.isEmpty()) {
            return new CommandResponse(500, "No nfo files found, aborting");
        }

        User user;
        try {
            user = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(request.getUser());
        } catch (NoSuchUserException e) {
            return new CommandResponse(500, "Couldn't find user: " + e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(500, "User file corrupt?: " + e.getMessage());
        }

        request.getSession().printOutput(200, request.getSession().jprintf(_bundle,
                "createimdb.start", env, request.getUser()));

        for (Map.Entry<String, String> item : nfoFiles.entrySet()) {
            try {
                FileHandle nfo = new FileHandle(VirtualFileSystem.fixPath(item.getKey()));
                if (!nfo.isHidden(user)) {
                    // Check if valid section
                    SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(nfo.getParent());
                    if (IMDBUtils.containSection(sec, IMDBConfig.getInstance().getRaceSections())) {
                        DirectoryHandle parent = nfo.getParent();
                        IMDBInfo imdbInfo = IMDBUtils.getIMDBInfo(parent, false);
                        if (imdbInfo == null) {
                            continue;
                        }
                        env.put("dirname", parent.getName());
                        env.put("dirpath", parent.getPath());
                        env.put("filename", nfo.getName());
                        env.put("filepath", nfo.getPath());
                        if (imdbInfo.getMovieFound()) {
                            request.getSession().printOutput(200, request.getSession().jprintf(_bundle,
                                    "createimdb.cache", env, request.getUser()));
                        } else {
                            IMDBUtils.addMetadata(imdbInfo, parent);
                            if (imdbInfo.getMovieFound()) {
                                request.getSession().printOutput(200, request.getSession().jprintf(_bundle,
                                        "createimdb.add", env, request.getUser()));
                            } else {
                                request.getSession().printOutput(500, request.getSession().jprintf(_bundle,
                                        "createimdb.fail", env, request.getUser()));
                            }
                            try {
                                // Sleep for randomly generated seconds specified in conf
                                Thread.sleep(IMDBUtils.randomNumber());
                            } catch (InterruptedException ie) {
                                // Thread interrupted
                            }
                        }
                    }
                }
                if (request.getSession().isAborted()) {
                    break;
                }
            } catch (FileNotFoundException e) {
                logger.warn("Index contained an unexistent inode: {}", item.getKey());
            }
        }

        env.put("dirname", dir.getName());
        env.put("dirpath", dir.getPath());

        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "createimdb.aborted", env, request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "createimdb.complete", env, request.getUser()));
        }
    }

    public CommandResponse doREMOVEIMDB(CommandRequest request) throws ImproperUsageException {
        DirectoryHandle dir = request.getCurrentDirectory();
        if (request.hasArgument()) {
            try {
                dir = GlobalContext.getGlobalContext().getRoot().
                        getDirectory(request.getArgument(), request.getUserObject());
            } catch (Exception e) {
                return new CommandResponse(500, "Failed getting path, invalid or no permission!");
            }
        }
        Map<String, Object> env = new HashMap<>();
        env.put("dirname", dir.getName());
        env.put("dirpath", dir.getPath());

        Map<String, String> nfoFiles;
        try {
            nfoFiles = IMDBUtils.getNFOFiles(dir, "doREMOVEIMDB");
        } catch (IndexException e) {
            return new CommandResponse(500, "Index Exception: " + e.getMessage());
        }

        if (nfoFiles.isEmpty()) {
            return new CommandResponse(500, "No nfo files found, aborting");
        }

        User user;
        try {
            user = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(request.getUser());
        } catch (NoSuchUserException e) {
            return new CommandResponse(500, "Couldn't find user: " + e.getMessage());
        } catch (UserFileException e) {
            return new CommandResponse(500, "User file corrupt?: " + e.getMessage());
        }

        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "removeimdb.start", env, request.getUser()));

        for (Map.Entry<String, String> item : nfoFiles.entrySet()) {
            try {
                FileHandle nfo = new FileHandle(VirtualFileSystem.fixPath(item.getKey()));
                if (!nfo.isHidden(user)) {
                    try {
                        if (nfo.getParent().removePluginMetaData(IMDBInfo.IMDBINFO) != null) {
                            Map<String, Object> env2 = new HashMap<>();
                            env2.put("dirname", nfo.getParent().getName());
                            env2.put("dirpath", nfo.getParent().getPath());
                            request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "removeimdb.remove", env2, request.getUser()));
                        }
                    } catch (FileNotFoundException e) {
                        // No inode to remove imdb info from
                    }
                }
                if (request.getSession().isAborted()) {
                    break;
                }
            } catch (FileNotFoundException e) {
                logger.warn("Index contained an unexistent inode: {}", item.getKey());
            }
        }

        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "removeimdb.aborted", env, request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "removeimdb.complete", env, request.getUser()));
        }
    }

    public CommandResponse doIMDBQUEUE(CommandRequest request) throws ImproperUsageException {
        Map<String, Object> env = new HashMap<>();
        env.put("size", IMDBConfig.getInstance().getQueueSize());
        return new CommandResponse(200, request.getSession().jprintf(_bundle, "imdb.queue", env, request.getUser()));
    }
}
