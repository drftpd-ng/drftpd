package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.rmi.RemoteException;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
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

import org.apache.log4j.FileAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

/**
 * This is a generic ftp connection handler. It delegates 
 * the request to appropriate methods in subclasses.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 * @version $Id: BaseFtpConnection.java,v 1.55 2003/11/17 20:13:10 mog Exp $
 */
public class BaseFtpConnection implements Runnable {
	private static Logger debuglogger =
		Logger.getLogger(BaseFtpConnection.class.getName() + ".service");
	static {
		try {
			debuglogger.addAppender(
				new FileAppender(
					new PatternLayout(PatternLayout.TTCC_CONVERSION_PATTERN),
					"ftp-data/logs/debug.log"));
			debuglogger.setAdditivity(false);
		} catch (IOException e) {
			throw new FatalException(e);
		}
	}

	private static Logger logger = Logger.getLogger(BaseFtpConnection.class);

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
	public BaseFtpConnection(ConnectionManager connManager, Socket soc) {
		_commandManager =
			connManager.getCommandManagerFactory().initialize(this);
		_controlSocket = soc;
		_cm = connManager;
		lastActive = System.currentTimeMillis();
		setCurrentDirectory(connManager.getSlaveManager().getRoot());
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

	public char getTransferDirection() {
		String cmd = getRequest().getCommand();
		if (cmd.equals("RETR")) {
			return Transfer.TRANSFER_SENDING_DOWNLOAD;
		} else if (cmd.equals("STOR")) {
			return Transfer.TRANSFER_RECEIVING_UPLOAD;
		} else {
			return Transfer.TRANSFER_UNKNOWN;
		}
	}

	/**
	 * Get user object
	 */
	public User getUser() throws NoSuchUserException {
		if (_user == null || !_authenticated)
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

		String command = request.getCommand();
		if ("USER".equals(command)
			|| "PASS".equals(command)
			|| "QUIT".equals(command)
			|| "HELP".equals(command))
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

	/**
	 * Reset all the member variables. Close all sockets.
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
			in =
				new BufferedReader(
					new InputStreamReader(_controlSocket.getInputStream()));

			out = new PrintWriter(
				//new FtpWriter( no need for spying :P
	new BufferedWriter(
		new OutputStreamWriter(_controlSocket.getOutputStream())));

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
					+ (this._user == null
						? "<not logged in>"
						: this._user.getUsername()),
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
		} catch (RuntimeException e) {
			reply = new FtpReply(500, e.toString());
			logger.warn("", e);
		}
		if (reply != null)
			out.print(reply);
		//		try {
		//			String metName;
		//			metName = "do" + request.getCommand().replaceAll(" ", "_");
		//			Method actionMet =
		//				getClass().getDeclaredMethod(metName, METHOD_INPUT_SIG);
		//			actionMet.invoke(this, new Object[] { request, out });
		//		} catch (NoSuchMethodException ex) {
		//			out.print(FtpReply.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
		//			//out.write(ftpStatus.getResponse(502, request, user, null));
		//		} catch (InvocationTargetException ex) {
		//			logger.log(Level.ERROR, "Error", ex);
		//			out.print(
		//				new FtpReply(
		//					500,
		//					"Uncaught exception: " + ex.getCause().toString()));
		//			Throwable th = ex.getTargetException();
		//			th.printStackTrace();
		//		} catch (Exception ex) {
		//			out.print(FtpReply.RESPONSE_500_SYNTAX_ERROR);
		//			if (ex instanceof java.io.IOException) {
		//				throw (IOException) ex;
		//			}
		//		}
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
	 *  returns a one-line status line
	 */
	public String status() {
		return " [Credits: "
			+ Bytes.formatBytes(_user.getCredits())
			+ "] [Ratio: 1:"
			+ _user.getRatio()
			+ "] [Disk free: "
			+ Bytes.formatBytes(
				getSlaveManager().getAllStatus().getDiskSpaceAvailable())
			+ "]";
	}

	/**
	 * User logout and stop this thread.
	 */
	public void stop() {
		stopRequest = true;
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
}
