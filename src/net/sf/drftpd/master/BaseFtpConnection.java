package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * This is a generic ftp connection handler. It delegates 
 * the request to appropriate methods in subclasses.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class BaseFtpConnection implements Runnable {

	List ftpListeners;

	/**
	 * @deprecated use getConnectionManager().dispatchFtpEvent()
	 */
	protected void dispatchFtpEvent(Event event) {
		connManager.dispatchFtpEvent(event);
	}

	protected final static Class[] METHOD_INPUT_SIG =
		new Class[] { FtpRequest.class, PrintWriter.class };
	private static Logger logger =
		Logger.getLogger(BaseFtpConnection.class.getName());
	static {
		logger.setLevel(Level.ALL);
	}
	/**
	 * time when last command from the client finished execution
	 */
	protected long lastActive;
	/**
	 * Is the client running a command?
	 */
	protected boolean executing;

	protected User _user;
	/**
	 * Is the current password authenticated?
	 */
	protected boolean authenticated = false;
	protected Socket controlSocket;
	protected PrintWriter out;
	/**
	 * Should this thread stop insted of continue looping?
	 */
	protected boolean stopRequest = false;
	protected String stopRequestMessage;
	protected SlaveManagerImpl slaveManager;
	protected InetAddress clientAddress = null;
	protected Thread thread;
	protected ConnectionManager connManager;
	protected FtpRequest request;
	public void start() {
		thread = new Thread(this);
		thread.start();
		// start() calls run() and execution will start in the background.
	}
	/**
	 * Get client address
	 */
	public InetAddress getClientAddress() {
		return clientAddress;
	}

	/**
	 * Set client address
	 */
	/*
	public void setClientAddress(InetAddress clientAddress) {
		clientAddress = clientAddress;
	}
	*/

	/**
	 * Set configuration file and the control socket.
	 */
	/*
	public BaseFtpConnection(FtpConfig ftpConfig, Socket soc) {
	    //mConfig = ftpConfig;
	    //mFtpStatus = mConfig.getStatus();
	mFtpStatus = new FtpStatus();
	    mControlSocket = soc;
	    mUser = new FtpUser();
	}
	*/
	private Writer debugLog;
	public BaseFtpConnection(
		ConnectionManager connManager,
		Socket soc,
		Writer debugLog) {
		this.controlSocket = soc;
		this.connManager = connManager;
		this.debugLog = debugLog;
	}

	BufferedReader in;

	/**
	 * Server one FTP connection.
	 */
	public void run() {
		clientAddress = controlSocket.getInetAddress();
		logger.info(
			"Handling new request from " + clientAddress.getHostAddress());
		thread.setName(
			"FtpConnection from "
				+ clientAddress.getHostName()
				+ "["
				+ clientAddress.getHostAddress()
				+ "]");

		try {
			in =
				new BufferedReader(
					new InputStreamReader(controlSocket.getInputStream()));

			out = new PrintWriter(
				//new FtpWriter( no need for spying :P
	new BufferedWriter(
		new OutputStreamWriter(controlSocket.getOutputStream())));

			controlSocket.setSoTimeout(1000);
			if (connManager.isShutdown()) {
				stop(connManager.getShutdownMessage());
			} else {
				out.println("220 Service ready for new user.");
			}
			while (!stopRequest) {
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
				//TODO write to this.debugLog
				logger.debug(
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
					out.print(FtpResponse.RESPONSE_530_NOT_LOGGED_IN);
					continue;
				}
				// execute command
				executing = true;
				service(request, out);
				executing = false;
				lastActive = System.currentTimeMillis();
			}
			if (stopRequestMessage != null) {
				out.print(new FtpResponse(421, stopRequestMessage));
			} else {
				out.println("421 Connection closing");
			}
			out.flush();
		} catch (SocketException ex) {
			logger.log(Level.INFO, ex.getMessage()+", closing for user "+(this._user == null ? "<not logged in>" : this._user.getUsername()), ex);
		} catch (Exception ex) {
			logger.log(Level.INFO, "Exception, closing", ex);
		} finally {
			try {
				in.close();
				out.close();
			} catch (Exception ex2) {
				logger.log(Level.WARN, "Exception closing stream", ex2);
			}
			if (isAuthenticated())
				dispatchFtpEvent(new UserEvent(_user, "LOGOUT"));
			connManager.remove(this);
		}
	}

	/**
	 * Execute the ftp command.
	 */
	public void service(FtpRequest request, PrintWriter out)
		throws IOException {
		try {
			String metName;
			metName = "do" + request.getCommand().replaceAll(" ", "_");
			Method actionMet =
				getClass().getDeclaredMethod(metName, METHOD_INPUT_SIG);
			actionMet.invoke(this, new Object[] { request, out });
		} catch (NoSuchMethodException ex) {
			out.print(FtpResponse.RESPONSE_502_COMMAND_NOT_IMPLEMENTED);
			//out.write(ftpStatus.getResponse(502, request, user, null));
		} catch (InvocationTargetException ex) {
			logger.log(Level.ERROR, "Error", ex);
			out.print(
				new FtpResponse(
					500,
					"Uncaught exception: " + ex.getCause().toString()));
			Throwable th = ex.getTargetException();
			th.printStackTrace();
		} catch (Exception ex) {
			out.print(FtpResponse.RESPONSE_500_SYNTAX_ERROR);
			if (ex instanceof java.io.IOException) {
				throw (IOException) ex;
			}
		}
	}

	/**
	 * Check permission - default implementation - does nothing.
	 */
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

	/**
	 * User logout and stop this thread.
	 */
	public void stop() {
		stopRequest = true;
	}

	public void stop(String message) {
		stopRequestMessage = message;
		stop();
	}
	/**
	 * Monitor the user request.
	 */
	/**
	 * Get user object
	 */
	public User getUser() throws NoSuchUserException {
		if (_user == null)
			throw new NoSuchUserException("no user logged in for connection");
		return _user;
	}

	/**
	 * Get connection spy object
	 */
	/*
	public SpyConnectionInterface getSpyObject() {
	    return mSpy;
	}
	*/

	/**
	 * Set spy object
	 */
	/*
	public void setSpyObject(SpyConnectionInterface spy) {
	    mWriter.setSpyObject(spy);
	    mSpy = spy;
	}
	*/

	/**
	 * Get observer
	 */
	/*
	public FtpConnectionObserver getObserver() {
	    return mObserver;
	}
	*/
	/**
	 * Set observer
	 */
	/*
	public void setObserver(FtpConnectionObserver obsr) {
	    mObserver = obsr;
	}
	*/

	/////////// DATA CONNECTION ///////////
	protected Socket mDataSoc;
	protected ServerSocket mServSoc;
	/**
	 * Set by setPasvCommand to controlSocket.getLocalAddress()
	 * Set by setPortCommand to whatever the argument said.
	 */
	protected InetAddress mAddress;
	protected int miPort = 0;

	protected boolean mbPort = false;
	protected boolean mbPasv = false;
	/**
	 * Reset all the member variables. Close all sockets.
	 */
	public void reset() {

		// close data socket
		if (mDataSoc != null) {
			try {
				mDataSoc.close();
			} catch (Exception ex) {
				logger.log(Level.WARN, "Error closing data socket", ex);
			}
			mDataSoc = null;
		}

		// close server socket
		//		if (mServSoc != null) {
		//			try {
		//				mServSoc.close();
		//			} catch (Exception ex) {
		//				logger.log(Level.WARNING, "Error closing server socket", ex);
		//			}
		//			mServSoc = null;
		//		}

		// reset other variables
		mAddress = null;
		miPort = 0;

		mbPort = false;
		mbPasv = false;
	}

	/**
	 * Port command.
	 */
	public void setPortCommand(InetAddress addr, int port) {
		reset();
		mbPort = true;
		mAddress = addr;
		miPort = port;
	}

	/**
	 * Passive command. It returns the success flag.
	 */
	public boolean setPasvCommand() {
		try {
			reset();
			mAddress = controlSocket.getLocalAddress();
			mServSoc = new ServerSocket(0, 1, mAddress);
			//mServSoc = new ServerSocket(0, 1);
			mServSoc.setSoTimeout(60000);
			miPort = mServSoc.getLocalPort();
			mbPasv = true;
			return true;
		} catch (Exception ex) {
			logger.log(Level.WARN, "", ex);
			return false;
		}
	}

	/**
	 * Listen for passive socket connection. It returns the success flag.
	 */
	public boolean acceptPasvConnection() throws IOException {
		boolean bRet = false;
		mDataSoc = null;
		try {
			mDataSoc = mServSoc.accept();
			mDataSoc.setSoTimeout(60000);
			bRet = true;
		} catch (IOException ex) {
			throw ex;
		} catch (Exception ex) {
			throw new RuntimeException(ex);
		} finally {
			if (mServSoc != null)
				mServSoc.close();
			mServSoc = null;
		}
		return bRet;
	}

	/**
	 * Get client address from PORT command.
	 */
	public InetAddress getInetAddress() {
		if (preTransferRSlave != null) {
			return preTransferRSlave.getInetAddress();
		} else {
			return mAddress;
		}
	}

	/**
	 * Get port number.
	 * return miPort
	 */
	public int getPort() {
		return miPort;
	}

	/**
	 * Get the data socket. In case of error returns null.
	 * 
	 * Used by LIST and NLST.
	 */
	public Socket getDataSocket() throws IOException {

		// get socket depending on the selection
		if (mbPort) {
			try {
				mDataSoc = new Socket(mAddress, miPort);
				mDataSoc.setSoTimeout(30000); // 30 seconds timeout
			} catch (IOException ex) {
				//mConfig.getLogger().warn(ex);
				logger.log(Level.WARN, "Error opening data socket", ex);
				mDataSoc = null;
				throw ex;
			}
		} else if (mbPasv) {
			if (mDataSoc == null)
				acceptPasvConnection();
		}

		/* else if (!mbPasv) {
			if (mDataSoc != null) {
				try {
					mDataSoc.close();
				} catch (Exception ex) {
					//mConfig.getLogger().warn(ex);
					ex.printStackTrace();
				}
				mDataSoc = null;
			}
		}*/

		// result check
		return mDataSoc;
	}

	/**
	 * Last defense - close connections.
	 */
	protected void finalize() throws Throwable {
		reset();
		super.finalize();
	}

	/**
	 * Returns the authenticated.
	 * @return boolean
	 */
	public boolean isAuthenticated() {
		return authenticated;
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

	/**
	 * Returns true if client is executing a command.
	 */
	public boolean isExecuting() {
		return executing;
	}
	public boolean isTransfering() {
		return _transfer != null;
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

	protected LinkedRemoteFile currentDirectory;

	/**
	 * @return
	 */
	public LinkedRemoteFile getCurrentDirectory() {
		return currentDirectory;
	}

	/**
	 * @param file
	 */
	public void setCurrentDirectory(LinkedRemoteFile file) {
		currentDirectory = file;
	}
	protected Transfer _transfer;
	protected LinkedRemoteFile _transferFile;
	protected RemoteSlave _rslave;

	/**
		 * PRE Transfere
		 *
		 */
	protected RemoteSlave preTransferRSlave;
	protected boolean preTransfer;

	public Transfer getTransfer() {
		return _transfer;
	}

	public LinkedRemoteFile getTransferFile() {
		return _transferFile;
	}
	public RemoteSlave getTranferSlave() {
		if(!isTransfering()) throw new IllegalStateException("can only call getTransferSlave() during transfer");
		return _rslave;
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
}
