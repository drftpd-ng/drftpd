/*
 * Created on 2003-okt-16
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.master.command.plugins;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * returns 200 Command OK on all commands
 * 
 * @author mog
 */
public class Dummy implements CommandHandler {

	private CommandManager _cmdmgr;

	public Dummy() {
		super();
	}

	public FtpReply execute(BaseFtpConnection conn) throws UnhandledCommandException {
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		_cmdmgr = initializer;
		return this;
	}

	public String[] getFeatReplies() {
		return (String[])_cmdmgr.getHandledCommands(getClass()).toArray(new String[0]);
	}
	/**
	 * <code>NOOP &lt;CRLF&gt;</code><br>
	 *
	 * This command does not affect any parameters or previously
	 * entered commands. It specifies no action other than that the
	 * server send an OK reply.
	 */
//DUMMY
//	public void doNOOP(FtpRequest request, PrintWriter out) {
//
//		// reset state variables
//		resetState();
//
//		out.print(FtpReply.RESPONSE_200_COMMAND_OK);
//	}
//	DUMMY
//	  public void doCLNT(FtpRequest request, PrintWriter out) {
//		  out.print(FtpReply.RESPONSE_200_COMMAND_OK);
//		  return;
//	  }

}
