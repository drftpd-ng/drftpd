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
package org.drftpd.autonuke.master;

import org.drftpd.master.vfs.DirectoryHandle;

/**
 * Item that holds nuke data
 *
 * @author scitz0
 */
public class NukeItem {
    private final long _time;
    private final DirectoryHandle _dir;
    private final String _reason;
    private final int _mult;
    private final boolean _isSubdir;
    private final boolean _debug;

    public NukeItem(long time, DirectoryHandle dir, String reason, int mult, boolean isSubdir, boolean debug) {
        _time = time;
        _dir = dir;
        _reason = reason;
        _mult = mult;
        _isSubdir = isSubdir;
        _debug = debug;
    }

    public long getTime() {
        return _time;
    }

    public DirectoryHandle getDir() {
        return _dir;
    }

    public String getReason() {
        return _reason;
    }

    public int getMultiplier() {
        return _mult;
    }

    public boolean isSubdir() {
        return _isSubdir;
    }

    public boolean debug() {
        return _debug;
    }
}
