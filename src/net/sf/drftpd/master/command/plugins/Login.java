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

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.HostMask;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;

import org.apache.log4j.Logger;

import socks.server.Ident;

/**
 * @version $Id: Login.java,v 1.20 2004/04/20 04:11:48 mog Exp $
 */
public class Login implements CommandHandler, Cloneable {

	private static final Logger logger = Logger.getLogger(CommandHandler.class);
	private InetAddress _idntAddress;
	private String _idntIdent;

	/**
	 * Syntax: IDNT ident@ip:dns ???
	 * @param conn
	 * @return
	 */
	private FtpReply doIDNT(BaseFtpConnection conn) {
		if(_idntAddress != null) return FtpReply.RESPONSE_530_ACCESS_DENIED;
		if(conn.getClientAddress().equals(conn.getConnectionManager().getConfig().getBouncerIp())) {
			String arg = conn.getRequest().getArgument();
			int pos1 = arg.indexOf('@');
			if(pos1 == -1) return FtpReply.RESPONSE_501_SYNTAX_ERROR;
			int pos2 = arg.indexOf(':', pos1+1);
			if(pos2 == -1) return FtpReply.RESPONSE_501_SYNTAX_ERROR;

			try {
				_idntAddress = InetAddress.getByName(arg.substring(pos1+1, pos2));
				_idntIdent = arg.substring(0, pos1).toString();
			} catch (UnknownHostException e) {
				return new FtpReply(501, e.getMessage());
			}
		}
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	/**
	 * <code>PASS &lt;SP&gt; <password> &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string specifying the user's
	 * password.  This command must be immediately preceded by the
	 * user name command.
	 */
	private FtpReply doPASS(BaseFtpConnection conn) {
		if (conn.getUserNull() == null) {
			return FtpReply.RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS;
		}

		FtpRequest request = conn.getRequest();

		// set user password and login
		String pass = request.hasArgument() ? request.getArgument() : "";

		// login failure - close connection
		if (conn.getUserNull().checkPassword(pass)) {
			conn.getUserNull().login();
			conn.setAuthenticated(true);
			conn.getConnectionManager().dispatchFtpEvent(
				new UserEvent(conn.getUserNull(), "LOGIN"));

			FtpReply response =
				new FtpReply(
					230,
					conn.jprintf(Login.class.getName(), "pass.success"));
			try {
				Textoutput.addTextToResponse(response, "welcome");
			} catch (IOException e) {
				logger.warn("Error reading welcome", e);
			}
			return response;
		} else {
			return new FtpReply(530, conn.jprintf(Login.class.getName(), "pass.fail"));
		}
	}

	/**
	 * <code>QUIT &lt;CRLF&gt;</code><br>
	 *
	 * This command terminates a USER and if file transfer is not
	 * in progress, the server closes the control connection.
	 */
	private FtpReply doQUIT(BaseFtpConnection conn) {

		conn.stop();
		return new FtpReply(221, conn.jprintf(Login.class.getName(), "quit.success"));
	}

	/**
	 * <code>USER &lt;SP&gt; &lt;username&gt; &lt;CRLF&gt;</code><br>
	 *
	 * The argument field is a Telnet string identifying the user.
	 * The user identification is that which is required by the
	 * server for access to its file system.  This command will
	 * normally be the first command transmitted by the user after
	 * the control connections are made.
	 */
	private FtpReply doUSER(BaseFtpConnection conn) {
		FtpRequest request = conn.getRequest();
		conn.setAuthenticated(false);
		conn.setUser(null);

		// argument check
		if (!request.hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}

		User newUser;
		try {
			newUser =
				conn.getUserManager().getUserByName(request.getArgument());
		} catch (NoSuchUserException ex) {
			return new FtpReply(530, ex.getMessage());
		} catch (UserFileException ex) {
			logger.warn("", ex);
			return new FtpReply(530, "IOException: " + ex.getMessage());
		} catch (RuntimeException ex) {
			logger.error("", ex);
			return new FtpReply(530, ex.getMessage());
		}

		//		if(connManager.isShutdown() && !conn.getUser().isAdmin()) {
		//			out.print(new FtpResponse(421, ))
		//		}

		if (newUser.isDeleted()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}

		List masks = newUser.getIpMasks2();
		String ident = null;
		for (Iterator iter = masks.iterator(); iter.hasNext();) {
			HostMask mask = (HostMask) iter.next();
			if (ident == null
				&& mask.isIdentMaskSignificant()
				&& conn.getConfig().useIdent()) {
				Ident id = new Ident(conn.getControlSocket());
				if (id.successful) {
					ident = id.userName;
					if (ident.indexOf('@') != -1) {
						return new FtpReply(530, "Invalid ident response");
					}
				} else {
					logger.warn("Failed to get ident response: " + id.errorMessage);
					ident = "";
				}
			}
			if((_idntAddress != null && mask.matches(_idntIdent, _idntAddress)) ||
				mask.matches((ident == null ? "" : ident), conn.getClientAddress())) {
				//success
				// max_users and num_logins restriction
				FtpReply response = conn.getConnectionManager().canLogin(conn, newUser);
				if (response != null) {
					return response;
				}
				conn.setUser(newUser);
				return new FtpReply(
					331,
					conn.jprintf(Login.class.getName(), "user.success"));
			}
		}
		//fail
		return FtpReply.RESPONSE_530_ACCESS_DENIED;
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("USER".equals(cmd))
			return doUSER(conn);
		if ("PASS".equals(cmd))
			return doPASS(conn);
		if ("QUIT".equals(cmd))
			return doQUIT(conn);
		if("IDNT".equals(cmd)) return doIDNT(conn);
		throw UnhandledCommandException.create(Login.class, conn.getRequest());
	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
				Login login;
				try {
					login = (Login) clone();
				} catch (CloneNotSupportedException e) {
					throw new RuntimeException(e);
				}
				return login;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
