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
package org.drftpd.links.master.types.latestdir;

import org.drftpd.links.master.LinkType;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.Properties;

/**
 * @author freasy
 * @version $Id: LatestDir.java freasy $
 */

public class LatestDir extends LinkType {

    public LatestDir(Properties p, int confnum, String type) {
        super(p, confnum, type);
    }

    /*
     * This just passes the target dir through to creating the Link
     * No special setup is needed for this type.
     */
    @Override
    public void doCreateLink(DirectoryHandle targetDir) {
        createLink(targetDir, targetDir.getPath(), targetDir.getName());
    }

    /*
     * This just passes the target dir through to creating the Link
     * No special setup is needed for this type.
     */
    @Override
    public void doDeleteLink(DirectoryHandle targetDir) {
        deleteLink(targetDir, targetDir.getPath(), targetDir.getName());

    }

    public void doFixLink(DirectoryHandle dirHandle) {

    }
}
