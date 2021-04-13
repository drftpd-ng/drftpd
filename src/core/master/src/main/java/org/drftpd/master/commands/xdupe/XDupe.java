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
package org.drftpd.master.commands.xdupe;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;

public class XDupe extends CommandInterface {

    public static final Key<Integer> XDUPE = new Key<>(XDupe.class, "XDUPE");

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _featReplies = new String[]{
                "SITE XDUPE"
        };
    }

    public CommandResponse doXDUPE(CommandRequest request) {
        int xDupe = request.getSession().getObjectInteger(XDUPE);

        if (!request.hasArgument()) {
            if (xDupe == 0) {
                return new CommandResponse(200, "Extended dupe mode is disabled.");
            }
            return new CommandResponse(200, "Extended dupe mode " + xDupe + " is enabled.");
        }
        int myXdupe;

        try {
            myXdupe = Integer.parseInt(request.getArgument());
        } catch (NumberFormatException ex) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        if (myXdupe < 0 || myXdupe > 4) {
            return StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
        }

        request.getSession().setObject(XDUPE, myXdupe);
        return new CommandResponse(200, "Activated extended dupe mode " + myXdupe + ".");
    }
}
