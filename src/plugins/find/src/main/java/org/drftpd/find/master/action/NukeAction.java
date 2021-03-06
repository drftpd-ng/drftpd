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
package org.drftpd.find.master.action;

import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.commands.nuke.NukeBeans;
import org.drftpd.master.commands.nuke.NukeException;
import org.drftpd.master.commands.nuke.NukeUtils;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.VirtualFileSystem;

/**
 * @author scitz0
 * @version $Id$
 */
public class NukeAction implements ActionInterface {
    private boolean _failed;
    private int _multiplier;
    private String _reason = "";

    @Override
    public String name() {
        return "Nuke";
    }

    @Override
    public void initialize(String action, String[] args) throws ImproperUsageException {
        if (args == null) {
            throw new ImproperUsageException("Missing argument for " + action + " action");
        }
        // -nuke <multiplier> [reason]
        _multiplier = Integer.parseInt(args[0]);
        if (args.length == 2) {
            _reason = args[1];
        }
    }

    @Override
    public String exec(CommandRequest request, InodeHandle inode) {
        DirectoryHandle dir = (DirectoryHandle) inode;

        // Check if dir is nuked already, remove nuke prefix if necessary
        String dirName = VirtualFileSystem.getLast(
                NukeUtils.getPathWithoutNukePrefix(dir.getPath()));
        NukeData nd = NukeBeans.getNukeBeans().findName(dirName);
        if (nd != null) {
            _failed = true;
            return "Access denied - " + nd.getPath() + " already nuked for '" + nd.getReason() + "'";
        }

        User user = request.getSession().getUserNull(request.getUser());
        try {
            nd = NukeUtils.nuke(dir, _multiplier, _reason, user);
        } catch (NukeException e) {
            _failed = true;
            return "Nuke failed for " + inode.getPath() + ": " + e.getMessage();
        }
        return "Successfully nuked " + nd.getPath();
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
