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
package org.drftpd.master.vfs;

import org.drftpd.master.vfs.event.ImmutableInodeHandle;

import java.util.HashSet;
import java.util.List;

/**
 * Builds detached inodes (not part of any virtual file system, nothing written to disk, no
 * event published) to feed the index engine in tests.
 */
public final class IndexTestInodes {

    private IndexTestInodes() {
    }

    public static ImmutableInodeHandle dir(String path, String owner, String group, long lastModified, long size) {
        VirtualFileSystemDirectory dir = new VirtualFileSystemDirectory(owner, group);
        dir.setName(VirtualFileSystem.getLast(path));
        dir.setLastModified(lastModified);
        dir.setSize(size);
        return new ImmutableInodeHandle(dir, path);
    }

    public static ImmutableInodeHandle file(String path, String owner, String group, long lastModified, long size,
                                            String... slaves) {
        VirtualFileSystemFile file = new VirtualFileSystemFile(owner, group, size, new HashSet<>(List.of(slaves)));
        file.setName(VirtualFileSystem.getLast(path));
        file.setLastModified(lastModified);
        return new ImmutableInodeHandle(file, path);
    }
}
