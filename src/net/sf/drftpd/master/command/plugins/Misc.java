/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import java.io.PrintWriter;
import java.util.Date;
import java.util.Iterator;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.slave.SlaveImpl;

public class Misc implements CommandHandler {
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
		// reset state variables
		conn.resetState();
		//mDataConnection.reset();
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
		conn.resetState();
		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM;
		}
		return new FtpReply(200, conn.status());
	}

	private FtpReply doSITE_TIME(BaseFtpConnection conn) {
		conn.resetState();
		if (conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		return new FtpReply(200, "Server time is: " + new Date());
	}

	private FtpReply doSITE_VERS(BaseFtpConnection conn) {
		conn.resetState();
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
