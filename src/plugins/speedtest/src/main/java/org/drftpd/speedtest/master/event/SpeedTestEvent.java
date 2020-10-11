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
package org.drftpd.speedtest.master.event;

import org.drftpd.common.slave.TransferStatus;
import org.drftpd.master.usermanager.User;

/**
 * @author scitz0
 */
public class SpeedTestEvent {
    String _filePath;
    String _slaveName;
    TransferStatus _status;
    User _user;

    public SpeedTestEvent(String path, String slave, TransferStatus status, User user) {
        _filePath = path;
        _slaveName = slave;
        _status = status;
        _user = user;
    }

    public String getFilePath() {
        return _filePath;
    }

    public String getSlaveName() {
        return _slaveName;
    }

    public TransferStatus getStatus() {
        return _status;
    }

    public User getUser() {
        return _user;
    }
}