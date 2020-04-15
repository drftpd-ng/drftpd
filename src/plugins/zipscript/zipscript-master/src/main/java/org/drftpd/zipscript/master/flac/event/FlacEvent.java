/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.zipscript.master.flac.event;

import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.common.flac.FlacInfo;


/**
 * @author norox
 */
public class FlacEvent {

    private final FlacInfo _flacinfo;
    private final DirectoryHandle _dir;
    private final boolean _isFirst;

    public FlacEvent(FlacInfo flacinfo, DirectoryHandle dir, boolean isFirst) {
        _flacinfo = flacinfo;
        _dir = dir;
        _isFirst = isFirst;
    }

    public DirectoryHandle getDir() {
        return _dir;
    }

    public FlacInfo getFlacInfo() {
        return _flacinfo;
    }

    public boolean isFirst() {
        return _isFirst;
    }
}
