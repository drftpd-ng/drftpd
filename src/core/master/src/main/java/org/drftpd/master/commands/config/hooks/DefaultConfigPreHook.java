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
package org.drftpd.master.commands.config.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.Master;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.commands.usermanagement.UserManagementHandler;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.vfs.perms.VFSPermissions;

import java.util.List;

/**
 * PreHooks that implements some of the functionalities "required" by the directives in perms.conf
 *
 * @author fr0w
 * @version $Id$
 */
public class DefaultConfigPreHook {

    protected static final Logger logger = LogManager.getLogger(DefaultConfigPreHook.class);

    @CommandHook(commands = {"doBW", "doIdlers", "doLeechers", "doSWHO", "doWHO", "doSpeed", "doUploaders"},
            priority = 2, type = HookType.PRE)
    public CommandRequestInterface hideInWhoHook(CommandRequest request) {
        List<BaseFtpConnection> conns = Master.getConnectionManager().getConnections();
        ConfigInterface cfg = GlobalContext.getConfig();

        conns.removeIf(conn -> cfg.checkPathPermission("hideinwho", conn.getUserNull(), conn.getCurrentDirectory()));

        request.getSession().setObject(UserManagementHandler.CONNECTIONS, conns);

        return request;
    }

    @CommandHook(commands = "doRETR", priority = 2, type = HookType.PRE)
    public CommandRequestInterface checkDownloadPermsHook(CommandRequest request) {
        VFSPermissions vfsPerms = GlobalContext.getConfig().getVFSPermissions();

        if (!vfsPerms.checkPathPermission("download", request.getSession().getUserNull(request.getUser()), request.getCurrentDirectory().getNonExistentFileHandle(request.getArgument()))) {
            request.setAllowed(false);
            request.setDeniedResponse(StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED"));
        }

        return request;
    }
}
