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
package org.drftpd.commands;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.plugins.DataConnectionHandler;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.remotefile.RemoteFileInterface;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;

import java.net.Socket;

import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id: MLST.java,v 1.5 2004/11/03 16:46:44 mog Exp $
 */
public class MLST implements CommandHandlerFactory, CommandHandler {
    private static final Logger logger = Logger.getLogger(MLST.class);

    public FtpReply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String command = conn.getRequest().getCommand();

        LinkedRemoteFileInterface dir = conn.getCurrentDirectory();

        if (conn.getRequest().hasArgument()) {
            try {
                dir = dir.lookupFile(conn.getRequest().getArgument());
            } catch (FileNotFoundException e) {
                return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
            }
        }

        if (!conn.getGlobalContext().getConnectionManager().getGlobalContext()
                     .getConfig().checkPrivPath(conn.getUserNull(), dir)) {
            return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
        }

        PrintWriter out = conn.getControlWriter();

        if ("MLST".equals(command)) {
            out.print("250- Begin\r\n");
            out.print(toMLST(dir) + "\r\n");
            out.print("250 End.\r\n");

            return null;
        } else if ("MLSD".equals(command)) {
            DataConnectionHandler dataConnHnd;

            try {
                dataConnHnd = (DataConnectionHandler) conn.getCommandManager()
                                                          .getCommandHandler(DataConnectionHandler.class);
            } catch (ObjectNotFoundException e) {
                return new FtpReply(500, e.getMessage());
            }

            out.print(FtpReply.RESPONSE_150_OK);
            out.flush();

            try {
                Socket sock = dataConnHnd.getDataSocket(conn.getSocketFactory());
                List files = ListUtils.list(dir, conn);
                Writer os = new OutputStreamWriter(sock.getOutputStream());

                for (Iterator iter = files.iterator(); iter.hasNext();) {
                    RemoteFileInterface file = (RemoteFileInterface) iter.next();
                    os.write(toMLST(file) + "\r\n");
                }

                os.close();
            } catch (IOException e1) {
                logger.warn("", e1);

                //425 Can't open data connection
                return new FtpReply(425, e1.getMessage());
            }

            return FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION;
        }

        return FtpReply.RESPONSE_500_SYNTAX_ERROR;
    }

    public String[] getFeatReplies() {
        return new String[] {
            "MLST type*,x.crc32*,size*,modify*,unix.owner*,unix.group*,x.slaves*,x.xfertime*"
        };
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    private String toMLST(RemoteFileInterface file) {
        String ret = MLSTSerialize.toMLST(file);

        //add perm=
        //add 
        return ret;
    }

    public void unload() {
    }
}
