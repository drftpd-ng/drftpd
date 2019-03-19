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
package org.drftpd.commands.imdb.pre;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.imdb.IMDBConfig;
import org.drftpd.commands.imdb.IMDBPrintThread;
import org.drftpd.commands.imdb.IMDBUtils;
import org.drftpd.commands.pre.Pre;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author scitz0
 */
public class IMDBPREPostHook implements PostHookInterface {

	public void initialize(StandardCommandManager manager) {
	}

	public void doPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// PRE Failed, skip
			return;
		}

		// PRE dir
		DirectoryHandle preDir = response.getObject(Pre.PREDIR, null);
		if (preDir == null) {
			// no pre dir found, how can this be on successful PRE?
			return;
		}

		SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(preDir);
		if (!IMDBUtils.containSection(sec, IMDBConfig.getInstance().getRaceSections()))
			return;

		if (preDir.getName().matches(IMDBConfig.getInstance().getExclude()))
			return;

		// Spawn an IMDB thread and exit.
		// This so its not stalling nfo upload
		IMDBPrintThread imdbPrintThread = new IMDBPrintThread(preDir, sec);
		imdbPrintThread.start();
	}
}