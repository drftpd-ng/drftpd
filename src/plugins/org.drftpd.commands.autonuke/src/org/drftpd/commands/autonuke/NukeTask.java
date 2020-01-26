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
import org.drftpd.commands.approve.metadata.Approve;
import org.drftpd.commands.nuke.NukeException;
import org.drftpd.commands.nuke.NukeUtils;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.NukeEvent;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.Iterator;
import java.util.TimerTask;

/**
 * Task that runs every minute checking nuke queue for items old enough to nuke
 * @author scitz0
 */
public class NukeTask extends TimerTask {
	private static final Logger logger = LogManager.getLogger(NukeTask.class);

	public NukeTask() {
	}

	public void run() {
		for (Iterator<NukeItem> iter = DirsToNuke.getDirsToNuke().get().iterator(); iter.hasNext();) {
			NukeItem ni = iter.next();
			if (System.currentTimeMillis() >= ni.getTime()) {
				DirectoryHandle dir;
				if (ni.isSubdir()) {
					dir = ni.getDir().getParent();
				} else {
					dir = ni.getDir();
				}

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

				NukeItem updatedNI = AutoNukeManager.getANC().getConfigChain().simpleConfigCheck(dir);
				if (updatedNI != null && !isApproved) {
					// Dir still not ok and not approved, nuke it!
					if (AutoNukeSettings.getSettings().debug() || updatedNI.debug()) {
                        logger.debug("NukeTask: NUKE {}X {} with reason: {}", updatedNI.getMultiplier(), dir.getPath(), updatedNI.getReason());
					} else {
						doNuke(dir, updatedNI.getMultiplier(), updatedNI.getReason());
					}
				} else {
                    logger.debug("{} was flagged to be nuked but is now ok or set approved, skipping nuke!", dir.getPath());
				}
				iter.remove();
			}
		}
	}

	/**
	 * Function to nuke directory.
	 * @param	dir 		Directory being nuked
	 * @param 	multiplier 	Credit multiplier
	 * @param 	reason		The reason for the nuke
	 */
	public void doNuke(DirectoryHandle dir, int multiplier, String reason) {
		User nukeUser;
		try {
			nukeUser = GlobalContext.getGlobalContext().getUserManager().
					getUserByNameUnchecked(AutoNukeSettings.getSettings().getNukeUser());
		} catch (NoSuchUserException nsue) {
			logger.error(nsue.getMessage());
			return;
		} catch (UserFileException ufe) {
			logger.error(ufe.getMessage());
			return;
		}

		NukeData nd;
		try {
			nd = NukeUtils.nuke(dir, multiplier, reason, nukeUser);
		} catch (NukeException e) {
			logger.error(e,e);
			return;
		}

		NukeEvent nuke = new NukeEvent(nukeUser, "NUKE", nd);
		GlobalContext.getEventService().publishAsync(nuke);
	}
}
