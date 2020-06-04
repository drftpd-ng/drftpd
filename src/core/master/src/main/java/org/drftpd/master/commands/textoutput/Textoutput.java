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
package org.drftpd.master.commands.textoutput;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.commands.CommandInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.StandardCommandManager;

import java.io.IOException;


/**
 * @author mog
 * @version $Id$
 */
public class Textoutput extends CommandInterface {

    public CommandResponse doTEXT_OUTPUT(CommandRequest request) {
        if (request.hasArgument()) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        try {
            CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
            String fileToDisplay = request.getProperties().getProperty("file");
            response.addComment(ConfigLoader.loadTextFile(fileToDisplay));
            return response;
        } catch (IOException e) {
            return new CommandResponse(200, "IO Error: " + e.getMessage());
        }
    }
}
