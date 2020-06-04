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
package org.drftpd.master.commands.nuke;

import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.vfs.VirtualFileSystem;

/**
 * Nuke PreHook.
 *
 * @author fr0w
 * @version $Id$
 */
public class NukePreHook {

    @CommandHook(commands = {"doMKD", "doRNTO"}, priority = 1000, type = HookType.PRE)
    public CommandRequestInterface doNukeCheck(CommandRequest request) {
        String path = VirtualFileSystem.fixPath(request.getArgument());

        if (!path.startsWith(VirtualFileSystem.separator)) {
            // Create full path
            if (request.getCurrentDirectory().isRoot()) {
                path = VirtualFileSystem.separator + path;
            } else {
                path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + path;
            }
        }

        NukeData nd = NukeBeans.getNukeBeans().findPath(path);

        if (nd != null) {
            // This path exist in nukelog
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(530, "Access denied - " +
                    nd.getPath() + " already nuked for '" + nd.getReason() + "'"));
        }

        return request;
    }
}
