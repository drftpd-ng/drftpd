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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.master.command.plugins.DataConnectionHandler;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;

/**
 * This is a generic ftp connection handler. It delegates 
 * the request to appropriate methods in subclasses.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author mog
 * @version $Id: BaseFtpConnection.java,v 1.74 2004/02/10 00:03:06 mog Exp $
 */
public class BaseFtpConnection implements Runnable {
	private static final Logger debuglogger =
		Logger.getLogger(BaseFtpConnection.class.getName() + ".service");

	private static final Logger logger =
		Logger.getLogger(BaseFtpConnection.class);
	public static final String NEWLINE = "\r\n";

	/**
	 * Is the current password authenticated?
	 */
	protected boolean _authenticated = false;
	protected ConnectionManager _cm;
	private CommandManager _commandManager;
	protected Socket _controlSocket;

	protected User _user;
	protected InetAddress clientAddress = null;

	protected LinkedRemoteFile currentDirectory;

	/**
	 * Is the client running a command?
	 */
	protected boolean executing;

	private BufferedReader in;
	/**
	 * time when last command from the client finished execution
	 */
	protected long lastActive;

	protected PrintWriter out;

	/**
	  * PRE Transfere
	  *
	  */
	protected FtpRequest request;

	/**
	 * Should this thread stop insted of continue looping?
	 */
	protected boolean stopRequest = false;
	protected String stopRequestMessage;
	protected Thread thread;
	public BaseFtpConnection(ConnectionManager connManager, Socket soc)
		throws IOException {
		_commandManager =
			connManager.getCommandManagerFactory().initialize(this);
		_cm = connManager;
		//_controlSocket = soc;
		setControlSocket(soc);
		lastActive = System.currentTimeMillis();
		setCurrentDirectory(connManager.getSlaveManager().getRoot());
	}
	protected BaseFtpConnection() {
	}

	/**
	 * @deprecated use getConnectionManager().dispatchFtpEvent()
	 */
	protected void dispatchFtpEvent(Event event) {
		getConnectionManager().dispatchFtpEvent(event);
	}

	/**
	 * Last defense - close connections.
	 */
	protected void finalize() throws Throwable {
		reset();
		super.finalize();
	}

	/**
	 * Get client address
	 */
	public InetAddress getClientAddress() {
		return clientAddress;
	}

	public CommandManager getCommandManager() {
		assert _commandManager != null : toString();
		return _commandManager;
	}

	public FtpConfig getConfig() {
		return getConnectionManager().getConfig();
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	public Socket getControlSocket() {
		return _controlSocket;
	}

	public PrintWriter getControlWriter() {
		return out;
	}

	public LinkedRemoteFile getCurrentDirectory() {
		return currentDirectory;
	}

	public DataConnectionHandler getDataConnectionHandler() {
		try {
			return (DataConnectionHandler) getCommandManager()
				.getCommandHandler(
				DataConnectionHandler.class);
		} catch (ObjectNotFoundException e) {
			throw new RuntimeException(
				"DataConnectionHandler must be available",
				e);
		}
	}
	public char getDirection() {
		String cmd = getRequest().getCommand();
		if ("RETR".equals(cmd))
			return Transfer.TRANSFER_SENDING_DOWNLOAD;
		if ("STOR".equals(cmd) || "APPE".equals(cmd))
			return Transfer.TRANSFER_RECEIVING_UPLOAD;
		return Transfer.TRANSFER_UNKNOWN;
	}

	/**
	 * Returns the "currentTimeMillis" when last command finished executing.
	 */
	public long getLastActive() {
		return lastActive;
	}

	/**
	 * Returns the FtpRequest of current or last command executed.
	 */
	public FtpRequest getRequest() {
		return request;
	}

	public SlaveManagerImpl getSlaveManager() {
		return getConnectionManager().getSlaveManager();
	}

	/**
	 * Returns Transfer.TRANSFER_SENDING_DOWNLOAD if this connection is processing a RETR command
	 * or Transfer.TRANSFER_RECEIVING_UPLOAD if this connection is processing a STOR command.
	 * @throws IllegalStateException if the connection isn't processing a STOR or RETR command.
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
		if (_user == null || !isAuthenticated())
			throw new NoSuchUserException("no user logged in for connection");
		return _user;
	}

	public UserManager getUserManager() {
		return getConnectionManager().getUserManager();
	}

	public User getUserNull() {
		return _user;
	}

	protected boolean hasPermission(FtpRequest request) {
		if (isAuthenticated())
			return true;

		String cmd = request.getCommand();
		if ("USER".equals(cmd)
			|| "PASS".equals(cmd)
			|| "QUIT".equals(cmd)
			|| "HELP".equals(cmd)
			|| "AUTH".equals(cmd)
			|| "PBSZ".equals(cmd))
			return true;

		return false;
	}

	public boolean isAuthenticated() {
		return _authenticated;
	}

	/**
	 * Returns true if client is executing a command.
	 */
	public boolean isExecuting() {
		return executing;
	}

	public String jprintf(String baseName, String key) {
		return jprintf(baseName, key, null);
	}

	public String jprintf(Class baseName, String key) {
		return jprintf(baseName.getName(), key, null);
	}

	/**
	 * @param env null for an empty parent replacerenvironment.
	 */
	public String jprintf(
		String baseName,
		String key,
		ReplacerEnvironment env) {
		return jprintf(baseName, key, env, getUserNull());
	}

	public static ReplacerEnvironment getReplacerEnvironment(
		ReplacerEnvironment env,
		User user) {
		env = new ReplacerEnvironment(env);
				
		if (user != null) {
			env.add("user", user.getUsername());
			env.add("credits", Bytes.formatBytes(user.getCredits()));
			env.add("ratio", "" + user.getRatio());
			env.add("tagline", user.getTagline());
			env.add("uploaded", Bytes.formatBytes(user.getUploadedBytes()));
			env.add("downloaded", Bytes.formatBytes(user.getDownloadedBytes()));
			env.add("avragespeed", Bytes.formatBytes(user.getUploadedMilliseconds() + user.getDownloadedMilliseconds() / 2));
		} else {
			env.add("user", "<unknown>");
		}
		return env;
	}

	public static String jprintf(
		String baseName,
		String key,
		ReplacerEnvironment env,
		User user) {
		env = getReplacerEnvironment(env, user);
		return ReplacerUtils.jprintf(key, env, baseName);
	}
	public static String jprintf(
		ReplacerFormat format,
		ReplacerEnvironment env,
		User user)
		throws FormatterException {
		env = getReplacerEnvironment(env, user);
		return ReplacerUtils.finalJprintf(format, env);
	}

	/**
	 * Reset all the member variables. Close all sockets.
	 * @deprecated empty, should call reset() on DataConnectionHandler ?
	 */
	public void reset() {
		//
		//		// close data socket
		//		if (_dataSocket != null) {
		//			try {
		//				_dataSocket.close();
		//			} catch (Exception ex) {
		//				logger.log(Level.WARN, "Error closing data socket", ex);
		//			}
		//			_dataSocket = null;
		//		}
		//
		//		// close server socket
		//		//		if (mServSoc != null) {
		//		//			try {
		//		//				mServSoc.close();
		//		//			} catch (Exception ex) {
		//		//				logger.log(Level.WARNING, "Error closing server socket", ex);
		//		//			}
		//		//			mServSoc = null;
		//		//		}
		//
		//		// reset other variables
		//		mAddress = null;
		//		miPort = 0;
		//
		//		mbPort = false;
		//		mbPasv = false;
	}

	/**
		 * Reset temporary state variables.
		 * mstRenFr and resumePosition
		 */
	public void resetState() {
		//		_renameFrom = null;
		//		preTransfer = false;
		//		preTransferRSlave = null;

		//		mbReset = false;
		//resumePosition = 0;

		//mbUser = false;
		//mbPass = false;
	}

	/**
	 * Server one FTP connection.
	 */
	public void run() {
		lastActive = System.currentTimeMillis();
		clientAddress = _controlSocket.getInetAddress();
		logger.info(
			"Handling new request from " + clientAddress.getHostAddress());
		thread.setName("FtpConn from " + clientAddress.getHostAddress());

		try {
			//			in =
			//				new BufferedReader(
			//					new InputStreamReader(_controlSocket.getInputStream()));

			//			out = new PrintWriter(
			//				//new FtpWriter( no need for spying :P
			//	new BufferedWriter(
			//		new OutputStreamWriter(_controlSocket.getOutputStream())));

			_controlSocket.setSoTimeout(1000);
			if (getConnectionManager().isShutdown()) {
				stop(getConnectionManager().getShutdownMessage());
			} else {
				FtpReply response =
					new FtpReply(220, getConfig().getLoginPrompt());
				out.print(response);
			}
			while (!stopRequest) {
				thread.setName(
					"FtpConn from " + clientAddress.getHostAddress());

				out.flush();
				//notifyObserver();
				String commandLine;
				try {
					commandLine = in.readLine();
				} catch (InterruptedIOException ex) {
					continue;
				}
				if (stopRequest)
					break;
				// test command line
				if (commandLine == null)
					break;

				//spyRequest(commandLine);
				if (commandLine.equals(""))
					continue;

				request = new FtpRequest(commandLine);

				debuglogger.debug(
					"<< "
						+ request.getCommandLine()
						+ " [user="
						+ _user
						+ ",cwd="
						+ currentDirectory.getPath()
						+ ",host="
						+ clientAddress
						+ "]");
				if (!hasPermission(request)) {
					out.print(FtpReply.RESPONSE_530_NOT_LOGGED_IN);
					continue;
				}
				// execute command
				executing = true;
				service(request, out);
				executing = false;
				lastActive = System.currentTimeMillis();
			}
			if (stopRequestMessage != null) {
				out.print(new FtpReply(421, stopRequestMessage));
			} else {
				out.println("421 Connection closing");
			}
			out.flush();
		} catch (SocketException ex) {
			logger.log(
				Level.INFO,
				ex.getMessage()
					+ ", closing for user "
					+ (_user == null
						? "<not logged in>"
						: _user.getUsername()),
				ex);
		} catch (Exception ex) {
			logger.log(Level.INFO, "Exception, closing", ex);
		} finally {
			try {
				in.close();
				out.close();
			} catch (Exception ex2) {
				logger.log(Level.WARN, "Exception closing stream", ex2);
			}
			if (isAuthenticated()) {
				_user.updateLastAccessTime();
				dispatchFtpEvent(new UserEvent(_user, "LOGOUT"));
			}
			getConnectionManager().remove(this);
		}
	}

	/**
	 * Execute the ftp command.
	 */
	public void service(FtpRequest request, PrintWriter out)
		throws IOException {
		FtpReply reply;
		try {
			reply = _commandManager.execute(this);
		} catch (UnhandledCommandException e) {
			reply = new FtpReply(500, e.getMessage());
			logger.warn("", e);
		} catch (Throwable e) {
			reply = new FtpReply(500, e.toString());
			logger.warn("", e);
		}
		if (reply != null)
			out.print(reply);
	}

	public void setAuthenticated(boolean authenticated) {
		_authenticated = authenticated;
		if (authenticated)
			thread.setName(
				"FtpConn from "
					+ clientAddress.getHostAddress()
					+ " "
					+ _user.getUsername()
					+ "/"
					+ _user.getGroupName());
	}

	public void setCurrentDirectory(LinkedRemoteFile file) {
		currentDirectory = file;
	}

	public void setUser(User user) {
		_user = user;
	}
	public void start() {
		thread = new Thread(this);
		thread.start();
		// start() calls run() and execution will start in the background.
	}

	/**
	 *  returns a two-line status
	 */
	public String status() {
		return jprintf(BaseFtpConnection.class.getName(), "statusline");
		//		return " [Credits: "
		//			+ Bytes.formatBytes(_user.getCredits())
		//			+ "] [Ratio: 1:"
		//			+ _user.getRatio()
		//			+ "]";
	}

	/**
	 * User logout and stop this thread.
	 */
	public void stop() {
		stopRequest = true;
		//TODO _sock.close() as well?
	}

	public void stop(String message) {
		stopRequestMessage = message;
		if (getDataConnectionHandler().isTransfering()) {
			try {
				getDataConnectionHandler().getTransfer().abort();
			} catch (RemoteException e) {
				getDataConnectionHandler()
					.getTranferSlave()
					.handleRemoteException(
					e);
			}
		}
		stop();
	}

	public String toString() {
		StringBuffer buf = new StringBuffer("[BaseFtpConnection");
		if (_user != null) {
			buf.append("[user: " + _user + "]");
		}
		if (request != null) {
			buf.append("[command: " + request.getCommand() + "]");
		}
		if (isExecuting()) {
			buf.append("[executing]");
		} else {
			buf.append(
				"[idle: "
					+ (System.currentTimeMillis() - getLastActive())
					+ "ms]");
		}
		buf.append("]");
		return buf.toString();
	}

	public void setControlSocket(Socket socket) {
		try {
			_controlSocket = socket;
			in =
				new BufferedReader(
					new InputStreamReader(
						_controlSocket.getInputStream(),
						"ISO-8859-1"));

			out =
				new PrintWriter(
					new OutputStreamWriter(
						_controlSocket.getOutputStream(),
						"ISO-8859-1"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	public ServerSocketFactory getServerSocketFactory() {
		return ServerSocketFactory.getDefault();
	}

	public SocketFactory getSocketFactory() {
		return SocketFactory.getDefault();
	}

	public String jprintf(
		Class class1,
		String string,
		ReplacerEnvironment env) {
		return jprintf(class1.getName(), string, env);
	}
}