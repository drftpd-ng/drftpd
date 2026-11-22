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
package org.drftpd.zipscript.master.sfv.banned;

import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.vfs.FileHandle;

/**
 * Refuses STOR of files whose name matches {@code banned.filenames} in zipscript.conf (off when empty).
 */
public class ZipscriptBannedFilenamesPreHook {

    @CommandHook(commands = "doSTOR", priority = 5, type = HookType.PRE)
    public CommandRequestInterface doBannedFilenamesPreCheck(CommandRequest request) {
        if (!request.hasArgument()) {
            return request;
        }
        BannedFilenames banned = BannedFilenames.fromProperties(ConfigLoader.loadPluginConfig("zipscript.conf"));
        if (banned.isEmpty()) {
            return request;
        }
        FileHandle file = request.getCurrentDirectory().getNonExistentFileHandle(request.getArgument());
        if (banned.isBanned(file.getParent().getPath(), file.getName())) {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(553,
                    "Requested action not taken. File name is not allowed on this site."));
        }
        return request;
    }
}
