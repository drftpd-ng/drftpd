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
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sections.conf.DatedSection;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;

/**
 * Hooks on MKDIR to add directory to scan queue and RMD/WIPE to clean queue
 *
 * @author scitz0
 */
public class AutoNukeHook {
    private static final Logger logger = LogManager.getLogger(AutoNukeHook.class);

    @CommandHook(commands = "doMKD", priority = 10, type = HookType.POST)
    public void doMKDPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 257) {
            // MKD Failed, skip check
            return;
        }
        // Get DirectoryHandle for dir created
        DirectoryHandle newDir;
        try {
            newDir = request.getCurrentDirectory().getDirectoryUnchecked(request.getArgument());
        } catch (FileNotFoundException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument(), e);
            return;
        } catch (ObjectNotValidException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument(), e);
            return;
        }
        // Get section for this mkd
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(newDir);
        // Section globaly excluded?
        if (AutoNukeSettings.getSettings().getExcludedSections().contains(section)) return;
        // Dir globaly excluded?
        if (newDir.getName().matches(AutoNukeSettings.getSettings().getExcludedDirs())) return;
        // Check if parent dir is section root or root of a dated dir, if not return
        try {
            if (section instanceof DatedSection) {
                // Parents parent dir same as base directory?
                if (!section.getBaseDirectory().equals(newDir.getParent().getParent())) {
                    // Not a release dir
                    return;
                }
            } else {
                if (!section.getBaseDirectory().equals(newDir.getParent())) {
                    // Not a release dir
                    return;
                }
            }
        } catch (Exception e) {
            // Unable to check if this is a release dir or not
            return;
        }
        // return if any matching sub directories exist in _excludeSubDirs
        try {
            for (DirectoryHandle subDir : newDir.getDirectoriesUnchecked()) {
                if (subDir.getName().matches(AutoNukeSettings.getSettings().getExcludedSubDirs())) {
                    return;
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("AutoNuke doMKDPostHook: FileNotFoundException - {}", newDir.getName());
            return;
        }
        // Dir valid, add it to queue
        DirsToCheck.getDirsToCheck().add(newDir);
    }

    @CommandHook(commands = {"doRMD", "doWIPE"}, priority = 10, type = HookType.POST)
    public void doAutoNukeCleanupHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250 && response.getCode() != 200) {
            // DELE/WIPE failed, abort cleanup
            return;
        }
        String path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + request.getArgument();
        DirsToCheck.getDirsToCheck().del(path);
        DirsToNuke.getDirsToNuke().del(path);
    }
}
