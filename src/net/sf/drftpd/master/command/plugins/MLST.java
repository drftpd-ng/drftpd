/*
 * Created on 2003-okt-20
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Iterator;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.remotefile.RemoteFileInterface;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class MLST implements CommandHandler {

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
				dir.lookupFile(conn.getRequest().getArgument());
			} catch (FileNotFoundException e) {
				return FtpReply.RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN;
			}
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
				Socket sock = dataConnHnd.getDataSocket();
				ArrayList files = new ArrayList(dir.getFiles());
				Writer out2 = new OutputStreamWriter(sock.getOutputStream());
				for (Iterator iter = files.iterator(); iter.hasNext();) {
					RemoteFileInterface file = (RemoteFileInterface) iter.next();
					out2.write(toMLST(file)+"\r\n");					
				}
				out2.close();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
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

}
