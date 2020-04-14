/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.master.commands.usermanagement.expireduser;

import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.util.Date;


/*
 * @author CyBeR
 */
public class ExpiredUserPreHook {

    @CommandHook(commands = "doUSER", type = HookType.PRE)
    public CommandRequestInterface doUSERPreHook(CommandRequest request) {
    	BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
    	request.getSession().setObject(BaseFtpConnection.FAILEDLOGIN, true);
        conn.setAuthenticated(false);
        conn.setUser(null);

        // argument check
        if (!request.hasArgument()) {
        	return request;
        }

        User newUser;

        try {
            newUser = conn.getGlobalContext().getUserManager().getUserByNameIncludeDeleted(request.getArgument());
        } catch (NoSuchUserException | UserFileException | RuntimeException ex) {
        	return request;
        }

        if (newUser.isDeleted()) {
        	return request;
        }

        if (newUser.getKeyedMap().getObject(ExpiredUserData.EXPIRES,new Date(4102441199000L)).before(new Date())) {
			request.setDeniedResponse(StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED"));
			request.setAllowed(false); 
        }
		return request;
    }
}