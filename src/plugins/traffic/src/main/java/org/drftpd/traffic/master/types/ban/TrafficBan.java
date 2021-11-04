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

import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.FtpReply;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.traffic.master.TrafficType;

import java.util.Date;
import java.util.Properties;

/**
 * @author CyBeR
 * @version $Id: TrafficBan.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficBan extends TrafficType {

    private final String _reason;
    private final long _bantime;
    private final boolean _kickall;

    public TrafficBan(Properties p, int confnum, String type) {
        super(p, confnum, type);

        _reason = p.getProperty(confnum + ".reason", "Trasnfering Too Slow").trim();

        try {
            _bantime = Integer.parseInt(p.getProperty(confnum + ".bantime", "300").trim()) * 1000;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid BanTime for " + confnum + ".bantime - Skipping Config");
        }

        _kickall = p.getProperty(confnum + ".kickall", "true").trim().equalsIgnoreCase("true");
    }

    @Override
    public void doAction(User user, FileHandle file, boolean isStor, long minspeed, long speed, long transfered, BaseFtpConnection conn, String slavename) {
        user.getKeyed().setObject(UserManagement.BANTIME, new Date(System.currentTimeMillis() + _bantime));
        user.getKeyed().setObject(UserManagement.BANREASON, _reason);
        user.commit();

        if (_kickall) {
            for (BaseFtpConnection connection : GlobalContext.getConnectionManager().getConnections()) {
                if (connection.getUsername().equals(user.getName())) {
                    connection.printOutput(new FtpReply(426, _reason));
                    if (isStor) {
                        connection.abortCommand();
                    }
                    if (doDelete(file)) {
                        try {
                            wait(1000);
                        } catch (InterruptedException e) {

                        }
                    }
                    connection.stop();
                }
            }
        } else {
            conn.printOutput(new FtpReply(426, _reason));
            if (isStor) {
                conn.abortCommand();
            }
            if (doDelete(file)) {
                try {
                    wait(1000);
                } catch (InterruptedException e) {

                }
            }
            conn.stop();
        }
        GlobalContext.getEventService().publishAsync(new TrafficTypeBanEvent(getType(), user, file, isStor, minspeed, speed, transfered, slavename, _bantime));
    }

}
