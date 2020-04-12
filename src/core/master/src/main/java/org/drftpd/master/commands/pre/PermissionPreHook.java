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
package org.drftpd.master.commands.pre;

import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;

/**
 * @author zubov
 * @version $Id$
 */
public class PermissionPreHook {

    @CommandHook(commands = "*", type = HookType.PRE)
    public CommandRequestInterface doPermissionCheck(CommandRequest request) {
        // Every command need to enforce permissions
        Permission perm = request.getPermission();
        if (perm == null) {
            request.setDeniedResponse(new CommandResponse(500, "Permissions are not configured for command " + request.getCommand()));
            request.setAllowed(false);
            return request;
        }
        User user = request.getSession().getUserNull(request.getUser());
        // And then check the permission
        if (perm.check(user)) {
            // it worked, you passed the test
            return request;
        }
        request.setDeniedResponse(new CommandResponse(500, "You are not allowed to do this"));
        request.setAllowed(false);
        return request;
    }
}
