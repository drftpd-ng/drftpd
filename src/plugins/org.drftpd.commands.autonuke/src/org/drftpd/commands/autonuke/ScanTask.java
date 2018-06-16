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

import org.drftpd.commands.approve.metadata.Approve;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.TimerTask;

/**
 * Task that runs every minute checking scan queue for items old enough to scan
 * for either incomplete, missing or empty according to nuke configuration
 * @author scitz0
 */
public class ScanTask extends TimerTask {

	public ScanTask() {
	}

	public void run() {
		for (Iterator<DirectoryHandle> iter = DirsToCheck.getDirsToCheck().get().iterator(); iter.hasNext();) {
			DirectoryHandle dir = iter.next();

			boolean isApproved = false;
			try {
				isApproved = dir.getPluginMetaData(Approve.APPROVE);
			} catch (KeyNotFoundException e) {
				// This is ok
			} catch (FileNotFoundException e) {
				// Dir no longer exist, remove it from queue and continue.
				iter.remove();
				continue;
			}

			if (isApproved) {
				iter.remove();
				continue;
			}

			if (AutoNukeManager.getANC().checkConfigs(dir)) {
				iter.remove();
			}
		}
	}

}
