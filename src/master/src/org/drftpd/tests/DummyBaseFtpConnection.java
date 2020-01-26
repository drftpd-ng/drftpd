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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.util.FtpRequest;
import org.drftpd.vfs.DirectoryHandle;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.Socket;


/**
 * @author mog
 * @version $Id$
 */
@SuppressWarnings("serial")
public class DummyBaseFtpConnection extends BaseFtpConnection {
    private InetAddress _clientAddress;
    private StringWriter _out2;
    private DummyServerSocketFactory _serverSocketFactory;
    private DummySocketFactory _socketFactory;
	private static final Logger logger = LogManager.getLogger(DummyBaseFtpConnection.class);

    public DummyBaseFtpConnection() {
        _socketFactory = new DummySocketFactory();
        _serverSocketFactory = new DummyServerSocketFactory(_socketFactory);

        _currentDirectory = new DirectoryHandle(null);
        try {
			_currentDirectory.createFileUnchecked("testfile", "drftpd", "drftpd", null);
		} catch (FileExistsException e) {
			logger.error(e);
		} catch (FileNotFoundException e) {
			logger.error(e);
		}
        _out2 = new StringWriter();
        _out = new PrintWriter(_out2);
    }

    public InetAddress getClientAddress() {
        if (_clientAddress == null) {
            throw new NullPointerException();
        }

        return _clientAddress;
    }

    public CommandManagerInterface getCommandManager() {
        throw new UnsupportedOperationException();
    }

    public Socket getControlSocket() {
        return new DummySocket();
    }

    public char getDirection() {
        throw new UnsupportedOperationException();
    }

    public StringWriter getDummyOut() {
        return _out2;
    }

    public DummyServerSocketFactory getDummySSF() {
        return _serverSocketFactory;
    }

    public long getLastActive() {
        throw new UnsupportedOperationException();
    }

    public ServerSocketFactory getServerSocketFactory() {
        return _serverSocketFactory;
    }

    public SocketFactory getSocketFactory() {
        return _socketFactory;
    }

    public char getTransferDirection() {
        throw new UnsupportedOperationException();
    }

    public User getUser() throws NoSuchUserException {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.BaseFtpConnection#getUserNull()
     */
    public User getUserNull() {
        return super.getUserNull();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    public int hashCode() {
        throw new UnsupportedOperationException();
    }

    protected boolean hasPermission(FtpRequest request) {
        throw new UnsupportedOperationException();
    }

    public boolean isExecuting() {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    @Deprecated
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
    public void service(FtpRequest request, PrintWriter out) {
        throw new UnsupportedOperationException();
    }

    public void setClientAddress(InetAddress clientAddress) {
        _clientAddress = clientAddress;
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.BaseFtpConnection#setControlSocket(java.net.Socket)
     */
    public void setControlSocket(Socket socket) {
        throw new UnsupportedOperationException();
    }

    public void setRequest(FtpRequest request) {
        _request = request;
    }

    public void setUser(String user) {
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
}
