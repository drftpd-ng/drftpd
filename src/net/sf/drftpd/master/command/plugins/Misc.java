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

import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.UnhandledCommandException;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandlerBundle;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.slave.SlaveImpl;

/**
 * @version $Id: Misc.java,v 1.8 2004/06/01 15:40:30 mog Exp $
 */
public class Misc implements CommandHandlerBundle {
	/**
	 * <code>ABOR &lt;CRLF&gt;</code><br>
	 *
	 * This command tells the server to abort the previous FTP
	 * service command and any associated transfer of data.
	 * No action is to be taken if the previous command
	 * has been completed (including data transfer).  The control
	 * connection is not to be closed by the server, but the data
	 * connection must be closed.  
	 * Current implementation does not do anything. As here data 
	 * transfers are not multi-threaded. 
	 */
	private FtpReply doABOR(BaseFtpConnection conn) {
		return FtpReply.RESPONSE_226_CLOSING_DATA_CONNECTION;
	}

	// LIST;NLST;RETR;STOR
	private FtpReply doFEAT(BaseFtpConnection conn) {
		PrintWriter out = conn.getControlWriter();
		out.print("211-Extensions supported:\r\n");
		for (Iterator iter = conn.getCommandManager().getCommandHandlersMap().values().iterator(); iter.hasNext();) {
			CommandHandler hnd = (CommandHandler) iter.next();
			String feat[] = hnd.getFeatReplies();
			if(feat == null) continue;
			for (int i = 0; i < feat.length; i++) {
				out.print(" "+feat[i]+"\r\n");
			}
		}
//				+ " CLNT\r\n"
//				+ " MDTM\r\n"
//				+ " PRET\r\n"
//				+ " SIZE\r\n"
//				+ " XCRC\r\n"
		out.print("211 End\r\n");
		return null;
	}

	/**
	 * <code>HELP [&lt;SP&gt; <string>] &lt;CRLF&gt;</code><br>
	 *
	 * This command shall cause the server to send helpful
	 * information regarding its implementation status over the
	 * control connection to the user.  The command may take an
	 * argument (e.g., any command name) and return more specific
	 * information as a response.
	 */
	//TODO implement HELP, SITE HELP would be good too.
//	public FtpReply doHELP(BaseFtpConnection conn) {
//
		// print global help
		//		if (!request.hasArgument()) {
//		FtpReply response = new FtpReply(214);
//		response.addComment("The following commands are recognized.");
		//out.write(ftpStatus.getResponse(214, null, user, null));
//		Method methods[] = this.getClass().getDeclaredMethods();
//		for (int i = 0; i < methods.length; i++) {
//			Method method = methods[i];
//			Class parameterTypes[] = method.getParameterTypes();
//			if (parameterTypes.length == 2
//				&& parameterTypes[0] == FtpRequest.class
//				&& parameterTypes[1] == PrintWriter.class) {
//				String commandName =
//					method.getName().substring(2).replace('_', ' ');
//				response.addComment(commandName);
//			}
//		}
//		out.print(response);
//		return;
		//		}
		//
		//		// print command specific help
		//		String ftpCmd = request.getArgument().toUpperCase();
		//		String args[] = null;
		//		FtpRequest tempRequest = new FtpRequest(ftpCmd);
		//		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
		//		return;
//	}

	private FtpReply doSITE_STAT(BaseFtpConnection conn) {
		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
		FtpReply response = (FtpReply) FtpReply.RESPONSE_200_COMMAND_OK.clone();
		
		response.addComment(conn.status());
		return response;
	}

	private FtpReply doSITE_TIME(BaseFtpConnection conn) {
		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		return new FtpReply(200, "Server time is: " + new Date());
	}

	private FtpReply doSITE_VERS(BaseFtpConnection conn) {
		return new FtpReply(200, SlaveImpl.VERSION);
	}

	public FtpReply execute(BaseFtpConnection conn) throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if("ABOR".equals(cmd)) return doABOR(conn);
		if("FEAT".equals(cmd)) return doFEAT(conn);
		if("SITE STAT".equals(cmd)) return doSITE_STAT(conn);
		if("SITE TIME".equals(cmd)) return doSITE_TIME(conn);
		if("SITE VERS".equals(cmd)) return doSITE_VERS(conn);
		throw UnhandledCommandException.create(Misc.class, conn.getRequest());
	}

	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public void load(CommandManagerFactory initializer) {}
	public void unload() {}

}
