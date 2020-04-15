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
package org.drftpd.find.master.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;

/**
 * @author scitz0
 * @version $Id$
 */
public class WipeAction implements ActionInterface {
    public static final Logger logger = LogManager.getLogger(WipeAction.class);

    private boolean _failed;

    @Override
    public String name() {
        return "Wipe";
    }

    @Override
    public void initialize(String action, String[] args) throws ImproperUsageException {
    }

    @Override
    public String exec(CommandRequest request, InodeHandle inode) {
        try {
            inode.delete(request.getSession().getUserNull(request.getUser()));
        } catch (FileNotFoundException e) {
            logger.error("The file was there and now it's gone, how?", e);
        } catch (PermissionDeniedException e) {
            _failed = true;
            return "You do not have the proper permissions to wipe " + inode.getPath();
        }
        return "Wiped " + inode.getPath();
    }

    @Override
    public boolean execInDirs() {
        return true;
    }

    @Override
    public boolean execInFiles() {
        return true;
    }

    @Override
    public boolean failed() {
        return _failed;
    }
}
