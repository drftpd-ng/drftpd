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

import net.sf.drftpd.event.Event;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.plugins.DataConnectionHandler;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.drftpd.Bytes;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.net.InetAddress;
import java.net.Socket;

import java.util.Collections;

import javax.net.ServerSocketFactory;
import javax.net.SocketFactory;


/**
 * @author mog
 * @version $Id: DummyBaseFtpConnection.java,v 1.14 2004/11/09 19:00:00 mog Exp $
 */
public class DummyBaseFtpConnection extends BaseFtpConnection {
    private InetAddress _clientAddress;
    private DataConnectionHandler _dch;
    private StringWriter _out2;
    private DummyServerSocketFactory _serverSocketFactory;
    private DummySocketFactory _socketFactory;

    public DummyBaseFtpConnection(DataConnectionHandler dch) {
        _dch = dch;
        _socketFactory = new DummySocketFactory();
        _serverSocketFactory = new DummyServerSocketFactory(_socketFactory);

        _currentDirectory = new LinkedRemoteFile(null);
        _currentDirectory.addFile(new StaticRemoteFile(Collections.EMPTY_LIST,
                "testfile", "drftpd", "drftpd", Bytes.parseBytes("10M"),
                System.currentTimeMillis()));
        _out2 = new StringWriter();
        _out = new PrintWriter(_out2);
    }

    protected Object clone() throws CloneNotSupportedException {
        throw new UnsupportedOperationException();
    }

    /**
     * @deprecated
     */
    protected void dispatchFtpEvent(Event event) {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        throw new UnsupportedOperationException();
    }

    public InetAddress getClientAddress() {
        if (_clientAddress == null) {
            throw new NullPointerException();
        }

        return _clientAddress;
    }

    public CommandManager getCommandManager() {
        throw new UnsupportedOperationException();
    }

    public Socket getControlSocket() {
        return new DummySocket();
    }

    public DataConnectionHandler getDataConnectionHandler() {
        if (_dch == null) {
            throw new NullPointerException("No DataConnectionHandler set");
        }

        return _dch;
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

    public void setGlobalConext(DummyGlobalContext gctx) {
        _gctx = gctx;
    }
}
