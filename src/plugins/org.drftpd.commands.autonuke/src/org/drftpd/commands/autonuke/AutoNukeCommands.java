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
package org.drftpd.commands.autonuke;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.Session;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.conf.DatedSection;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.ResourceBundle;

/**
 * FTP/IRC commands available to the AutoNuke plugin
 * @author scitz0
 */
public class AutoNukeCommands extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(AutoNukeCommands.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
    }

	public CommandResponse doSITE_AUTONUKES(CommandRequest request) {
		Session session = request.getSession();

		SectionInterface section = GlobalContext.getGlobalContext().
				getSectionManager().getSection(request.getArgument());

		if (request.hasArgument() && section.getName().equals("")) {
			return new CommandResponse(501, "Invalid section!");
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		response.addComment(session.jprintf(_bundle, _keyPrefix+"autonukes.header", request.getUser()));

		if (DirsToNuke.getDirsToNuke().empty()) {
			response.addComment(session.jprintf(_bundle, _keyPrefix+"autonukes.empty", request.getUser()));
		}

		ReplacerEnvironment env = new ReplacerEnvironment();

		boolean foundItem = false;
		for (NukeItem ni : DirsToNuke.getDirsToNuke().get()) {
			if (ni.getDir().getPath().startsWith(request.getArgument(), 1)) {
				SectionInterface niSection = GlobalContext.getGlobalContext().getSectionManager().lookup(ni.getDir());
				env.add("section", niSection.getName());
				env.add("sectioncolor", niSection.getColor());
				env.add("dir", ni.getDir().getName());
				env.add("path", ni.getDir().getPath());
				env.add("timeleft", Time.formatTime(ni.getTime() - System.currentTimeMillis()));
				env.add("multiplier", ""+ni.getMultiplier());
				env.add("reason", ni.getReason());
				response.addComment(session.jprintf(
						_bundle, _keyPrefix+"autonukes.item", env, request.getUser()));
				if (!foundItem) foundItem = true;
			}
		}

		if (!foundItem && !DirsToNuke.getDirsToNuke().empty()) {
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			env.add("nbrtotal", ""+DirsToNuke.getDirsToNuke().size());
			response.addComment(session.jprintf(
					_bundle, _keyPrefix+"autonukes.section.empty", request.getUser()));
		}

        return response;
	}

	public CommandResponse doSITE_DELQUEUE(CommandRequest request) {
		Session session = request.getSession();
		ReplacerEnvironment env = new ReplacerEnvironment();

		if (!request.hasArgument()) {
			env.add("items", ""+DirsToNuke.getDirsToNuke().clear());
			return new CommandResponse(200, session.jprintf(
					_bundle, _keyPrefix+"autonukes.del.clear", env, request.getUser()));
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
		env.add("path", path);
		try {
			dir = request.getCurrentDirectory().getDirectoryUnchecked(path);
		} catch (FileNotFoundException e) {
			return new CommandResponse(501, session.jprintf(
					_bundle, _keyPrefix+"autonukes.del.error", env, request.getUser()));
		} catch (ObjectNotValidException e) {
			return new CommandResponse(501, session.jprintf(
					_bundle, _keyPrefix+"autonukes.del.error", env, request.getUser()));
		}
		env.add("dir", dir.getName());

		if (DirsToNuke.getDirsToNuke().del(dir)) {
			return new CommandResponse(200, session.jprintf(
					_bundle, _keyPrefix+"autonukes.del.success", env, request.getUser()));
		}
		return new CommandResponse(500, session.jprintf(
				_bundle, _keyPrefix+"autonukes.del.notfound", env, request.getUser()));
	}

	public CommandResponse doSITE_AUTONUKESCAN(CommandRequest request) {
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
        ReplacerEnvironment env = new ReplacerEnvironment();
        for (SectionInterface section : sectionsToCheck) {
			DirectoryHandle sectionRoot = section.getBaseDirectory();
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			request.getSession().printOutput(200, request.getSession().jprintf(
					_bundle, _keyPrefix+"autonukescan.start", env, request.getUser()));

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
                            logger.warn("AutoNuke doSITE_AUTONUKESCAN: FileNotFoundException - {}", releaseDir.getName());
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
				logger.error("",e);
			}  catch (UserFileException e) {
				logger.error("",e);
			}
        }
        if (request.getSession().isAborted()) {
            return new CommandResponse(200, request.getSession().jprintf(
					_bundle, _keyPrefix+"autonukescan.aborted", request.getUser()));
        } else {
            return new CommandResponse(200, request.getSession().jprintf(
					_bundle, _keyPrefix+"autonukescan.complete", request.getUser()));
        }
	}

}
