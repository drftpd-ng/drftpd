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
package org.drftpd.tvmaze.master.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.pre.Pre;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.tvmaze.master.TvMazeConfig;
import org.drftpd.tvmaze.master.TvMazePrintThread;
import org.drftpd.tvmaze.master.TvMazeUtils;

import java.io.FileNotFoundException;

/**
 * @author scitz0
 */

public class TvMazeHooks {
    private static final Logger logger = LogManager.getLogger(TvMazeHooks.class);

    @CommandHook(commands = "doPRE", priority = 100, type = HookType.POST)
    public void doSitePrePostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // PRE Failed, skip
            logger.debug("Response code is not 250, skipping");
            return;
        }

        // PRE dir
        DirectoryHandle dirHandle = response.getObject(Pre.PREDIR, null);
        if (dirHandle == null) {
            // no pre dir found, how can this be on successful PRE?
            logger.debug("PREDIR Object not set, how can this be a successful pre??, skipping");
            return;
        }

        checkForTvMazeAction(dirHandle);
    }

    @CommandHook(commands = "doMKD", priority = 1000, type = HookType.POST)
    public void doMKDPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 257) {
            // MKD Failed, skip check
            return;
        }

        DirectoryHandle dirHandle;
        try {
            dirHandle = request.getCurrentDirectory().getDirectoryUnchecked(request.getArgument());
        } catch (FileNotFoundException | ObjectNotValidException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument());
            return;
        }

        if (!TvMazeUtils.isRelease(dirHandle.getName())) {
            logger.debug("Not a release according TvMaze naming convention");
            return;
        }

        checkForTvMazeAction(dirHandle);
    }

    private void checkForTvMazeAction(DirectoryHandle dirHandle) {
        SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dirHandle);
        if (!TvMazeUtils.containSection(sec, TvMazeConfig.getInstance().getRaceSections())) {
            logger.debug("No TvMaze configuration found, skipping");
            return;
        }

        if (dirHandle.getName().matches(TvMazeConfig.getInstance().getExclude())) {
            logger.debug("Excluded from TvMaze by configuration, skipping");
            return;
        }

        // Spawn a TvMazePrintThread and exit.
        // This so its not stalling the command execution
        (new TvMazePrintThread(dirHandle, sec)).start();
    }
}