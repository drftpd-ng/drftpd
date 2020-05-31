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
package org.drftpd.master.slavemanagement;

import org.drftpd.master.slavemanagement.RemoteSlave;

/**
 * @author zubov
 * @version $Id$
 */
public class DummyRemoteSlave extends RemoteSlave {
    public DummyRemoteSlave(String name) {
        super(name);
    }

    public int getPort() {
        return 10;
    }

    public void fakeConnect() {
        _errors = 0;
        _lastNetworkError = System.currentTimeMillis();
    }
}
