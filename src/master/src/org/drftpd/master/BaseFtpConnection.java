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
package org.drftpd.master;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLSocket;


import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.commandmanager.CommandRequestInterface;
import org.drftpd.commandmanager.CommandResponseInterface;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.event.ConnectionEvent;
import org.drftpd.io.AddAsciiOutputStream;
import org.drftpd.slave.Transfer;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.FtpRequest;
import org.drftpd.util.ReplacerUtils;
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
public class BaseFtpConnection extends Session implements Runnable {
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
	private CommandManagerInterface _commandManager;

	protected Socket _controlSocket;
	
	protected KeyedMap<Key, Object> _keyedMap = new KeyedMap<Key, Object>();
	
	public KeyedMap<Key, Object> getKeyedMap() {
		return _keyedMap;
	}
	
	public TransferState getTransferState() {
		TransferState ts;
		try {
			ts = (TransferState) getKeyedMap().getObject(TransferState.TRANSFERSTATE);
		} catch (KeyNotFoundException e) {
			ts = new TransferState();
			getKeyedMap().setObject(TransferState.TRANSFERSTATE,ts);
		}
		return ts;
	}
	
	protected DirectoryHandle _currentDirectory;

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

	private ThreadPoolExecutor _pool;

	private boolean _authDone = false;

	protected BaseFtpConnection() {
	}

	public BaseFtpConnection(Socket soc) throws IOException {
		_commandManager = GlobalContext.getConnectionManager().getCommandManager();
		setCommands(GlobalContext.getConnectionManager().getCommands());
		setControlSocket(soc);
		_lastActive = System.currentTimeMillis();
		setCurrentDirectory(getGlobalContext().getRoot());
	}

	public static ReplacerEnvironment getReplacerEnvironment2(
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
		env = getReplacerEnvironment2(env, user);

		return SimplePrintf.jprintf(format, env);
	}

	public static String jprintf(Class class1, String key,
			ReplacerEnvironment env, User user) {
		env = getReplacerEnvironment2(env, user);
		ResourceBundle bundle = ResourceBundle.getBundle(class1.getName());

		return ReplacerUtils.jprintf(key, env, bundle);
	}

	public static String jprintfExceptionStatic(Class class1, String key,
			ReplacerEnvironment env, User user) throws FormatterException {
		env = getReplacerEnvironment2(env, user);
		ResourceBundle bundle = ResourceBundle.getBundle(class1.getName());

		return SimplePrintf
				.jprintf(ReplacerUtils.finalFormat(bundle, key), env);
	}

	/**
	 * Get client address
	 */
	public InetAddress getClientAddress() {
		return _controlSocket.getInetAddress();
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
	
	/**
	 * @return the username (string).
	 */
	public String getUsername() {
		return _user;
	}

	public boolean isAuthenticated() {
		return _authenticated;
	}

	/**
	 * Returns true if client is executing a command.
	 */
	public boolean isExecuting() {
		return _pool.getActiveCount() > 0;
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
		_thread = Thread.currentThread();
		GlobalContext.getConnectionManager().dumpThreadPool();
		
		_lastActive = System.currentTimeMillis();
		if (!GlobalContext.getConfig().getHideIps()) {
			logger.info("Handling new request from "
					+ getClientAddress().getHostAddress());
			_thread.setName("FtpConn thread " + _thread.getId() + " from "
					+ getClientAddress().getHostAddress());
		} else {
			logger.info("Handling new request from <iphidden>");
			_thread.setName("FtpConn thread " + _thread.getId()
					+ " from <iphidden>");
		}
		
		_pool = new ThreadPoolExecutor(1, Integer.MAX_VALUE,
                60L, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
                new CommandThreadFactory(_thread.getName()));

		try {
			_controlSocket.setSoTimeout(1000);

			if (GlobalContext.getGlobalContext().isShutdown()) {
				stop(GlobalContext.getGlobalContext().getShutdownMessage());
			} else {
				FtpReply response = new FtpReply(220, GlobalContext.getConfig().getLoginPrompt());
				_out.print(response);
			}

			while (!_stopRequest) {
				_out.flush();

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
						if (idleTime > 0) {
							_pool.setKeepAliveTime(idleTime, TimeUnit.SECONDS);
						}
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

				if (commandLine.equals("")) {
					continue;
				}

				_request = new FtpRequest(commandLine);

				if (!_request.getCommand().equals("PASS")) {
					debuglogger.debug("<< " + _request.getCommandLine());
				}

				// execute command
				_pool.execute(new CommandThread(_request, this));
				if (_request.getCommand().equalsIgnoreCase("AUTH")) {
					while(!_authDone) {
						Thread.sleep(100);
					}
				}
				poolStatus();
				_lastActive = System.currentTimeMillis();
			}

			if (_stopRequestMessage != null) {
				_out.print(new FtpReply(421, _stopRequestMessage));
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
				if (_in != null) {
					_in.close();
				}
				if (_out != null) {
					_out.close();
				}
			} catch (Exception ex2) {
				logger.log(Level.WARN, "Exception closing stream", ex2);
			}

			if (isAuthenticated()) {
				try {
					getUser().updateLastAccessTime();
				} catch (NoSuchUserException e) {
					logger.error("User does not exist, yet user is authenticated, this is a bug");
				}
				
				GlobalContext.getEventService().publish(new ConnectionEvent(getUserNull(), "LOGOUT"));
			}

			GlobalContext.getConnectionManager().remove(this);
			GlobalContext.getConnectionManager().dumpThreadPool();
			
			Thread t = Thread.currentThread();
			t.setName(ConnectionThreadFactory.getIdleThreadName(t.getId()));
		}
	}

	public void setAuthenticated(boolean authenticated) {
		_authenticated = authenticated;

		if (isAuthenticated()) {
			try {
				// If hideips is on, hide ip but not user/group
				if (GlobalContext.getConfig().getHideIps()) {
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
		getTransferState().abort("Your connection is being shutdown");
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

	public static int countTransfersForUser(User user, char transferDirection) {
		List<BaseFtpConnection> conns = Collections.unmodifiableList(GlobalContext.getConnectionManager().getConnections());
		
		int count = 0;
		for (BaseFtpConnection conn : conns) {
			if (conn.getUserNull() == user) {
				if (conn.getTransferState().isTransfering()) {
					count++;
				} // else we dont need to process it.
			}
		}
		return count;
	}
	
	/**
	 * When a user is renamed the control connection looses its owner since the reference to the User
	 * is actually made thru a String containing the username.<br>
	 * This methods iterates thru all control connections trying to match connections owned by 'oldUsername'
	 * and re-sets it to 'newUsername'. 
	 * @param oldUsername
	 * @param newUsername
	 */
	public static void fixBaseFtpConnUser(String oldUsername, String newUsername) {
		List<BaseFtpConnection> list = GlobalContext.getConnectionManager().getConnections();
		synchronized (list) {
			List<BaseFtpConnection> conns = Collections.unmodifiableList(list);
			for (BaseFtpConnection conn : conns)
				if (conn.getUsername().equals(oldUsername))
					conn.setUser(newUsername);
		}
	}

	public synchronized void printOutput(Object o) {
		_out.print(o);
		_out.flush();
	}

	public synchronized void printOutput(int code, Object o) {
		_out.print(code+"- "+o.toString()+"\n");
		_out.flush();
	}

	public void authDone() {
		_authDone = true;
	}

	public void poolStatus() {
		//logger.debug("pool size: "+_pool.getPoolSize());
		//logger.debug("active threads: "+_pool.getActiveCount());
	}

	class CommandThread implements Runnable {

		private FtpRequest _ftpRequest;

		private BaseFtpConnection _conn;

		private CommandThread(FtpRequest ftpRequest, BaseFtpConnection conn) {
			_ftpRequest = ftpRequest;
			_conn = conn;
		}

		public void run() {
			// Remove this for now as it seems to cause lockups due to a race between checking active count and the old thread dying
			/*if (_pool.getActiveCount() > 1 && !_ftpRequest.getCommand().equalsIgnoreCase("ABOR")) {
				return;
			}*/
			CommandRequestInterface cmdRequest = _commandManager.newRequest(
					_ftpRequest.getCommand(), _ftpRequest.getArgument(),
					_currentDirectory, _user, _conn, _conn.getCommands().get(_ftpRequest.getCommand()));
			CommandResponseInterface cmdResponse = _commandManager
				.execute(cmdRequest);
			if (cmdResponse != null) {
				if (cmdResponse.getCurrentDirectory() instanceof DirectoryHandle)
					_currentDirectory = cmdResponse.getCurrentDirectory();
				if (cmdResponse.getUser() instanceof String)
					_user = cmdResponse.getUser();
				printOutput(new FtpReply(cmdResponse));
			}
		}
	}

	class CommandThreadFactory implements ThreadFactory {

		String _parentName;

		private CommandThreadFactory(String parentName) {
			_parentName = parentName;
		}

		public Thread newThread(Runnable r) {
			Thread ret = Executors.defaultThreadFactory().newThread(r);
			ret.setName(_parentName + " - " + ret.getName());
			return ret;
		}
	}
}
