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
package org.drftpd.slave.vfs;

import org.drftpd.common.io.PhysicalFile;

import java.io.IOException;

/**
 * @author mog
 * @version $Id$
 */
public class Root {
    private static final String separator = "/";
    private final PhysicalFile _rootFile;
    private long _lastModified;

    public Root(String root) throws IOException {
        _rootFile = new PhysicalFile(new PhysicalFile(root).getCanonicalFile());
        _lastModified = getFile().lastModified();
    }

    public PhysicalFile getFile() {
        return _rootFile;
    }

    public String getPath() {
        return _rootFile.getPath();
    }

    public long lastModified() {
        return _lastModified;
    }

    public void touch() {
        getFile().setLastModified(_lastModified = System.currentTimeMillis());
    }

    public String toString() {
        return "[root=" + getPath() + "]";
    }

    public long getDiskSpaceAvailable() {
        return getFile().getUsableSpace();
    }

    public long getDiskSpaceCapacity() {
        return getFile().getTotalSpace();
    }

    public PhysicalFile getFile(String path) {
        return new PhysicalFile(getPath() + separator + path);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    //@Override
    public boolean equals(Object arg0) {
        if (!(arg0 instanceof Root)) {
            return false;
        }
        Root r = (Root) arg0;
        return r.getPath().equals(getPath());
    }
}
