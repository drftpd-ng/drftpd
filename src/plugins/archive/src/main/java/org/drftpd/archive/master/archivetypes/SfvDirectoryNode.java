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
package org.drftpd.archive.master.archivetypes;

import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link ReleaseCompleteness.Node} backed by the zipscript SFV data of a directory. This is the only class of
 * the archive plugin that needs the zipscript plugin, and only used when skipincomplete is enabled.
 */
final class SfvDirectoryNode implements ReleaseCompleteness.Node {

    private final DirectoryHandle _dir;

    SfvDirectoryNode(DirectoryHandle dir) {
        _dir = dir;
    }

    @Override
    public Boolean sfvFinished() throws Exception {
        try {
            return new ZipscriptVFSDataSFV(_dir).getSFVStatus().isFinished();
        } catch (FileNotFoundException e) {
            // no .sfv in this directory
            return null;
        }
    }

    @Override
    public Collection<? extends ReleaseCompleteness.Node> children() throws FileNotFoundException {
        Collection<SfvDirectoryNode> children = new ArrayList<>();
        for (DirectoryHandle child : _dir.getDirectoriesUnchecked()) {
            children.add(new SfvDirectoryNode(child));
        }
        return children;
    }

    @Override
    public String toString() {
        return _dir.getPath();
    }
}
