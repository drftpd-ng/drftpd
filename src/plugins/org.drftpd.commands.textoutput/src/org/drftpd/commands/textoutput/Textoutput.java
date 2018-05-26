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
package org.drftpd.commands.textoutput;

import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;

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
            addTextToResponse(response, request.getProperties().getProperty("file"));

            return response;
        } catch (IOException e) {
            return new CommandResponse(200, "IO Error: " + e.getMessage());
        }
    }
}
