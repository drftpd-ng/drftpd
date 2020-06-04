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
package org.drftpd.imdb.master.event;

import org.drftpd.master.vfs.DirectoryHandle;

import java.util.Map;

/**
 * @author lh
 */
public class IMDBEvent {

    private final Map<String, Object> _env;
    private final DirectoryHandle _dir;

    public IMDBEvent(Map<String, Object> env, DirectoryHandle dir) {
        _env = env;
        _dir = dir;
    }

    public Map<String, Object> getEnv() {
        return _env;
    }

    public DirectoryHandle getDir() {
        return _dir;
    }
}