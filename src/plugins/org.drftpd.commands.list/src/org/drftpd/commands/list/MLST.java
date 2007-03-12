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
package org.drftpd.commands.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;


import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.FtpReply;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandleInterface;
import org.drftpd.vfs.ObjectNotValidException;


/**
 * @author mog
 * @version $Id$
 */
public class MLST extends CommandInterface {
    private static final Logger logger = Logger.getLogger(MLST.class);

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_featReplies = new String[] {
            "MLST type*,x.crc32*,size*,modify*,unix.owner*,unix.group*,x.slaves*,x.xfertime*"
        };
    }

    public CommandResponse doMLSTandMLSD(CommandRequest request) {
        String command = request.getCommand();

        DirectoryHandle dir = request.getCurrentDirectory();

        if (request.hasArgument()) {
            try {
                try {
					dir = dir.getDirectory((request.getArgument()));
				} catch (ObjectNotValidException e) {
					return new CommandResponse(500, "Target is not a directory, MLST only works on Directories");
				}
            } catch (FileNotFoundException e) {
                return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
            }
        }

        if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("privpath", 
        		request.getSession().getUserNull(request.getUser()), dir, true)) {
        	return StandardCommandManager.genericResponse("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN");
        }

        BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
        PrintWriter out = conn.getControlWriter();

        if ("MLST".equalsIgnoreCase(command)) {
            out.print("250- Begin\r\n");
            out.print(toMLST(dir) + "\r\n");
            out.print("250 End.\r\n");

            return null;
        } else if ("MLSD".equalsIgnoreCase(command)) {
        	
            if (!conn.getTransferState().isPasv() && !conn.getTransferState().isPort()) {
            	return StandardCommandManager.genericResponse("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS");
            }
            out.print(new FtpReply(StandardCommandManager.genericResponse("RESPONSE_150_OK")));
            out.flush();

            try {
            	
                Socket sock = conn.getTransferState().getDataSocketForLIST();
                ListElementsContainer container = new ListElementsContainer(
    					request.getSession(), request.getUser());
    			container = ListUtils.list(dir, container);
                Writer os = new OutputStreamWriter(sock.getOutputStream());

                for (InodeHandleInterface inode : container.getElements()) {
                    os.write(toMLST(inode) + "\r\n");
                }

                os.close();
            } catch (IOException e1) {
                logger.warn("", e1);

                //425 Can't open data connection
                return new CommandResponse(425, e1.getMessage());
            }

            return StandardCommandManager.genericResponse("RESPONSE_226_CLOSING_DATA_CONNECTION");
        }

        return StandardCommandManager.genericResponse("RESPONSE_500_SYNTAX_ERROR");
    }

    private String toMLST(InodeHandleInterface file) {
        String ret = // MLSTSerialize.toMLST(file);
        	"mlst"; // -zubov

        //add perm=
        //add 
        return ret;
    }
}
