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
package org.drftpd.slave;

import se.mog.io.File;

import java.io.IOException;


/**
 * @author mog
 * @version $Id$
 */
public class Root {
    private File _rootFile;
    private String _root;
    private long _minFreeSpace = 50000000; //50,000,000 = 50mb
    private int _priority = 0;
    private long _lastModified;

    public Root(String root) throws IOException {
        _rootFile = new File(new File(root).getCanonicalFile());
        _root = _rootFile.getPath();
        _lastModified = getFile().lastModified();
    }

    public File getFile() {
        return _rootFile;
    }

    public String getPath() {
        return _root;
    }

    public long lastModified() {
        return _lastModified;
    }

    public void touch() {
        getFile().setLastModified(_lastModified = System.currentTimeMillis());
    }

    /**
     * @deprecated MinFreeSpacen now depends DiskSelection
     */
    public long getMinSpaceFree() {
        return _minFreeSpace;
    }

    /**
     * @deprecated Priority now depens on DiskSelection
     */
    public int getPriority() {
        return _priority;
    }

    public String toString() {
        return "[root=" + getPath() + "]";
    }

    public long getDiskSpaceAvailable() {
        return getFile().getDiskSpaceAvailable();
    }

    public long getDiskSpaceCapacity() {
        return getFile().getDiskSpaceCapacity();
    }

    public File getFile(String path) {
        return new File(_root + File.separator + path);
    }

    /**
     * @return true if File.getDiskSpaceAvailable() is less than getMinSpaceFree()
     * @deprecated Check DiskSelection for more details.
     */
    public boolean isFull() {
        return getFile().getDiskSpaceAvailable() < getMinSpaceFree();
    }
}
