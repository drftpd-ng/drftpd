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
package net.sf.drftpd.master;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map;

import javax.net.ssl.SSLSocket;

import net.sf.drftpd.event.ConnectionEvent;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commands.Reply;
import org.drftpd.commands.ReplyException;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.io.AddAsciiOutputStream;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

/**
 * This is a generic ftp connection handler. It delegates the request to
 * appropriate methods in subclasses.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author mog
 * @version $Id$
 */
public class BaseFtpConnection implements Runnable {
	private static final Logger debuglogger = Logger
			.getLogger(BaseFtpConnection.class.getName() + ".service");

	private static final Logger logger = Logger
			.getLogger(BaseFtpConnection.class);

	public static final String NEWLINE = "\r\n";

	/**
	 * Is the current password authenticated?
	 */
	protected boolean _authenticated = false;

	// protected ConnectionManager _cm;
	private CommandManager _commandManager;

	protected Socket _controlSocket;

	protected DirectoryHandle _currentDirectory;

	/**
	 * Is the client running a command?
	 */
	protected boolean _executing;

	private BufferedReader _in;

	/**
	 * time when last command from the client finished execution
	 */
	protected long _lastActive;

	protected PrintWriter _out;

	protected FtpRequest _request;

	/**
	 * Should this thread stop insted of continue looping?
	 */
	protected boolean _stopRequest = false;

	protected String _stopRequestMessage;

	protected Thread _thread;

	protected String _user;

	protected BaseFtpConnection() {
	}

	public BaseFtpConnection(Socket soc) throws IOException {
		_commandManager = getGlobalContext().getConnectionManager()
				.getCommandManagerFactory().initialize(this);
		setControlSocket(soc);
		_lastActive = System.currentTimeMillis();
		setCurrentDirectory(getGlobalContext().getRoot());
	}

	public static ReplacerEnvironment getReplacerEnvironment(
			ReplacerEnvironment env, User user) {
		env = new ReplacerEnvironment(env);

		if (user != null) {
			for (Map.Entry<Key, Object> o : user.getKeyedMap().getAllObjects()
					.entrySet()) {
				env.add(o.getKey().toString(), o.getKey()
						.toString(o.getValue()));
				// logger.debug("Added "+o.getKey().toString()+"
				// "+o.getKey().toString(o.getValue()));
			}
			env.add("user", user.getName());
			env.add("username", user.getName());
			env.add("idletime", "" + user.getIdleTime());
			env.add("credits", Bytes.formatBytes(user.getCredits()));
			env.add("ratio", ""
					+ user.getKeyedMap().get((UserManagement.RATIO)));
			env
					.add("tagline", user.getKeyedMap().get(
							(UserManagement.TAGLINE)));
			env.add("uploaded", Bytes.formatBytes(user.getUploadedBytes()));
			env.add("downloaded", Bytes.formatBytes(user.getDownloadedBytes()));
			env.add("group", user.getGroup());
			env.add("groups", user.getGroups());
			env.add("averagespeed", Bytes.formatBytes(user.getUploadedTime()
					+ (user.getDownloadedTime() / 2)));
			env.add("ipmasks", user.getHostMaskCollection().toString());
			env.add("isbanned",
					""
							+ ((user.getKeyedMap()
									.getObjectDate(UserManagement.BAN_TIME))
									.getTime() > System.currentTimeMillis()));
			// } else {
			// env.add("user", "<unknown>");
		}
		return env;
	}

	public static String jprintf(ReplacerFormat format,
			ReplacerEnvironment env, User user) throws FormatterException {
		env = getReplacerEnvironment(env, user);

		return SimplePrintf.jprintf(format, env);
	}

	public static String jprintf(Class class1, String key,
			ReplacerEnvironment env, User user) {
		env = getReplacerEnvironment(env, user);

		return ReplacerUtils.jprintf(key, env, class1);
	}

	public static String jprintfExceptionStatic(Class class1, String key,
			ReplacerEnvironment env, User user) throws FormatterException {
		env = getReplacerEnvironment(env, user);

		return SimplePrintf
				.jprintf(ReplacerUtils.finalFormat(class1, key), env);
	}

	/**
	 * Get client address
	 */
	public InetAddress getClientAddress() {
		return _controlSocket.getInetAddress();
	}

	public CommandManager getCommandManager() {
		return _commandManager;
	}

	public GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public BufferedReader getControlReader() {
		return _in;
	}

	public Socket getControlSocket() {
		return _controlSocket;
	}

	public PrintWriter getControlWriter() {
		return _out;
	}

	public DirectoryHandle getCurrentDirectory() {
		return _currentDirectory;
	}

	public char getDirection() {
		String cmd = getRequest().getCommand();

		if ("RETR".equals(cmd)) {
			return Transfer.TRANSFER_SENDING_DOWNLOAD;
		}

		if ("STOR".equals(cmd) || "APPE".equals(cmd)) {
			return Transfer.TRANSFER_RECEIVING_UPLOAD;
		}

		return Transfer.TRANSFER_UNKNOWN;
	}

	/**
	 * Returns the "currentTimeMillis" when last command finished executing.
	 */
	public long getLastActive() {
		return _lastActive;
	}

	/**
	 * Returns the FtpRequest of current or last command executed.
	 */
	public FtpRequest getRequest() {
		return _request;
	}

	/**
	 * Returns Transfer.TRANSFER_SENDING_DOWNLOAD if this connection is
	 * processing a RETR command or Transfer.TRANSFER_RECEIVING_UPLOAD if this
	 * connection is processing a STOR command.
	 * 
	 * @throws IllegalStateException
	 *             if the connection isn't processing a STOR or RETR command.
	 */
	public char getTransferDirection() {
		String cmd = getRequest().getCommand();

		if (cmd.equals("RETR")) {
			return Transfer.TRANSFER_SENDING_DOWNLOAD;
		} else if (cmd.equals("STOR")) {
			return Transfer.TRANSFER_RECEIVING_UPLOAD;
		} else {
			throw new IllegalStateException("Not transfering");
		}
	}

	/**
	 * Get user object
	 */
	public User getUser() throws NoSuchUserException {
		if ((_user == null) || !isAuthenticated()) {
			throw new NoSuchUserException("no user logged in for connection");
		}
		try {
			return getGlobalContext().getUserManager().getUserByNameUnchecked(
					_user);
		} catch (UserFileException e) {
			throw new NoSuchUserException(e);
		}
	}

	public User getUserNull() {
		if (_user == null) {
			return null;
		}
		try {
			return getGlobalContext().getUserManager().getUserByNameUnchecked(
					_user);
		} catch (NoSuchUserException e) {
			return null;
		} catch (UserFileException e) {
			return null;
		}
	}

	protected boolean hasPermission(FtpRequest request) {
		if (isAuthenticated()) {
			return true;
		}

		String cmd = request.getCommand();

		if ("USER".equals(cmd) || "PASS".equals(cmd) || "QUIT".equals(cmd)
				|| "HELP".equals(cmd) || "AUTH".equals(cmd)
				|| "PBSZ".equals(cmd) || "IDNT".equals(cmd)) {
			return true;
		}

		return false;
	}

	public boolean isAuthenticated() {
		return _authenticated;
	}

	/**
	 * Returns true if client is executing a command.
	 */
	public boolean isExecuting() {
		return _executing;
	}

	public boolean isSecure() {
		return _controlSocket instanceof SSLSocket;
	}

	public String jprintf(Class baseName, String key) {
		return jprintf(baseName, key, null, getUserNull());
	}

	public String jprintf(Class class1, String string, ReplacerEnvironment env) {
		return jprintf(class1, string, env, getUserNull());
	}

	public String jprintfException(Class class1, String key,
			ReplacerEnvironment env) throws FormatterException {
		env = getReplacerEnvironment(env, getUserNull());

		return jprintfExceptionStatic(class1, key, env, getUserNull());
	}

	/**
	 * Server one FTP connection.
	 */
	public void run() {
		_lastActive = System.currentTimeMillis();
		if (!GlobalContext.getGlobalContext().getConfig().getHideIps()) {
			logger.info("Handling new request from "
					+ getClientAddress().getHostAddress());
			_thread.setName("FtpConn thread " + _thread.getId() + " from "
					+ getClientAddress().getHostAddress());
		} else {
			logger.info("Handling new request from <iphidden>");
			_thread.setName("FtpConn thread " + _thread.getId()
					+ " from <iphidden>");
		}

		try {
			// in =
			// new BufferedReader(
			// new InputStreamReader(_controlSocket.getInputStream()));
			// out = new PrintWriter(
			// //new FtpWriter( no need for spying :P
			// new BufferedWriter(
			// new OutputStreamWriter(_controlSocket.getOutputStream())));
			_controlSocket.setSoTimeout(1000);

			if (GlobalContext.getGlobalContext().isShutdown()) {
				stop(GlobalContext.getGlobalContext().getShutdownMessage());
			} else {
				Reply response = new Reply(220, GlobalContext
						.getGlobalContext().getConfig().getLoginPrompt());
				_out.print(response);
			}

			while (!_stopRequest) {
				_out.flush();

				// notifyObserver();
				String commandLine = null;

				try {
					commandLine = _in.readLine();
					// will block for a maximum of _controlSocket.getSoTimeout()
					// milliseconds
				} catch (InterruptedIOException ex) {
					if (_controlSocket == null) {
						stop("Control socket is null");
						break;
					}
					if (!_controlSocket.isConnected()) {
						stop("Socket unexpectedly closed");
						break;
					}
					int idleTime;
					try {
						idleTime = getUser().getIdleTime();
					} catch (NoSuchUserException e) {
						idleTime = 60;
						// user not logged in yet
					}
					if (idleTime > 0
							&& ((System.currentTimeMillis() - _lastActive) / 1000 >= idleTime)) {
						stop("IdleTimeout");
						break;
					}
					continue;
				}

				if (_stopRequest) {
					break;
				}

				// test command line
				if (commandLine == null) {
					break;
				}

				// spyRequest(commandLine);
				if (commandLine.equals("")) {
					continue;
				}

				_request = new FtpRequest(commandLine);

				if (!_request.getCommand().equals("PASS")) {
					debuglogger.debug("<< " + _request.getCommandLine());
				}

				if (!hasPermission(_request)) {
					_out.print(Reply.RESPONSE_530_NOT_LOGGED_IN);

					continue;
				}

				// execute command
				_executing = true;
				service(_request, _out);
				_executing = false;
				_lastActive = System.currentTimeMillis();
			}

			if (_stopRequestMessage != null) {
				_out.print(new Reply(421, _stopRequestMessage));
			} else {
				_out.println("421 Connection closing");
			}

			_out.flush();
		} catch (SocketException ex) {
			logger.log(Level.INFO, ex.getMessage() + ", closing for user "
					+ ((_user == null) ? "<not logged in>" : _user), ex);
		} catch (Exception ex) {
			logger.log(Level.INFO, "Exception, closing", ex);
		} finally {
			try {
				_in.close();
				_out.close();
			} catch (Exception ex2) {
				logger.log(Level.WARN, "Exception closing stream", ex2);
			}

			if (isAuthenticated()) {
				try {
					getUser().updateLastAccessTime();
				} catch (NoSuchUserException e) {
					logger
							.error("User does not exist, yet user is authenticated, this is a bug");
				}
				getGlobalContext().dispatchFtpEvent(
						new ConnectionEvent(getUserNull(), "LOGOUT"));
			}

			getGlobalContext().getConnectionManager().remove(this);
		}
	}

	/**
	 * Execute the ftp command.
	 */
	public void service(FtpRequest request, PrintWriter out) throws IOException {
		Reply reply;

		try {
			reply = _commandManager.execute(this);
		} catch (Throwable e) {
			int replycode = e instanceof ReplyException ? ((ReplyException) e)
					.getReplyCode() : 500;
			reply = new Reply(replycode, e.getMessage());
			try {
				if (getUser().getKeyedMap().getObjectBoolean(
						UserManagement.DEBUG)) {
					StringWriter sw = new StringWriter();
					e.printStackTrace(new PrintWriter(sw));
					reply.addComment(sw.toString());
				}
			} catch (NoSuchUserException e1) {
			}
			logger.warn("", e);
		}

		if (reply != null) {
			out.print(reply);
		}
	}

	public void setAuthenticated(boolean authenticated) {
		_authenticated = authenticated;

		if (isAuthenticated()) {
			try {
				// If hideips is on, hide ip but not user/group
				if (GlobalContext.getGlobalContext().getConfig().getHideIps()) {
					_thread.setName("FtpConn thread " + _thread.getId()
							+ " servicing " + _user + "/"
							+ getUser().getGroup());
				} else {
					_thread.setName("FtpConn thread " + _thread.getId()
							+ " from " + getClientAddress().getHostAddress()
							+ " " + _user + "/" + getUser().getGroup());
				}
			} catch (NoSuchUserException e) {
				logger
						.error("User does not exist, yet user is authenticated, this is a bug");
			}
		}
	}

	public void setControlSocket(Socket socket) {
		try {
			_controlSocket = socket;
			_in = new BufferedReader(new InputStreamReader(_controlSocket
					.getInputStream(), "ISO-8859-1"));

			_out = new PrintWriter(new OutputStreamWriter(
					new AddAsciiOutputStream(new BufferedOutputStream(
							_controlSocket.getOutputStream())), "ISO-8859-1"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void setCurrentDirectory(DirectoryHandle path) {
		_currentDirectory = path;
	}

	public void setUser(String user) {
		_user = user;
	}

	public void start() {
		_thread = new Thread(this);
		_thread.start();

		// start() calls run() and execution will start in the background.
	}

	/**
	 * returns a two-line status
	 */
	public String status() {
		return jprintf(BaseFtpConnection.class, "statusline");
	}

	/**
	 * User logout and stop this thread.
	 */
	public void stop() {
		/*
		 * synchronized (getDataConnectionHandler()) { if
		 * (getDataConnectionHandler().isTransfering()) { try {
		 * getDataConnectionHandler().getTransfer().abort( "Control connection
		 * dropped"); } catch (ObjectNotFoundException e) { logger.debug("This
		 * is a bug, please report it", e); } } }
		 */
		// This needs to be addressed after session
		// information is stored in BaseFtpConnection and not
		// DataConnectionHandler
		_stopRequest = true;
	}

	public void stop(String message) {
		_stopRequestMessage = message;
		stop();
	}

	public String toString() {
		StringBuffer buf = new StringBuffer("[BaseFtpConnection");

		if (_user != null) {
			buf.append("[user: " + _user + "]");
		}

		if (_request != null) {
			buf.append("[command: " + _request.getCommand() + "]");
		}

		if (isExecuting()) {
			buf.append("[executing]");
		} else {
			buf.append("[idle: "
					+ Time.formatTime(System.currentTimeMillis()
							- getLastActive()));
		}

		buf.append("]");

		return buf.toString();
	}

	public OutputStream getOutputStream() throws IOException {
		return _controlSocket.getOutputStream();
	}

	public int transferCounter(char transferDirection) {
		ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(
				getGlobalContext().getConnectionManager().getConnections());
		int count = 0;
		for (Iterator<BaseFtpConnection> iter = conns.iterator(); iter
				.hasNext();) {
			BaseFtpConnection conn2 = iter.next();

			/*
			 * synchronized (conn2.getDataConnectionHandler()) {
			 * 
			 * if (conn2.getUserNull() == getUserNull()) { if
			 * (!conn2.isExecuting()) { continue; } if
			 * (conn2.getDataConnectionHandler().isTransfering() &&
			 * (conn2.getTransferDirection() == transferDirection)) { count++; } } }
			 */
			// This needs to be addressed after session
			// information is stored in BaseFtpConnection and not
			// DataConnectionHandler
		}
		return count;
	}
}
