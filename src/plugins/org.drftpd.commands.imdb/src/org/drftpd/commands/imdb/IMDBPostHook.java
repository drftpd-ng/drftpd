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
package org.drftpd.commands.imdb;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;

/**
 * @author scitz0
 */
public class IMDBPostHook implements PostHookInterface {
	public void initialize(StandardCommandManager manager) {
	}

	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR Failed, skip
			return;
		}

		String fileName = request.getArgument();
		if (!fileName.endsWith(".nfo") || fileName.endsWith("imdb.nfo"))
			return;

		DirectoryHandle workingDir = request.getCurrentDirectory();

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
		if (!IMDBUtils.containSection(sec, IMDBConfig.getInstance().getRaceSections()))
			return;

		// Spawn an IMDBPrintThread and exit.
		// This so its not stalling nfo upload
		IMDBPrintThread imdb = new IMDBPrintThread(workingDir, sec);
		imdb.start();
	}

	public void doIMDBCleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250 && response.getCode() != 200) {
			// DELE/WIPE failed, abort cleanup
			return;
		}
		String deleFileName;
		deleFileName = request.getArgument().toLowerCase();
		if (deleFileName.endsWith(".nfo") && !deleFileName.endsWith("imdb.nfo")) {
			try {
				request.getCurrentDirectory().removePluginMetaData(IMDBInfo.IMDBINFO);
			} catch(FileNotFoundException e) {
				// No inode to remove imdb info from
			}
		}
	}
}
