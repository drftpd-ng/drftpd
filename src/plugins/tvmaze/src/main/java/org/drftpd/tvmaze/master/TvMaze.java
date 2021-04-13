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
package org.drftpd.tvmaze.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.tvmaze.master.metadata.TvEpisode;
import org.drftpd.tvmaze.master.metadata.TvMazeInfo;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author lh
 */
public class TvMaze extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(TvMaze.class);
    private ResourceBundle _bundle;


    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

        TvMazeConfig.getInstance();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public CommandResponse doTV(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String searchstring = request.getArgument().trim();

        boolean verbose = false;
        if (searchstring.toLowerCase().startsWith("-v")) {
            verbose = true;
            searchstring = searchstring.substring(2).trim();
        }

        TvMazeParser tvmaze = new TvMazeParser();
        tvmaze.doTV(searchstring);
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        if (tvmaze.getTvShow() == null) {
            env.put("searchstr", searchstring);
            env.put("error", tvmaze.getError());
            response.addComment(request.getSession().jprintf(_bundle, "tv.none", env, request.getUser()));
        } else {
            env = TvMazeUtils.getShowEnv(tvmaze.getTvShow());
            if (tvmaze.getTvShow().getEPList().length == 0) {
                if (tvmaze.getTvShow().getPreviousEP() != null) {
                    addTagToEnvironment(env, request, "prevep", "tv.prevep", verbose);
                }
                if (tvmaze.getTvShow().getNextEP() != null) {
                    addTagToEnvironment(env, request, "nextep", "tv.nextep", verbose);
                }
                addResponse(env, request, response, "tv.show", verbose);
            } else {
                ArrayList<TvEpisode> epList = new ArrayList<>(Arrays.asList(tvmaze.getTvShow().getEPList()));
                if (epList.size() > 1) {
                    epList.sort(TvMazeUtils.epNumberComparator);
                    addResponse(env, request, response, "tv.ep.season.header", verbose);
                    for (TvEpisode ep : epList) {
                        env = TvMazeUtils.getEPEnv(tvmaze.getTvShow(), ep);
                        addResponse(env, request, response, "tv.ep.season", verbose);
                    }
                } else if (epList.size() == 1) {
                    TvEpisode tvEP = epList.get(0);
                    env = TvMazeUtils.getEPEnv(tvmaze.getTvShow(), tvEP);

                    if (TvMazeConfig.getInstance().searchRelease()) {
                        env.put("foundSD", "No");
                        env.put("foundHD", "No");

                        ArrayList<DirectoryHandle> results = new ArrayList<>();

                        try {
                            for (SectionInterface section : TvMazeConfig.getInstance().getHDSections()) {
                                results.addAll(TvMazeUtils.findReleases("doTV",
                                        section.getCurrentDirectory(), request.getSession().getUserNull(request.getUser()),
                                        tvmaze.getTvShow().getName(), tvEP.getSeason(), tvEP.getNumber()));
                            }
                            for (SectionInterface section : TvMazeConfig.getInstance().getSDSections()) {
                                results.addAll(TvMazeUtils.findReleases("doTV",
                                        section.getCurrentDirectory(), request.getSession().getUserNull(request.getUser()),
                                        tvmaze.getTvShow().getName(), tvEP.getSeason(), tvEP.getNumber()));
                            }
                            for (DirectoryHandle dir : results) {
                                SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
                                if (TvMazeUtils.containSection(sec, TvMazeConfig.getInstance().getHDSections())) {
                                    env.put("foundHD", "Yes");
                                }
                                if (TvMazeUtils.containSection(sec, TvMazeConfig.getInstance().getSDSections())) {
                                    env.put("foundSD", "Yes");
                                }
                            }
                            env.put("results", results.size());
                            addTagToEnvironment(env, request, "release", "tv.ep.release", verbose);
                        } catch (Exception e) {
                            logger.error(e.getMessage(), e);
                        }
                    }
                    addResponse(env, request, response, "tv.ep", verbose);
                }
            }
        }

        return response;
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

    public CommandResponse doCREATETV(CommandRequest request) throws ImproperUsageException {
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
        ArrayList<DirectoryHandle> dirsToCheck = getDirsToCheck(request, dir);
        if (dirsToCheck.isEmpty()) {
            return new CommandResponse(500, "Unable to find any directories within your current working directory that is configured for TV processing. Aborting");
        }
        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "createtvmaze.start", env, request.getUser()));
        for (DirectoryHandle dirToCheck : dirsToCheck) {
            parseTvMazeDirs(dirToCheck, request);
        }
        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "createtvmaze.aborted", env, request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "createtvmaze.complete", env, request.getUser()));
        }
    }

    private void parseTvMazeDirs(DirectoryHandle dir, CommandRequest request) {
        if (request.getSession().isAborted()) {
            return;
        }
        try {
            if (!dir.isHidden(request.getUserObject()) && TvMazeUtils.isRelease(dir.getName())) {
                boolean cache = false;
                TvMazeInfo tvmazeInfo = TvMazeUtils.getTvMazeInfoFromCache(dir);
                if (tvmazeInfo != null) {
                    cache = true;
                } else {
                    tvmazeInfo = TvMazeUtils.getTvMazeInfo(dir);
                }
                if (tvmazeInfo != null) {
                    Map<String, Object> env = TvMazeUtils.getShowEnv(tvmazeInfo);
                    env.put("dirname", dir.getName());
                    env.put("dirpath", dir.getPath());
                    if (cache) {
                        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "createtvmaze.cache", env, request.getUser()));
                    } else {
                        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "createtvmaze.add", env, request.getUser()));
                        try {
                            // Sleep for randomly generated seconds specified in conf
                            Thread.sleep(TvMazeUtils.randomNumber());
                        } catch (InterruptedException ie) {
                            // Thread interrupted
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Failed to get user, log and continue
            logger.warn(e.getMessage());
        }
        if (request.getSession().isAborted()) {
            return;
        }
        try {
            for (DirectoryHandle subdir : dir.getDirectories(request.getUserObject())) {
                parseTvMazeDirs(subdir, request);
            }
        } catch (FileNotFoundException e) {
            // Just continue
        } catch (NoSuchUserException | UserFileException e) {
            logger.error("", e);
        }
    }

    public CommandResponse doREMOVETV(CommandRequest request) throws ImproperUsageException {
        DirectoryHandle dir = request.getCurrentDirectory();
        if (request.hasArgument()) {
            try {
                dir = GlobalContext.getGlobalContext().getRoot().getDirectory(request.getArgument(), request.getUserObject());
            } catch (Exception e) {
                return new CommandResponse(500, "Failed getting path, invalid or no permission!");
            }
        }
        Map<String, Object> env = new HashMap<>();
        env.put("dirname", dir.getName());
        env.put("dirpath", dir.getPath());
        ArrayList<DirectoryHandle> dirsToCheck = getDirsToCheck(request, dir);
        if (dirsToCheck.isEmpty()) {
            return new CommandResponse(500, "Unable to find any directories within your current working directory that is configured for TV processing. Aborting");
        }
        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "removetvmaze.start", env, request.getUser()));
        for (DirectoryHandle dirToCheck : dirsToCheck) {
            removeMetaDataRecursive(dirToCheck, request);
        }
        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "removetvmaze.aborted", env, request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(_bundle, "removetvmaze.complete", env, request.getUser()));
        }
    }

    private void removeMetaDataRecursive(DirectoryHandle dir, CommandRequest request) {
        if (request.getSession().isAborted()) {
            return;
        }
        try {
            if (!dir.isHidden(request.getUserObject()) && dir.removePluginMetaData(TvMazeInfo.TVMAZEINFO) != null) {
                Map<String, Object> env = new HashMap<>();
                env.put("dirname", dir.getName());
                env.put("dirpath", dir.getPath());
                request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "removetvmaze.remove", env, request.getUser()));
            }
        } catch (FileNotFoundException e) {
            // No inode to remove tvmaze info from
        } catch (Exception e) {
            // Failed to get user, log and continue
            logger.warn(e.getMessage());
        }
        try {
            for (DirectoryHandle subdir : dir.getDirectories(request.getUserObject())) {
                removeMetaDataRecursive(subdir, request);
            }
        } catch (FileNotFoundException e) {
            // Just continue
        } catch (NoSuchUserException | UserFileException e) {
            logger.error("", e);
        }
    }

    private ArrayList<DirectoryHandle> getDirsToCheck(CommandRequest request, DirectoryHandle dir) {
        ArrayList<DirectoryHandle> dirsToCheck = new ArrayList<>();
        ArrayList<SectionInterface> joinedSectionList = TvMazeConfig.getInstance().getRaceSections();
        for (SectionInterface section : TvMazeConfig.getInstance().getHDSections()) {
            if (!joinedSectionList.contains(section)) {
                joinedSectionList.add(section);
            }
        }
        for (SectionInterface section : TvMazeConfig.getInstance().getSDSections()) {
            if (!joinedSectionList.contains(section)) {
                joinedSectionList.add(section);
            }
        }
        if (dir.isRoot()) {
            for (SectionInterface section : joinedSectionList) {
                try {
                    dirsToCheck.add(dir.getDirectory(section.getBaseDirectory().getPath(), request.getUserObject()));
                } catch (Exception e) {
                    logger.warn("Failed getting DirectoryHandle for section {}", section);
                }
            }
        } else {
            SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
            if (TvMazeUtils.containSection(sec, joinedSectionList)) {
                dirsToCheck.add(dir);
            }
        }
        return dirsToCheck;
    }

    public CommandResponse doTVQUEUE(CommandRequest request) throws ImproperUsageException {
        Map<String, Object> env = new HashMap<>();
        env.put("size", TvMazeConfig.getInstance().getQueueSize());
        return new CommandResponse(200, request.getSession().jprintf(_bundle, "tv.queue", env, request.getUser()));
    }

}
