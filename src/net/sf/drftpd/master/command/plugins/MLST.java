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
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.remotefile.RemoteFileInterface;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: MLST.java,v 1.10 2004/02/10 00:03:07 mog Exp $
 */
public class MLST implements CommandHandler {

	private static final Logger logger = Logger.getLogger(MLST.class);

	private CommandManager _cmr;

	private String toMLST(RemoteFileInterface file) {
		String ret = MLSTSerialize.toMLST(file);
		//add perm=
		//add 
		return ret;
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String command = conn.getRequest().getCommand();

		LinkedRemoteFile dir = conn.getCurrentDirectory();
		if (conn.getRequest().hasArgument()) {
			try {
				dir = dir.lookupFile(conn.getRequest().getArgument());
			} catch (FileNotFoundException e) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
		}
		if(!conn.getConfig().checkPrivPath(conn.getUserNull(), dir)) {
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
				dataConnHnd =
					(DataConnectionHandler) _cmr.getCommandHandler(
						DataConnectionHandler.class);
			} catch (ObjectNotFoundException e) {
				return new FtpReply(500, e.getMessage());
			}

			out.print(FtpReply.RESPONSE_150_OK);
			try {
				Socket sock = dataConnHnd.getDataSocket(conn.getSocketFactory());
				List files = ListUtils.list(dir, conn);
				Writer out2 = new OutputStreamWriter(sock.getOutputStream());
				for (Iterator iter = files.iterator(); iter.hasNext();) {
					RemoteFileInterface file = (RemoteFileInterface) iter.next();
					out2.write(toMLST(file)+"\r\n");					
				}
				out2.close();
			} catch (IOException e1) {
				logger.warn("", e1);
				//425 Can't open data connection
				return new FtpReply(425, e1.getMessage());
			}
			
			return FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION;
		}
		return FtpReply.RESPONSE_500_SYNTAX_ERROR;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		_cmr = initializer;
		return this;
	}

	public String[] getFeatReplies() {
		return new String[] { "MLST", "MLSD" };
	}

	public void load(CommandManagerFactory initializer) {}

	public void unload() {}

}
