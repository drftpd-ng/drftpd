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
package org.drftpd.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.socks.Ident;
import org.drftpd.common.util.HostMask;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.commands.CommandManagerInterface;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.ConnectionThreadFactory;
import org.drftpd.master.network.FtpReply;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @version $Id$
 */
public class Master {
    private static final Logger logger = LogManager.getLogger(Master.class.getName());

    private static final String cmdConf = "config/commands/ftp";

    private static final String themeDir = "config/themes/ftp";

    private static Master _master = null;

    private HashMap<String, Properties> _cmds;
    private CommandManagerInterface _commandManager = null;
    private final List<BaseFtpConnection> _conns = new Vector<>();
    private ThreadPoolExecutor _pool;

    /**
     * If you're creating a ConnectionManager object and it's not part of a TestCase
     * you're not doing it correctly, ConnectionManager is a Singleton
     */
    protected Master() {
        getGlobalContext().getSlaveManager().addShutdownHook();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public static void main(String... args) {
        Master.boot();
    }

    public static Master getConnectionManager() {
        if (_master == null) {
            _master = new Master();
        }
        return _master;
    }

    public static InetAddress getBindIP() {
        try {
            String bindIP = PropertyHelper.getProperty(GlobalContext.getConfig().getMainProperties(), "master.ip", "");
            logger.debug("'master.ip' has been resolved to " + bindIP);
            if (bindIP.length() > 0) {
                return InetAddress.getByName(bindIP);
            }
        } catch(UnknownHostException e) {
            logger.warn("'master.ip' is not a valid ip address");
        } catch(Exception e) {
            logger.error("Unknown error occurred trying to get 'master.ip' config", e);
        }
        return null;
    }

    public static void boot() {
        System.out.println(GlobalContext.VERSION + " Master starting.");
        System.out.println("https://github.com/drftpd-ng/drftpd");
        System.out.println("Further logging will be done using (mostly) log4j, check logs/");
        // Set current thread name to make it clear in logfiles what is coming from the main master process
        // instead of being named after the wrapper
        Thread.currentThread().setName("Master Main Thread");

        try {
            logger.info("Starting ConnectionManager");

            GlobalContext.getGlobalContext().init();

            // Load our main configuration options
            Properties cfg = GlobalContext.getConfig().getMainProperties();

            getConnectionManager().loadCommands();

            // initialise command manager before accepting connections
            getConnectionManager().initCommandManager();

            // listen for connections
            ServerSocket server;

            if (getBindIP() != null) {
                server = new ServerSocket();
                server.bind(new InetSocketAddress(getBindIP(), Integer.parseInt(PropertyHelper.getProperty(cfg, "master.port"))));
                logger.info("Listening on {}:{}", server.getInetAddress(), server.getLocalPort());
            } else {
                server = new ServerSocket(Integer.parseInt(PropertyHelper.getProperty(cfg, "master.port")));
                logger.info("Listening on port {}", server.getLocalPort());
            }

            getConnectionManager().createThreadPool();

            while (true) {
                try {
                    getConnectionManager().start(server.accept());
                } catch(IOException e) {
                    logger.error("Caught IOException while accepting new connection", e);
                    break;
                }
            }
            logger.error("Reached the end of 'main' thread");

            // catches subclasses of Error and Exception
        } catch (Throwable th) {
            th.printStackTrace();
            logger.error("", th);
            System.exit(1);
        }
    }

    public static GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }

    public void createThreadPool() {
        int maxAliveThreads = GlobalContext.getConfig().getMaxUsersTotal() + GlobalContext.getConfig().getMaxUsersExempt();
        int minAliveThreads = (int) Math.round(maxAliveThreads * 0.25);

        _pool = new ThreadPoolExecutor(minAliveThreads, maxAliveThreads, 3 * 60, TimeUnit.SECONDS,
                new SynchronousQueue<>(), new ConnectionThreadFactory(), new ThreadPoolExecutor.AbortPolicy());
        _pool.allowCoreThreadTimeOut(false);
        _pool.prestartAllCoreThreads();
    }

    public void dumpThreadPool() {
        logger.debug("Active threads: {} / Completed Tasks: {}", _pool.getActiveCount(), _pool.getCompletedTaskCount());
        logger.debug("Pool information - Min # of threads: {} / Max: {}", _pool.getCorePoolSize(), _pool.getMaximumPoolSize());
        logger.debug("Current # of threads: {}", _pool.getPoolSize());
    }

    public FtpReply canLogin(BaseFtpConnection baseConn, User user) {
        int count = GlobalContext.getConfig().getMaxUsersTotal();

        // Math.max if the integer wraps
        if (GlobalContext.getConfig().isLoginExempt(user)) {
            count = Math.max(count, count + GlobalContext.getConfig().getMaxUsersExempt());
        }

        // not >= because baseConn is already included
        if (_conns.size() > count) {
            return new FtpReply(550, "The site is full, try again later.");
        }

        int userCount = 0;
        int ipCount = 0;

        synchronized (_conns) {
            for (BaseFtpConnection tempConnection : _conns) {
                try {
                    User tempUser = tempConnection.getUser();

                    if (tempUser.getName().equals(user.getName())) {
                        userCount++;
                        if (tempConnection.getClientAddress().equals(baseConn.getClientAddress())) {
                            ipCount++;
                        }
                    }
                } catch (NoSuchUserException ex) {
                    // do nothing, we found our current connection, baseConn = tempConnection
                }
            }
        }

        int maxLogins = user.getKeyedMap().getObjectInteger(UserManagement.MAXLOGINS);
        if (maxLogins > 0) {
            if (maxLogins <= userCount) {
                return new FtpReply(530, "Sorry, your account is restricted to " + maxLogins + " simultaneous logins.");
            }
        }

        int maxLoginsIP = user.getKeyedMap().getObjectInteger(UserManagement.MAXLOGINSIP);
        if (maxLoginsIP > 0) {
            if (maxLoginsIP <= ipCount) {
                return new FtpReply(530, "Sorry, your maximum number of connections from this IP (" + maxLoginsIP + ") has been reached.");
            }
        }

        Date banTime = user.getKeyedMap().getObject(UserManagement.BANTIME, new Date());
        if (banTime.getTime() > System.currentTimeMillis()) {
            return new FtpReply(530, "Sorry you are banned until " + banTime + "! (" + user.getKeyedMap().getObjectString(UserManagement.BANREASON) + ")");
        }

        if (!baseConn.isSecure() && GlobalContext.getConfig().checkPermission("userrejectinsecure", user)) {
            return new FtpReply(530, "USE SECURE CONNECTION");
        } else if (baseConn.isSecure() && GlobalContext.getConfig().checkPermission("userrejectsecure", user)) {
            return new FtpReply(530, "USE INSECURE CONNECTION");
        }

        return null; // everything passed
    }

    private void initCommandManager() {
        if (_commandManager == null) {
            _commandManager = getGlobalContext().createCommandManager();
            if (_commandManager != null) {
                _commandManager.initialize(getCommands(), themeDir);
            }
        }
    }

    public CommandManagerInterface getCommandManager() {
        return _commandManager;
    }

    /**
     * returns a {@code Collection} of current connections
     */
    public List<BaseFtpConnection> getConnections() {
        return new ArrayList<>(_conns);
    }

    public void remove(BaseFtpConnection conn) {
        if (!_conns.remove(conn)) {
            throw new RuntimeException("connections.remove() returned false.");
        }
    }

    public void shutdownPrivate(String message) {
        for (BaseFtpConnection conn : getConnections()) {
            conn.stop(message);
        }
    }

    public void start(Socket sock) throws IOException {
        if (getGlobalContext().isShutdown()) {
            new PrintWriter(sock.getOutputStream()).println("421 " + getGlobalContext().getShutdownMessage());
            sock.close();
            return;
        }

        /*
         * TODO: Reserved for Implicit SSL:
         * SSLSocket sslSock = (SSLSocket) sock;
         * logger.debug("[{}] Enabled ciphers for this new connection are as follows: '{}'",
         *           sslSock.getRemoteSocketAddress(), Arrays.toString(sslSock.getEnabledCipherSuites()));
         * logger.debug("[{}] Enabled protocols for this new connection are as follows: '{}'",
         *           sslSock.getRemoteSocketAddress(), Arrays.toString(sslSock.getEnabledProtocols()));
         * sslSock.setUseClientMode(false);
         * sslSock.startHandshake();
         * sock = sslSock;
         */

        // Check hostmasks before we move further unless we expect a bouncer to connect (which is handled during doIDNT)
        List<HostMask> masks = new ArrayList<>();
        if (!GlobalContext.getConfig().getBouncerIps().contains(sock.getInetAddress())) {

            // Get a list of masks that match the client IP
            // NOTE: Ident is handled later as it could introduce a timeout during accept() which is not acceptable
            for (User u : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
                // Skip if user is deleted
                if (!u.isDeleted()) {
                    masks.addAll(u.getHostMaskCollection().getMatchingMasks(sock));
                }
            }

            // If we have 0 matched hostmasks handle it quickly
            if (masks.size() < 1) {
                logger.warn("Closing connecting as it is not allowed based on existing hostmasks");
                sock.close();
                return;
            }
        }

        // Initialize a new BaseFtpConnection
        BaseFtpConnection conn = new BaseFtpConnection(sock);

        // If we get here it means at least one hostmask was matched so register it
        conn.setObject(BaseFtpConnection.HOSTMASKS, masks);

        _conns.add(conn);
        try {
            _pool.execute(conn);
        } catch (RejectedExecutionException e) {
            conn.printOutput(new FtpReply(421, "Connection closing"));
            conn.shutdownSocket();
            _conns.remove(conn);
        }
    }

    /**
     * Handles the load of the FTP Commands.
     * Firstly, it checks if {@code config/commands/ftp/*.conf} exists, if not it halts the daemon.
     * After that it read the file and create a list of the existing commands.
     */
    private void loadCommands() {
        _cmds = GlobalContext.loadCommandConfig(cmdConf);
    }

    /**
     * The HashMap should look like this:
     * Key -> Value
     * "AUTH" -> Properties Object for AUTH
     * "LIST" -> Properties Object for LIST
     *
     * @return Mapping of command (string) to properties
     */
    public HashMap<String, Properties> getCommands() {
        return _cmds;
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Reloading " + cmdConf);
        loadCommands();
        _commandManager.initialize(getCommands(), themeDir);
        for (BaseFtpConnection conn : getConnections()) {
            conn.setCommands(getCommands());
        }
    }
}


