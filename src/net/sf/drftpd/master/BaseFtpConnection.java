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
import java.rmi.NoSuchObjectException;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;

//import ranab.util.Message;
/*import ranab.io.StreamConnectorObserver;*/

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
		logger.setLevel(Level.FINEST);
	}
	/**
	 * time when last command from the client finished execution
	 */
	protected long lastActive;
	/**
	 * Is the client running a command?
	 */
	protected boolean executing;

	protected User user;
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
	public BaseFtpConnection(ConnectionManager connManager, Socket soc, Writer debugLog) {
		this.controlSocket = soc;
		this.connManager = connManager;
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
		//mDataConnection = new FtpDataConnection(mConfig);
		//mConfig.getConnectionService().newConnection(this);

		try {
			in =
				new BufferedReader(
					new InputStreamReader(controlSocket.getInputStream()));

			out = new PrintWriter(
				//new FtpWriter( no need for spying :P
	new BufferedWriter(
		new OutputStreamWriter(controlSocket.getOutputStream())));

			controlSocket.setSoTimeout(1000);
			// permission check
			/*
			    if( !mConfig.getIpRestrictor().hasPermission(mControlSocket.getInetAddress()) ) {
			        mWriter.write(mFtpStatus.getResponse(530, null, mUser, null));
			        return;
			    }
			*/
			if(connManager.isShutdown()) {
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
				//TODO log somewhere else!
				logger.fine(
					"<< "
						+ request.getCommandLine()
						+ " [user="
						+ user
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
			logger.log(Level.INFO, "SocketException, closing", ex);
		} catch (Exception ex) {
			logger.log(Level.INFO, "Exception, closing", ex);
		} finally {
			try {
				in.close();
				out.close();
			} catch (Exception ex2) {
				logger.log(Level.WARNING, "Exception closing stream", ex2);
			}
			if (isAuthenticated())
				dispatchFtpEvent(new UserEvent(getUser(), "LOGOUT"));
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
			logger.log(Level.SEVERE, "", ex);
			out.print(
				new FtpResponse(
					500,
					"Server error. " + ex.getCause().toString()));
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
	/*
	protected void spyRequest(final String str) {
	    final SpyConnectionInterface spy = mSpy;
	    if (spy != null) {
	        Message msg = new Message() {
	            public void execute() {
	                try {
	                    spy.request(str + '\n');
	                }
	                catch(Exception ex) {
	                    mSpy = null;
	                    mConfig.getLogger().error(ex);
	                }
	            }
	        };
	        mConfig.getMessageQueue().add(msg);
	    }
	}
	*/
	/**
	 * Get user object
	 */
	public User getUser() {
		if (user == null)
			throw new RuntimeException(new NoSuchObjectException("no user logged in for connection"));
		return user;
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

	/**
	 * Notify observer.
	 */
	/*
	public void notifyObserver() {
	   mUser.hitUser();
	   final FtpUser thisUser = mUser; 
	   final FtpConnectionObserver obsr = mObserver;
	
	   if (obsr != null) {
	        Message msg = new Message() {
	            public void execute() {
	                obsr.updateConnection(thisUser);
	            }
	        };
	        mConfig.getMessageQueue().add(msg);
	   }
	}
	*/
	/**
	 * This method tracks data transfer.
	 */
	/*
	public void dataTransferred(int sz) {
		//notifyObserver();
	}
	*/
	/**
	 * Get config object
	 */
	/*
	public FtpConfig getConfig() {
	    return mConfig;
	} 
	*/

	/**
	 * Get status object
	 */
	/*
	public FtpStatus getStatus() {
		return mFtpStatus;
	}
	*/
	/////////// DATA CONNECTION ///////////
	protected Socket mDataSoc;
	protected ServerSocket mServSoc;
	protected InetAddress mAddress;
	protected int miPort = 0;

	protected boolean mbPort = false;

	/**
	 * Reset all the member variables. Close all sockets.
	 */
	public void reset() {

		// close data socket
		if (mDataSoc != null) {
			try {
				mDataSoc.close();
			} catch (Exception ex) {
				//mConfig.getLogger().warn(ex);
				ex.printStackTrace();
			}
			mDataSoc = null;
		}

		// close server socket
		if (mServSoc != null) {
			try {
				mServSoc.close();
			} catch (Exception ex) {
				//mConfig.getLogger().warn(ex);
				ex.printStackTrace();
			}
			mServSoc = null;
		}

		// reset other variables
		mAddress = null;
		miPort = 0;

		mbPort = false;
		//mbPasv = false;
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
	/*
	public boolean setPasvCommand() {
		boolean bRet = false;
		try {
			reset();
			//mAddress = mConfig.getSelfAddress();
			//mServSoc = new ServerSocket(0, 1, mAddress);
			mServSoc = new ServerSocket(0, 1);
			mServSoc.setSoTimeout(60000);
			miPort = mServSoc.getLocalPort();
			mbPasv = true;
			bRet = true;
		} catch (Exception ex) {
			//mConfig.getLogger().warn(ex);
			ex.printStackTrace();
		}
		return bRet;
	}
	*/

	/**
	 * Listen for passive socket connection. It returns the success flag.
	 */
	/*
	public boolean listenPasvConnection() {
		boolean bRet = false;
		mDataSoc = null;
		try {
			mDataSoc = mServSoc.accept();
			mDataSoc.setSoTimeout(60000);
			bRet = true;
		} catch (Exception ex) {
			//mConfig.getLogger().warn(ex);
		}
		return bRet;
	}
	*/

	/**
	 * Get client address from PORT command.
	 */
	public InetAddress getInetAddress() {
		return mAddress;
	}

	/**
	 * Get port number.
	 */
	public int getPort() {
		return miPort;
	}

	/**
	 * Get the data socket. In case of error returns null.
	 */
	public Socket getDataSocket() throws IOException {

		// get socket depending on the selection
		if (mbPort) {
			try {
				mDataSoc = new Socket(mAddress, miPort);
				mDataSoc.setSoTimeout(30000); // 30 seconds timeout
			} catch (IOException ex) {
				//mConfig.getLogger().warn(ex);
				ex.printStackTrace();
				mDataSoc = null;
				throw ex;
			}
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
		if (user != null) {
			buf.append("[user: " + user + "]");
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
		return transfer != null;
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
	protected Transfer transfer;

}
