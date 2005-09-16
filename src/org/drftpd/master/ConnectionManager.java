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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.TimerTask;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UserManagement;
import org.drftpd.slave.Slave;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;


/**
 * @version $Id$
 */
public class ConnectionManager {
    private static final Logger logger = Logger.getLogger(ConnectionManager.class.getName());
    private CommandManagerFactory _commandManagerFactory;
    private List<BaseFtpConnection> _conns = Collections.synchronizedList(new ArrayList<BaseFtpConnection>());
    protected GlobalContext _gctx;

    protected ConnectionManager() {
    }

    public ConnectionManager(Properties cfg, Properties slaveCfg,
        String cfgFileName) throws SlaveFileException {
        GlobalContext.initGlobalContext(cfg, cfgFileName, this);
        _gctx = GlobalContext.getGlobalContext();

        if (slaveCfg != null) {
            try {
                new Slave(slaveCfg);
            } catch (IOException ex) { // RemoteException extends IOException
                throw new FatalException(ex);
            }
        }

        _commandManagerFactory = new CommandManagerFactory(this);

        getGlobalContext().addFtpListener(new RaceStatistics());
        getGlobalContext().addFtpListener(new Statistics());

        loadTimer();
        getGlobalContext().getSlaveManager().addShutdownHook();
    }

    public static void main(String[] args) {
        System.out.println(Slave.VERSION + " master server starting.");
        System.out.println("http://drftpd.org/");
        System.out.println("Further logging will be done using (mostly) log4j, check logs/");

        try {
            String cfgFileName;

            if (args.length >= 1) {
                cfgFileName = args[0];
            } else {
                cfgFileName = "drftpd.conf";
            }

            String slaveCfgFileName;

            if (args.length >= 2) {
                slaveCfgFileName = args[1];
            } else {
                slaveCfgFileName = "slave.conf";
            }

            /** load master config **/
            Properties cfg = new Properties();
            cfg.load(new FileInputStream(cfgFileName));

            /** load slave config **/
            Properties slaveCfg; //used as a flag for if localslave=true

            if (cfg.getProperty("master.localslave", "false").equalsIgnoreCase("true")) {
                slaveCfg = new Properties();
                slaveCfg.load(new FileInputStream(slaveCfgFileName));
            } else {
                slaveCfg = null;
            }

            logger.info("Starting ConnectionManager");

            ConnectionManager mgr = new ConnectionManager(cfg, slaveCfg,
                    cfgFileName);

            /** listen for connections **/
            String bindip = null;
            ServerSocket server = null;
            boolean useIP;
            
            try {
            	bindip = PropertyHelper.getProperty(cfg, "master.ip");
                if (bindip.equals(""))
                	useIP = false;
                else
                	useIP = true;
            } catch (NullPointerException e) {
            	useIP = false;
            }
            
        	if (useIP) {
        		server = new ServerSocket();
        		server.bind(new InetSocketAddress(bindip, Integer.parseInt(PropertyHelper.getProperty(cfg, "master.port"))));
            	logger.info("Listening on " + server.getInetAddress() + ":" + server.getLocalPort());
        	} else {
            	server = new ServerSocket(Integer.parseInt(
                    PropertyHelper.getProperty(cfg, "master.port")));
            	logger.info("Listening on port " + server.getLocalPort());	
            } 
            
            while (true) {
                mgr.start(server.accept());
            }

            //catches subclasses of Error and Exception
        } catch (Throwable th) {
            logger.error("", th);
            System.exit(0);

            return;
        }
    }

    public Reply canLogin(BaseFtpConnection baseconn, User user) {
        int count = getGlobalContext().getConfig().getMaxUsersTotal();

        //Math.max if the integer wraps
        if (user.isExempt()) {
            count = Math.max(count,
                    count + getGlobalContext().getConfig().getMaxUsersExempt());
        }

        // not >= because baseconn is already included
        if (_conns.size() > count) {
            return new Reply(550, "The site is full, try again later.");
        }
        
        int userCount = 0;
        int ipCount = 0;

        synchronized (_conns) {
            for (Iterator iter = _conns.iterator(); iter.hasNext();) {
                BaseFtpConnection tempConnection = (BaseFtpConnection) iter.next();

                try {
                    User tempUser = tempConnection.getUser();

                    if (tempUser.getName().equals(user.getName())) {
                        userCount++;

                        if (tempConnection.getClientAddress().equals(baseconn.getClientAddress())) {
                            ipCount++;
                        }
                    }
                } catch (NoSuchUserException ex) {
                    // do nothing, we found our current connection, baseconn = tempConnection
                }
            }
        }

        if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) > 0) {
			if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINS) <= userCount) {
				return new Reply(530, "Sorry, your account is restricted to "
						+ user.getKeyedMap().getObjectInt(
								UserManagement.MAXLOGINS)
						+ " simultaneous logins.");
			}
		}
		if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP) > 0) {
			if (user.getKeyedMap().getObjectInt(UserManagement.MAXLOGINSIP) <= ipCount) {
				return new Reply(530,
						"Sorry, your maximum number of connections from this IP ("
								+ user.getKeyedMap().getObjectInt(
										UserManagement.MAXLOGINSIP)
								+ ") has been reached.");
			}
		}

		if (user.getKeyedMap().getObjectDate(UserManagement.BAN_TIME).getTime() > System.currentTimeMillis()) {
		    return new Reply(530,"Sorry you are banned until " +  
		            user.getKeyedMap().getObjectDate(UserManagement.BAN_TIME) + "! (" + 
		            user.getKeyedMap().getObjectString(UserManagement.BAN_REASON) + ")");
		}

		if (!baseconn.isSecure() &&
                getGlobalContext().getConfig().checkPermission("userrejectinsecure", user)) {
            return new Reply(530, "USE SECURE CONNECTION");
        } else if (baseconn.isSecure() &&
                getGlobalContext().getConfig().checkPermission("userrejectsecure", user)) {
            return new Reply(530, "USE INSECURE CONNECTION");
        }

        return null; // everything passed
    }

    public void dispatchFtpEvent(Event event) {
        getGlobalContext().dispatchFtpEvent(event);
    }

    public CommandManagerFactory getCommandManagerFactory() {
        return _commandManagerFactory;
    }

    /**
     * returns a <code>Collection</code> of current connections
     */
    public List<BaseFtpConnection> getConnections() {
        return _conns;
    }

    public GlobalContext getGlobalContext() {
        if (_gctx == null) {
            throw new NullPointerException();
        }
        return _gctx;
    }

    private void loadTimer() {
/*        TimerTask timerLogoutIdle = new TimerTask() {
                public void run() {
                	try {
                		timerLogoutIdle();
                	} catch (Throwable t) {
                		logger.error("Error in timerLogoutIdle TimerTask", t);
                	}
                }
            };

        //run every 10 seconds
        getGlobalContext().getTimer().schedule(timerLogoutIdle, 10 * 1000, 10 * 1000);*/

        TimerTask timerSave = new TimerTask() {
                public void run() {
                	try {
                		getGlobalContext().getSlaveManager().saveFilelist();
                		
                		try {
                			getGlobalContext().getUserManager().saveAll();
                		} catch (UserFileException e) {
                			logger.log(Level.FATAL, "Error saving all users", e);
                		}
                	} catch (Throwable t) {
                		logger.error("Error in timerSave TimerTask", t);
                	}
                }
            };
        TimerTask timerGarbageCollect = new TimerTask() {
			public void run() {
				logger.debug("Memory free before GC :"
						+ Bytes.formatBytes(Runtime.getRuntime().freeMemory())
						+ "/"
						+ Bytes.formatBytes(Runtime.getRuntime().totalMemory()));
				System.gc();
				logger.debug("Memory free after GC :"
						+ Bytes.formatBytes(Runtime.getRuntime().freeMemory())
						+ "/"
						+ Bytes.formatBytes(Runtime.getRuntime().totalMemory()));
			}
		};

		// run every hour
		getGlobalContext().getTimer().schedule(timerSave, 60 * 60 * 1000, 60 * 60 * 1000);
		// run every minute
		getGlobalContext().getTimer().schedule(timerGarbageCollect, 60 * 1000, 60 * 1000);
    }

    public void remove(BaseFtpConnection conn) {
        if (!_conns.remove(conn)) {
            throw new RuntimeException("connections.remove() returned false.");
        }

        if (getGlobalContext().isShutdown() && _conns.isEmpty()) {
            //			_slaveManager.saveFilelist();
            //			try {
            //				getUserManager().saveAll();
            //			} catch (UserFileException e) {
            //				logger.log(Level.WARN, "Failed to save all userfiles", e);
            //			}
            logger.info("Shutdown complete, exiting");
            System.exit(0);
        }
    }

    public void shutdownPrivate(String message) {
        for(BaseFtpConnection conn : new ArrayList<BaseFtpConnection>(getConnections())) {
        	conn.stop(message);
        }
    }

    public void start(Socket sock) throws IOException {
        if (getGlobalContext().isShutdown()) {
            new PrintWriter(sock.getOutputStream()).println("421 " +
                getGlobalContext().getShutdownMessage());
            sock.close();
            return;
        }

/*      
 * 		Reserved for Implicit SSL, TODO
 * 		if(sock instanceof SSLSocket)
        {
        	SSLSocket sslsock = (SSLSocket) sock;
        	sslsock.setUseClientMode(false);
        	sslsock.startHandshake();
        	sock = sslsock;
        }*/

        BaseFtpConnection conn = new BaseFtpConnection(getGlobalContext(), sock);
        
        
        _conns.add(conn);
        conn.start();
    }

/*    public void timerLogoutIdle() {
        long currTime = System.currentTimeMillis();
        ArrayList<BaseFtpConnection> conns = new ArrayList<BaseFtpConnection>(_conns);

        for (Iterator i = conns.iterator(); i.hasNext();) {
            BaseFtpConnection conn = (BaseFtpConnection) i.next();

            int idle = (int) ((currTime - conn.getLastActive()) / 1000);
            int maxIdleTime;

            try {
                maxIdleTime = conn.getUser().getIdleTime();

                if (maxIdleTime == 0) {
                    maxIdleTime = idleTimeout;
                }
            } catch (NoSuchUserException e) {
                maxIdleTime = idleTimeout;
            }

            if (!conn.isExecuting() && (idle >= maxIdleTime)) {
                // idle time expired, logout user.
                conn.stop("Idle time expired: " + maxIdleTime + "s");
            }
        }
    }*/

    public void setGlobalContext(GlobalContext gctx) {
        _gctx = gctx;
    }
}
