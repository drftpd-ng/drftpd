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
package org.drftpd.links.master.types.sfvmissing;

import org.drftpd.links.master.LinkType;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 * @version $Id: SFVMissing.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class SFVMissing extends LinkType {

    public SFVMissing(Properties p, int confnum, String type) {
        super(p, confnum, type);
    }

    /*
     * This checks if any Dir from inside the targetDir matches
     * AddParentDir, and if it does, remove the link, if not create one
     */
    @Override
    public void doCreateLink(DirectoryHandle targetDir) {
        try {
            if (targetDir.getName().matches(getAddParentDir())) {
                doDeleteLink(targetDir.getParent());
            }

            Set<DirectoryHandle> dirs = targetDir.getDirectoriesUnchecked();
            for (DirectoryHandle dir : dirs) {
                if (dir.getName().matches(getAddParentDir())) {
                    doDeleteLink(targetDir);
                    return;
                }
            }
            createLink(targetDir, targetDir.getPath(), targetDir.getName());
        } catch (FileNotFoundException e) {
            // targetDir No Longer Exists
        }
    }

    /*
     * This just passes the target dir through to creating the Link
     * No special setup is needed for this type.
     */
    @Override
    public void doDeleteLink(DirectoryHandle targetDir) {
        deleteLink(targetDir, targetDir.getPath(), targetDir.getName());
    }

    /*
     * This loops though the files, and checks to see if any end with .sfv
     * If one does, it creates the link, if not, it deletes the link
     */
    @Override
    public void doFixLink(DirectoryHandle targetDir) {
        try {
            for (FileHandle file : targetDir.getFilesUnchecked()) {
                if (file.getName().toLowerCase().endsWith(".sfv")) {
                    doDeleteLink(targetDir);
                    return;
                }
            }
            doCreateLink(targetDir);
        } catch (FileNotFoundException e) {
            // No Files found - Ignore
        }
    }

}
