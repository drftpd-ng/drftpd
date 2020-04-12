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
package org.drftpd.tvmaze.master.hooks;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.drftpd.tvmaze.master.TvMazeConfig;
import org.drftpd.tvmaze.master.TvMazePrintThread;
import org.drftpd.tvmaze.master.TvMazeUtils;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;

import java.io.FileNotFoundException;

/**
 * @author lh
 */
public class TvMazePostHook  {
	private static final Logger logger = LogManager.getLogger(TvMazePostHook.class);

	@CommandHook(commands = "doMKD", priority = 1000, type = HookType.POST)
	public void doMKDPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 257) {
			// MKD Failed, skip check
			return;
		}

		DirectoryHandle workingDir;
		try {
			workingDir = request.getCurrentDirectory().getDirectoryUnchecked(request.getArgument());
		} catch (FileNotFoundException | ObjectNotValidException e) {
            logger.error("Failed getting DirectoryHandle for {}", request.getArgument());
			return;
		}

		if (!TvMazeUtils.isRelease(workingDir.getName())) {
			return;
		}

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
		if (!TvMazeUtils.containSection(sec, TvMazeConfig.getInstance().getRaceSections())) {
			return;
		}

		if (workingDir.getName().matches(TvMazeConfig.getInstance().getExclude())) {
			return;
		}

		// Spawn a TvMazePrintThread and exit.
		// This so its not stalling MKD
		TvMazePrintThread tvmaze = new TvMazePrintThread(workingDir, sec);
		tvmaze.start();
	}
}