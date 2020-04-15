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
package org.drftpd.traffic.master.types.def;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.traffic.master.TrafficType;
import org.drftpd.traffic.master.TrafficTypeEvent;

import java.util.Properties;

/**
 * @author CyBeR
 * @version $Id: TrafficDefault.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficDefault extends TrafficType {

    public TrafficDefault(Properties p, int confnum, String type) {
        super(p, confnum, type);
    }

    @Override
    public void doAction(User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, BaseFtpConnection conn, String slavename) {
        /*
         * Don't do anything, just publish Event for announce.
         */
        GlobalContext.getEventService().publishAsync(new TrafficTypeEvent(getType(), user, file, isStor, minspeed, speed, transfered, slavename));
    }

}
