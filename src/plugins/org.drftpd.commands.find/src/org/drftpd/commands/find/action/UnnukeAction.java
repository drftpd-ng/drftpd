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
package org.drftpd.commands.find.action;

import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.nuke.NukeException;
import org.drftpd.commands.nuke.NukeUtils;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author scitz0
 * @version $Id$
 */
public class UnnukeAction implements ActionInterface {
	private boolean _failed;
	private String _reason = "";

	@Override
	public void initialize(String action, String[] args) throws ImproperUsageException {
		if (args != null) {
			_reason = args[0];
		}
	}

	@Override
	public String exec(CommandRequest request, InodeHandle inode) {
		// Try to unnuke dir, if its not previously nuked an NukeException will be thrown.
		NukeData nd;
		try {
			nd = NukeUtils.unnuke((DirectoryHandle) inode, _reason);
		} catch (NukeException e) {
			_failed = true;
			return "Unnuke failed for " + inode.getPath() + ": " + e.getMessage();
		}
		return "Successfully unnuked " + nd.getPath();
	}

	@Override
	public boolean execInDirs() {
		return true;
	}

	@Override
	public boolean execInFiles() {
		return false;
	}

	@Override
	public boolean failed() {
		return _failed;
	}
}
