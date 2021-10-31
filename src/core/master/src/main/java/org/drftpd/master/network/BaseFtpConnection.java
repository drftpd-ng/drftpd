/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.network;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.io.AddAsciiOutputStream;
import org.drftpd.common.socks.Ident;
import org.drftpd.common.util.HostMask;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandManagerInterface;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponseInterface;
import org.drftpd.master.event.ConnectionEvent;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.FtpRequest;
import org.drftpd.master.util.Time;
import org.drftpd.master.vfs.DirectoryHandle;

import javax.net.ssl.SSLSocket;
import java.io.*;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * This is a generic ftp connection handler. It delegates the request to
 * appropriate methods in subclasses.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author mog
 * @version $Id$
 */
public class BaseFtpConnection extends Session implements Runnable {

    // Keys for our BaseFtpConnection KeyedMap
    public static final Key<InetAddress>    ADDRESS = new Key<>(BaseFtpConnection.class, "address");
    public static final Key<String>         IDENT = new Key<>(BaseFtpConnection.class, "ident");
    public static final Key<Boolean>        FAILEDLOGIN = new Key<>(BaseFtpConnection.class, "failedlogin");
    public static final Key<String>         FAILEDREASON = new Key<>(BaseFtpConnection.class, "failedreason");
    public static final Key<String>         FAILEDUSERNAME = new Key<>(BaseFtpConnection.class, "failedusername");
    public static final Key<Boolean>        KILLGHOSTS = new Key<>(BaseFtpConnection.class, "killghosts");
    public static final Key<Boolean>        BOUNCERALLOWED = new Key<>(BaseFtpConnection.class, "bouncerallowed");
    public static final Key<List<HostMask>> HOSTMASKS = new Key<>(BaseFtpConnection.class, "hostmasks");

    private static final Logger logger = LogManager.getLogger(BaseFtpConnection.class);

    /**
     * Is the current password authenticated?
     */
    protected boolean _authenticated = false;
    protected Socket _controlSocket;
    protected DirectoryHandle _currentDirectory;
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
    private CommandManagerInterface _commandManager;
    private BufferedReader _in;
    private ThreadPoolExecutor _pool;
    private boolean _securityDataExchangeCompleted = false;
    private final AtomicInteger _commandCount = new AtomicInteger(0);

    protected BaseFtpConnection() {
    }

    public BaseFtpConnection(Socket soc) {
        setControlSocket(soc);
    }

    public static int countTransfersForUser(User user, char transferDirection) {

        int count = 0;
        for (BaseFtpConnection conn : GlobalContext.getConnectionManager().getConnections()) {
            if (conn.getUserNull() == user) {
                if (conn.getTransferState().getDirection() == transferDirection) {
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
     *
     * @param oldUsername the old Username before we renamed the user
     * @param newUsername the new Username
     */
    public static void fixBaseFtpConnUser(String oldUsername, String newUsername) {
        for (BaseFtpConnection conn : GlobalContext.getConnectionManager().getConnections()) {
            if (conn.getUsername() != null && conn.getUsername().equals(oldUsername)) {
                conn.setUser(newUsername);
            }
        }
    }

    public TransferState getTransferState() {
        TransferState ts;
        try {
            ts = getObject(TransferState.TRANSFERSTATE);
        } catch (KeyNotFoundException e) {
            ts = new TransferState();
            setObject(TransferState.TRANSFERSTATE, ts);
        }
        return ts;
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

    public void setControlSocket(Socket socket) {
        try {
            _controlSocket = socket;
            _in = new BufferedReader(new InputStreamReader(_controlSocket
                    .getInputStream(), StandardCharsets.ISO_8859_1));

            _out = new PrintWriter(new OutputStreamWriter(
                    new AddAsciiOutputStream(new BufferedOutputStream(
                            _controlSocket.getOutputStream())), StandardCharsets.ISO_8859_1));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public PrintWriter getControlWriter() {
        return _out;
    }

    public DirectoryHandle getCurrentDirectory() {
        return _currentDirectory;
    }

    public void setCurrentDirectory(DirectoryHandle path) {
        _currentDirectory = path;
    }

    /*
     * Returns thread id number
     */
    public long getThreadID() {
        return _thread.getId();
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
            return getGlobalContext().getUserManager().getUserByNameUnchecked(_user);
        } catch (UserFileException e) {
            throw new NoSuchUserException(e);
        }
    }

    public void setUser(String user) {
        _user = user;
    }

    public User getUserNull() {
        if (_user == null || !isAuthenticated()) {
            return null;
        }
        try {
            return getGlobalContext().getUserManager().getUserByNameUnchecked(_user);
        } catch (NoSuchUserException | UserFileException e) {
            logger.debug("[getUserNull] User {} does not exist or cannot be loaded", _user);
        }
        return null;
    }

    public User getUserNullUnchecked() {
        if (_user == null) {
            return null;
        }
        try {
            return getGlobalContext().getUserManager().getUserByNameUnchecked(_user);
        } catch (NoSuchUserException | UserFileException e) {
            logger.debug("[getUserNullUnchecked] User {} does not exist or cannot be loaded", _user);
        }
        return null;
    }

    /**
     * @return the username (string).
     */
    public String getUsername() {
        if (isAuthenticated()) {
            return _user;
        }
        return null;
    }

    public boolean isAuthenticated() {
        return _authenticated;
    }

    public void setAuthenticated(boolean authenticated) {
        _authenticated = authenticated;

        if (isAuthenticated()) {
            try {
                // If hideips is on, hide ip but not user/group
                if (GlobalContext.getConfig().getHideIps()) {
                    _thread.setName("FtpConn thread " + _thread.getId() + " servicing " + _user + "/" + getUser().getGroup());
                } else {
                    _thread.setName("FtpConn thread " + _thread.getId() + " from " + getClientAddress().getHostAddress() + " " + _user + "/" + getUser().getGroup());
                }
            } catch (NoSuchUserException e) {
                logger.error("User does not exist, yet user is authenticated, this is a bug");
            }
        }
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

    /**
     * Server one FTP connection.
     */
    public void run() {
        // Make sure we require BOUNCERALLOWED to be set
        if (getObject(BOUNCERALLOWED, null) == null) {
            logger.error("BOUNCERALLOWED is not set, this should not be possible... BUG");
            return;
        }

        // We initialize allowed connection to false
        boolean allowedConnection = false;
        if (getObject(BOUNCERALLOWED, false)) {
            // Since we expect a bouncer connect allow the connection regardless
            allowedConnection = true;
        } else {
            // Make sure we require HOSTMASKS to be set
            if (getObject(HOSTMASKS, null) == null || getObject(HOSTMASKS, new ArrayList<>()).size() < 1) {
                logger.error("HOSTMASKS is not set, this should not be possible... BUG");
                return;
            }
        }

        _commandManager = GlobalContext.getConnectionManager().getCommandManager();
        setCommands(GlobalContext.getConnectionManager().getCommands());
        _lastActive = System.currentTimeMillis();
        setCurrentDirectory(getGlobalContext().getRoot());
        _thread = Thread.currentThread();
        GlobalContext.getConnectionManager().dumpThreadPool();

        _lastActive = System.currentTimeMillis();
        if (GlobalContext.getConfig().getHideIps()) {
            logger.info("Handling new request from <iphidden>");
            _thread.setName("FtpConn thread " + _thread.getId() + " from <iphidden>");
        } else {
            logger.info("Handling new request from {}", getClientAddress().getHostAddress());
            _thread.setName("FtpConn thread " + _thread.getId() + " from " + getClientAddress().getHostAddress());
        }

        _pool = new ThreadPoolExecutor(1, Integer.MAX_VALUE, 60L, TimeUnit.SECONDS, new SynchronousQueue<>(), new CommandThreadFactory(_thread.getName()));

        try {
            // First handle any ident requirements
            int identTimeout = Ident.defaultConnectionTimeout;
            try {
                identTimeout = Integer.parseInt(GlobalContext.getConfig().getMainProperties().getProperty("ident.lookup.timeout"));
            } catch(NumberFormatException e) {
                logger.warn("Failed to get 'ident.lookup.timeout' property or get the integer from the value, defaulting to [{}]", identTimeout, e);
            }
            logger.debug("Ident Timeout has been set to [{}]", identTimeout);


            String ident = null;
            // Only loop over the hostmasks if we are not expecting a bouncer to connect
            if (!getObject(BOUNCERALLOWED, false)) {
                for (HostMask hm : getObject(HOSTMASKS)) {
                    // Does this hostmask need ident to verify connection?
                    if (hm.isIdentMaskSignificant()) {
                        if (ident == null) {
                            logger.debug("One of the hostmasks requires ident so we get it regardless of the other hostmasks");
                            try {
                                ident = new Ident(_controlSocket, identTimeout).getUserName();
                            } catch (IOException e) {
                                if (GlobalContext.getConfig().getHideIps()) {
                                    logger.warn("Failed to get ident for <iphidden>");
                                } else {
                                    logger.warn("Failed to get ident for {}", _controlSocket.getInetAddress().getHostAddress());
                                }
                                // Because we tried to get ident and it failed, we change it from 'null' to '""'
                                ident = "";
                            }
                        }
                        if (hm.matchesIdent(ident)) {
                            allowedConnection = true;
                        }
                    } else {
                        allowedConnection = true;
                    }
                }
            }
            if (!allowedConnection) {
                logger.warn("Closing connecting as it is not allowed based on existing hostmasks");
                _stopRequest = true;
            } else {
                _controlSocket.setSoTimeout(1000);

                // If the ident is null it was not necessary. In order for the remaining logic (login etc) to function normally we set it to "" here.
                // NOTE: when we have a bouncer connect it sets the ident regardless of this
                if (ident == null) {
                    ident = "";
                }

                // Store the ident
                logger.debug("Setting ident for this connection to [{}]", ident);
                setObject(IDENT, ident);

                if (GlobalContext.getGlobalContext().isShutdown()) {
                    stop(GlobalContext.getGlobalContext().getShutdownMessage());
                } else {
                    FtpReply response = new FtpReply(220, GlobalContext.getConfig().getLoginPrompt());
                    _out.print(response);
                }
            }

            while (!_stopRequest) {
                _out.flush();

                String commandLine;

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
                            && ((System.currentTimeMillis() - _lastActive) / 1000 >= idleTime)
                            && !isExecuting()) {
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
                    logger.debug("<< {}", _request.getCommandLine());
                }

                // execute command
                _pool.execute(new CommandThread(_request, this));
                if (_request.getCommand().equalsIgnoreCase("AUTH")) {
                    while (!_securityDataExchangeCompleted && !_stopRequest) {
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
            logger.log(Level.INFO, ex.getMessage() + ", closing for user " + ((_user == null) ? "<not logged in>" : _user), ex);
        } catch (Exception ex) {
            logger.log(Level.INFO, "Exception, closing", ex);
        } finally {
            shutdownSocket();

            if (isAuthenticated()) {
                try {
                    getUser().updateLastAccessTime();
                } catch (NoSuchUserException e) {
                    logger.error("User does not exist, yet user is authenticated, this is a bug");
                }

                GlobalContext.getEventService().publishAsync(new ConnectionEvent(getUserNull(), "LOGOUT"));
            }

            if (isExecuting()) {
                super.abortCommand();
            }
            // Reset just the transfer if one is active, a full reset of the TransferState instance is
            // not required as this object will not be reused. Leaving the rest of the state untouched
            // will allow any active command threads to terminate gracefully.
            getTransferState().resetTransfer();
            _pool.shutdown();
            GlobalContext.getConnectionManager().remove(this);
            GlobalContext.getConnectionManager().dumpThreadPool();

            Thread t = Thread.currentThread();
            t.setName(ConnectionThreadFactory.getIdleThreadName(t.getId()));
        }
    }

    /**
     * returns a two-line status
     */
    public String status() {
        return jprintf(_commandManager.getResourceBundle(), "statusline", _user);
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
        StringBuilder buf = new StringBuilder("[BaseFtpConnection");

        if (_user != null) {
            buf.append("[user: ").append(_user).append("]");
        }

        if (_request != null) {
            buf.append("[command: ").append(_request.getCommand()).append("]");
        }

        if (isExecuting()) {
            buf.append("[executing]");
        } else {
            buf.append("[idle: ").append(Time.formatTime(System.currentTimeMillis() - getLastActive())).append("]");
        }

        buf.append("]");

        return buf.toString();
    }

    public synchronized void printOutput(Object o) {
        _out.print(o);
        _out.flush();
    }

    public synchronized void printOutput(int code, Object o) {
        _out.print(code + "- " + o.toString() + "\n");
        _out.flush();
    }

    public boolean hasSecurityExchangeCompleted() {
        return _securityDataExchangeCompleted;
    }

    public void securityExchangeCompleted() {
        _securityDataExchangeCompleted = true;
    }

    public void poolStatus() {
        logger.debug("pool size: {}", _pool.getPoolSize());
        logger.debug("active threads: {}", _pool.getActiveCount());
    }

    @Override
    public void abortCommand() {
        super.abortCommand();
        if (getTransferState().abort("Transfer aborted")) {
            printOutput(new FtpReply(426, "Connection closed; transfer aborted."));
        }
    }

    public void shutdownSocket() {
        try {
            if (_in != null) {
                _in.close();
            }
        } catch (Exception ex) {
            // Already closed
        }
        try {
            if (_out != null) {
                _out.close();
            }
        } catch (Exception ex) {
            // Already closed
        }
        try {
            if (_controlSocket != null) {
                _controlSocket.close();
            }
        } catch (Exception ex) {
            // Already closed
        }
    }

    /**
     * This is required because otherwise the superclass Hashtable.equals() will be
     * used which treats two maps as equal if the contents/size of the maps are equal
     * including for empty maps. For connections we only want equality to be true if
     * the objects being compared truly are the same connection.
     */
    @Override
    public boolean equals(Object o) {
        return o == this;
    }

    static class CommandThreadFactory implements ThreadFactory {

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

    class CommandThread implements Runnable {

        private final FtpRequest _ftpRequest;

        private final BaseFtpConnection _conn;

        private CommandThread(FtpRequest ftpRequest, BaseFtpConnection conn) {
            _ftpRequest = ftpRequest;
            _conn = conn;
        }

        public void run() {
            logger.debug("CommandThread started -#- _commandCount: {}, line: {}", _commandCount.get(), _ftpRequest.getCommandLine());
            if (_commandCount.get() > 0 && !_ftpRequest.getCommand().equalsIgnoreCase("ABOR")) {
                logger.warn("ABORT found, ignoring incoming line [{}]", _ftpRequest.getCommandLine());
                return;
            }
            _commandCount.incrementAndGet();
            clearAborted();
            logger.debug("commandThread[{}] - commandCount: {} -#- creating command request", _ftpRequest.getCommandLine(), _commandCount);
            CommandRequestInterface cmdRequest = _commandManager.newRequest(
                    _ftpRequest.getCommand(), _ftpRequest.getArgument(),
                    _currentDirectory, _conn.getUsername(), _conn, _conn.getCommands().get(_ftpRequest.getCommand()));
            logger.debug("commandThread[{}] - commandCount: {} -#- executing command request", _ftpRequest.getCommandLine(), _commandCount);
            CommandResponseInterface cmdResponse = _commandManager.execute(cmdRequest);
            logger.debug("commandThread[{}] - commandCount: {} -#- executing finished", _ftpRequest.getCommandLine(), _commandCount);
            if (cmdResponse != null) {
                if (!isAborted() || _ftpRequest.getCommand().equalsIgnoreCase("ABOR")) {
                    if (cmdResponse.getCurrentDirectory() != null) {
                        _currentDirectory = cmdResponse.getCurrentDirectory();
                    }
                    if (cmdResponse.getUser() != null) {
                        _user = cmdResponse.getUser();
                    }
                    printOutput(new FtpReply(cmdResponse));
                }
            }

            if (cmdRequest.getSession().getObject(BaseFtpConnection.FAILEDLOGIN, false)) {
                _conn.stop("Closing Connection");
            }

            _commandCount.decrementAndGet();
            logger.debug("commandThread[{}] - commandCount: {} -#- thread finished", _ftpRequest.getCommandLine(), _commandCount);
        }
    }
}
