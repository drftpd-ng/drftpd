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
package org.drftpd.tests;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Collections;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.plugins.DataConnectionHandler;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

/**
 * @author mog
 * @version $Id: DummyBaseFtpConnection.java,v 1.1 2004/05/16 18:07:31 mog Exp $
 */
public class DummyBaseFtpConnection extends BaseFtpConnection {

	private UserManager _userManager;
	private DummyServerSocketFactory _serverSocketFactory;
	private DummySocketFactory _socketFactory;
	private StringWriter _out;

	private DataConnectionHandler _dch;

	public DummyBaseFtpConnection(DataConnectionHandler dch) {
		_dch = dch;
		_socketFactory = new DummySocketFactory();
		_serverSocketFactory = new DummyServerSocketFactory(_socketFactory);
		
		currentDirectory = new LinkedRemoteFile(null);
		currentDirectory.addFile(new StaticRemoteFile(Collections.EMPTY_LIST, "testfile", "drftpd", "drftpd", Bytes.parseBytes("10M"), System.currentTimeMillis()));
		_out = new StringWriter();
		out = new PrintWriter(_out);
	}
	public void setRequest(FtpRequest request) {
		this.request = request;
	}
	/**
	 * @deprecated
	 */
	protected void dispatchFtpEvent(Event event) {
		throw new UnsupportedOperationException();
	}

	public InetAddress getClientAddress() {
		throw new UnsupportedOperationException();
	}

	public CommandManager getCommandManager() {
		throw new UnsupportedOperationException();
	}

	public FtpConfig getConfig() {
		return null;
	}

	public ConnectionManager getConnectionManager() {
		throw new UnsupportedOperationException();
	}

	public Socket getControlSocket() {
		return new DummySocket();
	}

	public DataConnectionHandler getDataConnectionHandler() {
		return _dch;
	}

	public char getDirection() {
		throw new UnsupportedOperationException();

	}

	public long getLastActive() {
		throw new UnsupportedOperationException();

	}

	public SlaveManagerImpl getSlaveManager() {
		throw new UnsupportedOperationException();

	}

	public char getTransferDirection() {
		throw new UnsupportedOperationException();
	}

	public User getUser() throws NoSuchUserException {
		throw new UnsupportedOperationException();
	}

	public UserManager getUserManager() {
		return _userManager;
	}
	public void setUserManager(UserManager um) {
		_userManager = um;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#getUserNull()
	 */
	public User getUserNull() {
		throw new UnsupportedOperationException();
	}

	protected boolean hasPermission(FtpRequest request) {
		throw new UnsupportedOperationException();
	}

	public boolean isAuthenticated() {
		throw new UnsupportedOperationException();
	}

	public boolean isExecuting() {
		throw new UnsupportedOperationException();
	}

	/**
	 * @deprecated
	 */
	public void reset() {
		throw new UnsupportedOperationException();
	}

	public void resetState() {
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#service(net.sf.drftpd.master.FtpRequest, java.io.PrintWriter)
	 */
	public void service(FtpRequest request, PrintWriter out)
		throws IOException {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#setControlSocket(java.net.Socket)
	 */
	public void setControlSocket(Socket socket) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#setCurrentDirectory(net.sf.drftpd.remotefile.LinkedRemoteFile)
	 */
	public void setCurrentDirectory(LinkedRemoteFile file) {
		throw new UnsupportedOperationException();
	}

	public void setUser(User user) {
		super.setUser(user);
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#start()
	 */
	public void start() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#status()
	 */
	public String status() {
		return "dummy status string";
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#stop()
	 */
	public void stop() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.BaseFtpConnection#stop(java.lang.String)
	 */
	public void stop(String message) {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#hashCode()
	 */
	public int hashCode() {
		throw new UnsupportedOperationException();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#equals(java.lang.Object)
	 */
	public boolean equals(Object obj) {
		throw new UnsupportedOperationException();
	}

	protected Object clone() throws CloneNotSupportedException {
		throw new UnsupportedOperationException();
	}

	public ServerSocketFactory getServerSocketFactory() {
		return _serverSocketFactory;
	}
	public DummyServerSocketFactory getDummySSF() {
		return _serverSocketFactory;
	}

	public StringWriter getDummyOut() {
		return _out;
	}

	public SocketFactory getSocketFactory() {
		return _socketFactory;
	}

}
