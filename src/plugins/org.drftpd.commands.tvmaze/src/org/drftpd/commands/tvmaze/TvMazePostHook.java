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
package org.drftpd.commands.tvmaze;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;

/**
 * @author lh
 */
public class TvMazePostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(TvMazePostHook.class);

	public void initialize(StandardCommandManager manager) {
	}

	public void doMKDPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 257) {
			// MKD Failed, skip check
			return;
		}

		DirectoryHandle workingDir;
		try {
			workingDir = request.getCurrentDirectory().getDirectoryUnchecked(request.getArgument());
		} catch (FileNotFoundException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument());
			return;
		} catch (ObjectNotValidException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument());
			return;
		}

		if (!TvMazeUtils.isRelease(workingDir.getName())) return;

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
		if (!TvMazeUtils.containSection(sec, TvMazeConfig.getInstance().getRaceSections())) return;

		if (workingDir.getName().matches(TvMazeConfig.getInstance().getExclude())) return;

		// Spawn a TvMazePrintThread and exit.
		// This so its not stalling MKD
		TvMazePrintThread tvmaze = new TvMazePrintThread(workingDir, sec);
		tvmaze.start();
	}
}