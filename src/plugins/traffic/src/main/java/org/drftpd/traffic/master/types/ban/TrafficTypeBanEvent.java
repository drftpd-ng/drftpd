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
package org.drftpd.traffic.master.types.ban;

import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.traffic.master.TrafficTypeEvent;

/**
 * @author CyBeR
 * @version $Id: TrafficTypeBanEvent.java 1925 2009-06-15 21:46:05Z cyber $
 */
public class TrafficTypeBanEvent extends TrafficTypeEvent {

    private final long _bantime;

    public TrafficTypeBanEvent(String type, User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, String slavename, long bantime) {
        super(type, user, file, isStor, minspeed, speed, transfered, slavename);
        _bantime = bantime;
    }

    public long getBanTime() {
        return _bantime;
    }
}