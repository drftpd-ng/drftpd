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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * returns 200 Command OK on all commands
 * 
 * @author mog
 * @version $Id: Dummy.java,v 1.4 2004/02/10 00:03:07 mog Exp $
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
	public void load(CommandManagerFactory initializer) {}
	public void unload() {}

}
