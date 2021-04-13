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
package org.drftpd.master.commands.usermanagement.expireduser;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author Scitz0
 */
public class ExpiredUserPostHook {

    private ResourceBundle _bundle;

    public void initialize(StandardCommandManager manager) {
        _bundle = manager.getResourceBundle();

    }

    @CommandHook(commands = "doUSER", priority = 1000, type = HookType.POST)
    public void doExpiredUserPostHook(CommandRequest request, CommandResponse response) {
        User myUser;
        try {
            myUser = GlobalContext.getGlobalContext().getUserManager()
                    .getUserByNameUnchecked(request.getArgument());
        } catch (NoSuchUserException | UserFileException ex) {
            return;
        }
        try {
            // Test if metadata exist for user and if so add to response
            Date expiredate = myUser.getKeyedMap().getObject(ExpiredUserData.EXPIRES);
            Map<String, Object> env = new HashMap<>();
            env.put("expiredate", expiredate);
            response.addComment(request.getSession().jprintf(_bundle, "expireduser", env, myUser.getName()));
        } catch (KeyNotFoundException e) {
            // ignore
        }
    }
}