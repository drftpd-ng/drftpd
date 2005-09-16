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
package se.mog.io;

import java.io.IOException;
import java.net.URI;



/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * @version $Id$
 */
public class File extends java.io.File {
    private static FileSystem fs = FileSystem.getFileSystem();

    /**
     * @param pathname
     */
    public File(String pathname) {
        super(pathname);
    }

    public File(java.io.File file) {
        super(file.getPath());
    }

    /**
     * @param parent
     * @param child
     */
    public File(String parent, String child) {
        super(parent, child);
    }

    /**
     * @param parent
     * @param child
     */
    public File(java.io.File parent, String child) {
        super(parent, child);
    }

    /**
     * @param uri
     */
    public File(URI uri) {
        super(uri);
    }

    /**
     * Returns all mounted volumes on the system, this includes file system roots.
     *
     * @see java.io.File#listRoots()
     */
    public static File[] listMounts() throws IOException {
        return fs.listMounts();
    }

    public long getDiskSpaceAvailable() {
        return fs.getDiskFreeSpace(this).freeBytes;
    }

    public long getDiskSpaceCapacity() {
        return fs.getDiskFreeSpace(this).totalBytes;
    }

    public boolean isSymbolicLink() throws IOException {
        return !getCanonicalPath().equals(getAbsolutePath());
    }

    /**
     * Works exactly like <code>{@link java.io.File#delete()}</code> but has the added funcionality of working recursively.
     * @see java.io.File#delete()
     */
    public boolean deleteRecursive() {
        if (isDirectory()) {
            java.io.File[] files = listFiles();

            for (int i = 0; i < files.length; i++) {
                File file = new File(files[i]);
                file.deleteRecursive();
            }
        }

        return super.delete();
    }

    public void delete2() throws PermissionDeniedException {
        if (!super.delete()) {
            throw new PermissionDeniedException("Failed to delete: " + toString());
        }
    }

	public void mkdirs2() throws PermissionDeniedException {
        if (!exists()) {
            if (!mkdirs()) {
                throw new PermissionDeniedException("mkdirs failed on " +
                    getPath());
            }
        }
	}
}
