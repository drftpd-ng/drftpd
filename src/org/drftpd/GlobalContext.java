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
package org.drftpd;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.UserManager;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.permission.GlobRMIServerSocketFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.MLSTSerialize;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.sections.SectionManagerInterface;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;


/**
 * @author mog
 * @version $Id: GlobalContext.java,v 1.4 2004/10/03 16:13:55 mog Exp $
 */
public class GlobalContext {
    private static final Logger logger = Logger.getLogger(GlobalContext.class);
    protected ConnectionManager _cm;
    protected FtpConfig _config;
    private ArrayList _ftpListeners = new ArrayList();
    protected JobManager _jm;
    protected LinkedRemoteFileInterface _root;
    protected SectionManagerInterface _sections;
    private String _shutdownMessage = null;
    protected SlaveManagerImpl _slaveManager;
    protected UserManager _usermanager;
    private Timer _timer = new Timer();

    protected GlobalContext() {
    }

    public GlobalContext(Properties cfg, Properties slaveCfg,
        String cfgFileName, String slaveCfgFileName, ConnectionManager cm) {
        _cm = cm;
        _cm.setGlobalContext(this);
        loadUserManager(cfg, cfgFileName);

        try {
            _config = new FtpConfig(cfg, cfgFileName, _cm);
        } catch (Throwable ex) {
            throw new FatalException(ex);
        }

        loadSlaveManager(cfg, cfgFileName);
        loadRSlavesAndRoot();
        loadSectionManager(cfg);
        loadPlugins(cfg);
    }

    /**
     * Calls init(this) on the argument
     */
    public void addFtpListener(FtpListener listener) {
        listener.init(_cm);
        _ftpListeners.add(listener);
    }

    /**
     * @param event
     */
    public void dispatchFtpEvent(Event event) {
        logger.debug("Dispatching " + event + " to " + getFtpListeners());

        for (Iterator iter = getFtpListeners().iterator(); iter.hasNext();) {
            try {
                FtpListener handler = (FtpListener) iter.next();
                handler.actionPerformed(event);
            } catch (RuntimeException e) {
                logger.warn("RuntimeException dispatching event", e);
            }
        }
    }

    public FtpConfig getConfig() {
        if (_config == null) {
            throw new NullPointerException();
        }

        return _config;
    }

    public ConnectionManager getConnectionManager() {
        if (_cm == null) {
            throw new NullPointerException();
        }

        return _cm;
    }

    public List getFtpListeners() {
        return _ftpListeners;
    }

    public JobManager getJobManager() {
        if (_jm == null) {
            throw new IllegalStateException("JobManager not loaded");
        }

        return _jm;
    }

    public LinkedRemoteFileInterface getRoot() {
        if (_root == null) {
            throw new NullPointerException();
        }

        return _root;
    }

    public SectionManagerInterface getSectionManager() {
        if (_sections == null) {
            throw new NullPointerException();
        }

        return _sections;
    }

    public String getShutdownMessage() {
        return _shutdownMessage;
    }

    public SlaveManagerImpl getSlaveManager() {
        if (_slaveManager == null) {
            throw new NullPointerException();
        }

        return _slaveManager;
    }

    public UserManager getUserManager() {
        if (_usermanager == null) {
            throw new NullPointerException();
        }

        return _usermanager;
    }

    public boolean isJobManagerLoaded() {
        return (_jm != null);
    }

    public boolean isShutdown() {
        return _shutdownMessage != null;
    }

    public void loadJobManager() {
        if (_jm != null) {
            return; // already loaded
        }

        try {
            _jm = new JobManager(_cm);
            getSlaveManager().getSlaveSelectionManager().reload();
            _jm.startJobs();
        } catch (IOException e) {
            throw new FatalException("Error loading JobManager", e);
        }
    }

    protected void loadPlugins(Properties cfg) {
        for (int i = 1;; i++) {
            String classname = cfg.getProperty("plugins." + i);

            if (classname == null) {
                break;
            }

            try {
                FtpListener ftpListener = (FtpListener) Class.forName(classname)
                                                             .newInstance();
                addFtpListener(ftpListener);
            } catch (Exception e) {
                throw new FatalException("Error loading plugins", e);
            }
        }
    }

    private void loadRSlavesAndRoot() {
        try {
            List rslaves = _slaveManager.getSlaves();
            _root = MLSTSerialize.loadMLSTFileDatabase(rslaves, _cm);
        } catch (FileNotFoundException e) {
            logger.info("files.mlst not found, creating a new filelist", e);
            _root = new LinkedRemoteFile(getConfig());
        } catch (IOException e) {
            throw new FatalException(e);
        }
    }

    private void loadSectionManager(Properties cfg) {
        try {
            Class cl = Class.forName(cfg.getProperty("sectionmanager",
                        "org.drftpd.sections.def.SectionManager"));
            Constructor c = cl.getConstructor(new Class[] {
                        ConnectionManager.class
                    });
            _sections = (SectionManagerInterface) c.newInstance(new Object[] { _cm });
        } catch (Exception e) {
            throw new FatalException(e);
        }
    }

    private void loadSlaveManager(Properties cfg, String cfgFileName) {
        /** register slavemanager **/
        try {
            String smclass = null;

            try {
                smclass = FtpConfig.getProperty(cfg, "master.slavemanager");
            } catch (Exception ex) {
            }

            if (smclass == null) {
                smclass = "net.sf.drftpd.master.SlaveManagerImpl";
            }

            _slaveManager = (SlaveManagerImpl) Class.forName(smclass)
                                                    .newInstance();
            _slaveManager.loadSlaves();

            GlobRMIServerSocketFactory ssf = new GlobRMIServerSocketFactory(getSlaveManager());
            _slaveManager.init(cfg, ssf, this);
        } catch (Exception e) {
            logger.log(Level.WARN, "Exception instancing SlaveManager", e);
            throw new FatalException(
                "Cannot create instance of slavemanager, check master.slavemanager in " +
                cfgFileName, e);
        }
    }

    protected void loadUserManager(Properties cfg, String cfgFileName) {
        try {
            _usermanager = (UserManager) Class.forName(FtpConfig.getProperty(
                        cfg, "master.usermanager")).newInstance();

            // if the below method is not run, JSXUserManager fails when trying to do a reset() on the user logging in
            _usermanager.init(_cm);
        } catch (Exception e) {
            throw new FatalException(
                "Cannot create instance of usermanager, check master.usermanager in " +
                cfgFileName, e);
        }
    }

    /**
     * Doesn't close connections like ConnectionManager.close() does
     * ConnectionManager.close() calls this method.
     * @see net.sf.drftpd.master.ConnectionManager#shutdown(String)
     */
    public void shutdown(String message) {
        _shutdownMessage = message;
        dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
    }

    public Timer getTimer() {
        return _timer;
    }
}
