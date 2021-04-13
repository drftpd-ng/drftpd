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
package org.drftpd.autonuke.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.network.Session;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sections.conf.DatedSection;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.Time;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * FTP/IRC commands available to the AutoNuke plugin
 *
 * @author scitz0
 */
public class AutoNukeCommands extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(AutoNukeCommands.class);

    private ResourceBundle _bundle;


    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    public CommandResponse doAUTONUKES(CommandRequest request) {
        Session session = request.getSession();

        SectionInterface section = GlobalContext.getGlobalContext().
                getSectionManager().getSection(request.getArgument());

        if (request.hasArgument() && section.getName().equals("")) {
            return new CommandResponse(501, "Invalid section!");
        }

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        response.addComment(session.jprintf(_bundle, "autonukes.header", request.getUser()));

        if (DirsToNuke.getDirsToNuke().empty()) {
            response.addComment(session.jprintf(_bundle, "autonukes.empty", request.getUser()));
        }

        Map<String, Object> env = new HashMap<>();

        boolean foundItem = false;
        for (NukeItem ni : DirsToNuke.getDirsToNuke().get()) {
            if (ni.getDir().getPath().startsWith(request.getArgument(), 1)) {
                SectionInterface niSection = GlobalContext.getGlobalContext().getSectionManager().lookup(ni.getDir());
                env.put("section", niSection.getName());
                env.put("sectioncolor", niSection.getColor());
                env.put("dir", ni.getDir().getName());
                env.put("path", ni.getDir().getPath());
                env.put("timeleft", Time.formatTime(ni.getTime() - System.currentTimeMillis()));
                env.put("multiplier", "" + ni.getMultiplier());
                env.put("reason", ni.getReason());
                response.addComment(session.jprintf(
                        _bundle, "autonukes.item", env, request.getUser()));
                if (!foundItem) foundItem = true;
            }
        }

        if (!foundItem && !DirsToNuke.getDirsToNuke().empty()) {
            env.put("section", section.getName());
            env.put("sectioncolor", section.getColor());
            env.put("nbrtotal", "" + DirsToNuke.getDirsToNuke().size());
            response.addComment(session.jprintf(
                    _bundle, "autonukes.section.empty", request.getUser()));
        }

        return response;
    }

    public CommandResponse doDELQUEUE(CommandRequest request) {
        Session session = request.getSession();
        Map<String, Object> env = new HashMap<>();

        if (!request.hasArgument()) {
            env.put("items", "" + DirsToNuke.getDirsToNuke().clear());
            return new CommandResponse(200, session.jprintf(
                    _bundle, "autonukes.del.clear", env, request.getUser()));
        }

        DirectoryHandle dir;
        String path = VirtualFileSystem.fixPath(request.getArgument());
        if (!(path.startsWith(VirtualFileSystem.separator))) {
            // Not a full path, let's make it one
            if (request.getCurrentDirectory().isRoot()) {
                path = VirtualFileSystem.separator + path;
            } else {
                path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + path;
            }
        }
        env.put("path", path);
        try {
            dir = request.getCurrentDirectory().getDirectoryUnchecked(path);
        } catch (FileNotFoundException e) {
            return new CommandResponse(501, session.jprintf(
                    _bundle, "autonukes.del.error", env, request.getUser()));
        } catch (ObjectNotValidException e) {
            return new CommandResponse(501, session.jprintf(
                    _bundle, "autonukes.del.error", env, request.getUser()));
        }
        env.put("dir", dir.getName());

        if (DirsToNuke.getDirsToNuke().del(dir)) {
            return new CommandResponse(200, session.jprintf(
                    _bundle, "autonukes.del.success", env, request.getUser()));
        }
        return new CommandResponse(500, session.jprintf(
                _bundle, "autonukes.del.notfound", env, request.getUser()));
    }

    public CommandResponse doAUTONUKESCAN(CommandRequest request) {
        DirectoryHandle dir = request.getCurrentDirectory();
        if (request.hasArgument()) {
            SectionInterface section = GlobalContext.getGlobalContext().
                    getSectionManager().getSection(request.getArgument());
            if (section.getName().equals("")) {
                return new CommandResponse(500, "Invalid section, aborting");
            } else {
                dir = section.getBaseDirectory();
            }
        }
        ArrayList<SectionInterface> sectionsToCheck = new ArrayList<>();
        if (dir.isRoot()) {
            for (SectionInterface section : GlobalContext.getGlobalContext().getSectionManager().getSections()) {
                if (!AutoNukeSettings.getSettings().getExcludedSections().contains(section)) {
                    sectionsToCheck.add(section);
                }
            }
        } else {
            SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
            if (!AutoNukeSettings.getSettings().getExcludedSections().contains(section)) {
                sectionsToCheck.add(section);
            }
        }
        if (sectionsToCheck.isEmpty()) {
            return new CommandResponse(500, "Section excluded from autonuke scan, aborting");
        }
        Map<String, Object> env = new HashMap<>();
        for (SectionInterface section : sectionsToCheck) {
            DirectoryHandle sectionRoot = section.getBaseDirectory();
            env.put("section", section.getName());
            env.put("sectioncolor", section.getColor());
            request.getSession().printOutput(200, request.getSession().jprintf(
                    _bundle, "autonukescan.start", env, request.getUser()));

            if (request.getSession().isAborted()) { break; }
            try {
                ArrayList<DirectoryHandle> sectionDirs = new ArrayList<>();
                if (section instanceof DatedSection) {
                    sectionDirs.addAll(sectionRoot.getDirectoriesUnchecked());
                } else {
                    sectionDirs.add(sectionRoot);
                }
                for (DirectoryHandle sectionDir : sectionDirs) {
                    for (DirectoryHandle releaseDir : sectionDir.getDirectories(request.getUserObject())) {
                        // Dir globaly excluded?
                        if (releaseDir.getName().matches(AutoNukeSettings.getSettings().getExcludedDirs())) continue;
                        // return if any matching sub directories exist in _excludeSubDirs
                        boolean foundExcludedSubDir = false;
                        try {
                            for (DirectoryHandle subDir : releaseDir.getDirectoriesUnchecked()) {
                                if (subDir.getName().matches(AutoNukeSettings.getSettings().getExcludedSubDirs())) {
                                    foundExcludedSubDir = true;
                                    break;
                                }
                            }
                        } catch (FileNotFoundException e) {
                            // Strange, dir was just here
                            logger.warn("AutoNuke doAUTONUKESCAN: FileNotFoundException - {}", releaseDir.getName());
                            continue;
                        }
                        if (foundExcludedSubDir) continue;
                        // Dir valid so far, add it to scan queue
                        DirsToCheck.getDirsToCheck().add(releaseDir);
                    }
                }
            } catch (FileNotFoundException e) {
                // Just continue
            } catch (NoSuchUserException e) {
                logger.error("", e);
            } catch (UserFileException e) {
                logger.error("", e);
            }
        }
        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(
                    _bundle, "autonukescan.aborted", request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(
                    _bundle, "autonukescan.complete", request.getUser()));
        }
    }

}
