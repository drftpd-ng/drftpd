package net.sf.drftpd.master;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;

import net.sf.drftpd.master.usermanager.User;

//import ranab.util.Message;
/*import ranab.io.StreamConnectorObserver;*/

/**
 * This is a generic ftp connection handler. It delegates 
 * the request to appropriate methods in subclasses.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 */
public class BaseFtpConnection implements Runnable {

	protected final static Class[] METHOD_INPUT_SIG =
		new Class[] { FtpRequest.class, PrintWriter.class };
	/**
	 * time when last command from the client finished execution
	 */
	protected long lastActive;
	/**
	 * Is the client running a command?
	 */
	protected boolean executing;
	
	protected FtpStatus mFtpStatus;
	//protected FtpDataConnection mDataConnection = null;
	protected User user;
	/**
	 * Is the current password authenticated?
	 */
	protected boolean authenticated = false;
	//protected SpyConnectionInterface mSpy       = null;
	//protected FtpConnectionObserver mObserver   = null;
	protected Socket mControlSocket;
	protected PrintWriter out;
	/**
	 * Should this thread stop insted of continue looping?
	 */
	protected boolean stopRequest = false;
	protected String stopRequestMessage;
	protected SlaveManagerImpl slavemanager;
	private InetAddress clientAddress = null;
	private Thread t;
	protected ConnectionManager connManager;
	protected FtpRequest request;
	public void start() {
		t = new Thread(this);
		t.start();
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
	public BaseFtpConnection(ConnectionManager connManager, Socket soc) {
		//mConfig = ftpConfig;
		//this.cfg = cfg;
		//mFtpStatus = mConfig.getStatus();
		mFtpStatus = new FtpStatus();
		mControlSocket = soc;
		this.connManager = connManager;
		//mUser = new FtpUser();
	}

	BufferedReader in;

	/**
	 * Server one FTP connection.
	 */
	public void run() {
		clientAddress = mControlSocket.getInetAddress();
		//mConfig.getLogger().info("Handling new request from " + clientAddress.getHostAddress());
		System.out.println(
			"Handling new request from " + clientAddress.getHostAddress());
		//mDataConnection = new FtpDataConnection(mConfig);
		//setClientAddress(clientAddress);
		//mConfig.getConnectionService().newConnection(this);

		try {
			in =
				new BufferedReader(
					new InputStreamReader(mControlSocket.getInputStream()));

			out =
				new PrintWriter(
					new FtpWriter(
						new BufferedWriter(
							new OutputStreamWriter(
								mControlSocket.getOutputStream()))));

			mControlSocket.setSoTimeout(500);
			// permission check
			/*
			    if( !mConfig.getIpRestrictor().hasPermission(mControlSocket.getInetAddress()) ) {
			        mWriter.write(mFtpStatus.getResponse(530, null, mUser, null));
			        return;
			    }
			*/
			out.write(mFtpStatus.getResponse(220, null, user, null));
			while(!stopRequest) {
				out.flush();
				//notifyObserver();
				String commandLine;
				try {
					commandLine = in.readLine();
				} catch(SocketTimeoutException ex) {
					continue;
				}
				if(stopRequest) break;
				// test command line
				if (commandLine == null) {
					break;
				}

				//spyRequest(commandLine);
				if (commandLine.equals("")) {
					continue;
				}

				request = new FtpRequest(commandLine);
				System.out.println("<< " + request.getCommandLine());

				if (!hasPermission(request)) {
					out.write(
						mFtpStatus.getResponse(530, request, user, null));
					continue;
				}
				// execute command
				executing = true;
				service(request, out);
				executing = false;
				lastActive = System.currentTimeMillis();
			}
			if(stopRequestMessage != null) {
				out.println("421 "+stopRequestMessage);
			} else {
				out.println("421 Connection closing");
			}
			out.flush();
		} catch (SocketException ex) {
			ex.printStackTrace();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			try {
				in.close();
				out.close();
			} catch (Exception ex2) {
				System.err.println("Warning, exception closing stream");
				ex2.printStackTrace();
			}
			/*
			ConnectionService conService = mConfig.getConnectionService();
			if (conService != null) {
			    conService.closeConnection(mUser.getSessionId());
			}
			*/
			connManager.remove(this);
		}
	}

	/**
	 * Execute the ftp command.
	 */
	public void service(FtpRequest request, Writer writer) throws IOException {
		try {
			String metName;
			metName = "do" + request.getCommand().replaceAll(" ", "");
			Method actionMet =
				getClass().getDeclaredMethod(metName, METHOD_INPUT_SIG);
			actionMet.invoke(this, new Object[] { request, writer });
		} catch (NoSuchMethodException ex) {
			writer.write(mFtpStatus.getResponse(502, request, user, null));
		} catch (InvocationTargetException ex) {
			ex.printStackTrace();
			writer.write(mFtpStatus.getResponse(500, request, user, null));
			Throwable th = ex.getTargetException();
			th.printStackTrace();
/*
 			if (th instanceof java.io.IOException) {
				throw (IOException) th;
			}
*/
		} catch (Exception ex) {
			writer.write(mFtpStatus.getResponse(500, request, user, null));
			ex.printStackTrace();
			if (ex instanceof java.io.IOException) {
				throw (IOException) ex;
			}
			/*
			    else {
			       mConfig.getLogger().warn(ex);
			    }
			*/
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
			|| "QUIT".equals(command))
			return true;

		return false;
	}

	/**
	 * User logout and stop this thread.
	 */
	public void stop() {
		stopRequest = true;
//		t.interrupt();
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
	 * Get client address.
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
	public Socket getDataSocket() {

		// get socket depending on the selection
		if (mbPort) {
			try {
				mDataSoc = new Socket(mAddress, miPort);
				mDataSoc.setSoTimeout(60000);
			} catch (Exception ex) {
				//mConfig.getLogger().warn(ex);
				ex.printStackTrace();
				mDataSoc = null;
			}
		} else if (!mbPasv) {
			if (mDataSoc != null) {
				try {
					mDataSoc.close();
				} catch (Exception ex) {
					//mConfig.getLogger().warn(ex);
					ex.printStackTrace();
				}
				mDataSoc = null;
			}
		}

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
		if(user != null) {
			buf.append("[user: "+user+"]");
		}
		if(request != null) {
			buf.append("[command: "+request.getCommand()+"]");
		}
		if(isExecuting()) {
			buf.append("[executing]");
		} else {
			buf.append("[idle: "+(System.currentTimeMillis()-getLastActive())+"ms]");
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

}
