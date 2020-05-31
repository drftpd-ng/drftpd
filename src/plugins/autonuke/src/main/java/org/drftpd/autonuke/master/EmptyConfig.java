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
package org.drftpd.autonuke.master;

import org.drftpd.master.vfs.DirectoryHandle;

import java.util.Properties;

/**
 * @author scitz0
 */
public class EmptyConfig extends Config {

    public EmptyConfig(int i, Properties p) {
        super(i, p);
    }

    /**
     * Boolean to return empty status
     * Minimum percent optional
     *
     * @param configData Object holding return data
     * @param dir        Directory currently being handled
     * @return Return false if dir should be nuked, else true
     */
    public boolean process(ConfigData configData, DirectoryHandle dir) {
        return !dir.getAllFilesRecursiveUnchecked().isEmpty();
    }

}
