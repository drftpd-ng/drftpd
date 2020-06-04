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
package org.drftpd.nukefilter.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.approve.metadata.Approve;
import org.drftpd.master.commands.nuke.NukeEvent;
import org.drftpd.master.commands.nuke.NukeException;
import org.drftpd.master.commands.nuke.NukeUtils;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.TimerTask;

/**
 * @author phew
 */
public class NukeFilterNukeTask extends TimerTask {
    private static final Logger logger = LogManager.getLogger(NukeFilterNukeTask.class);

    private final DirectoryHandle dir;
    private final String reason;
    private final int nukex;

    public NukeFilterNukeTask(NukeFilterNukeItem nfni) {
        dir = nfni.getDirectoryHandle();
        reason = nfni.getReason();
        nukex = nfni.getNukex();
    }

    public void run() {
        if (dir.getName().startsWith("[NUKED]-")) {
            //do not try nuking a nuked directory
            return;
        }

        if (Approve.isApproved(dir)) {
            return;
        }

        User nuker;
        try {
            nuker = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(
                    NukeFilterManager.getNukeFilterManager().getNukeFilterSettings().getNuker());
        } catch (NoSuchUserException | UserFileException e) {
            logger.error("error loading nuker: {}", e.getMessage());
            return;
        }

        NukeData nd;
        try {
            nd = NukeUtils.nuke(dir, nukex, reason, nuker);
        } catch (NukeException e) {
            logger.error("error nuking: {}", e.getMessage());
            return;
        }
        NukeEvent nuke = new NukeEvent(nuker, "NUKE", nd);
        GlobalContext.getEventService().publishAsync(nuke);
    }

}
