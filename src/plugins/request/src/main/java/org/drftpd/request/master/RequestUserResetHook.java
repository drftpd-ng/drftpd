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
package org.drftpd.request.master;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserResetPostHookInterface;
import org.drftpd.request.master.metadata.RequestUserData;

import java.util.Date;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestUserResetHook implements UserResetPostHookInterface {

    public void init() {
    }

    public void resetHour(Date d) {
        // No need for this interval
    }

    public void resetDay(Date d) {
        // No need for this interval
    }

    public void resetWeek(Date d) {
        // Reset users weekly allotment to zero
        for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
            user.getKeyedMap().setObject(RequestUserData.WEEKREQS, 0);
            user.commit();
        }
    }

    public void resetMonth(Date d) {
        // call resetWeek() instead
        resetWeek(d);
    }

    public void resetYear(Date d) {
        // call resetWeek() instead
        resetWeek(d);
    }
}
