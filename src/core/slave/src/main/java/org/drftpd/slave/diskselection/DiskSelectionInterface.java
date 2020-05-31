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
package org.drftpd.slave.diskselection;


import org.drftpd.slave.Slave;
import org.drftpd.slave.vfs.Root;

public abstract class DiskSelectionInterface {
    private final Slave _slave;

    public DiskSelectionInterface(Slave slave) {
        _slave = slave;
    }

    public Slave getSlaveObject() {
        return _slave;
    }

    public abstract Root getBestRoot(String dir);
}
