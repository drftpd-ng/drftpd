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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.config.ZipscriptConfig;
import net.sf.drftpd.mirroring.JobManager;
import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.SlaveManager;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.remotefile.MLSTSerialize;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserManager;


/**
 * @author mog
 * @version $Id$
 */
public class GlobalContext {
    private static final Logger logger = Logger.getLogger(GlobalContext.class);
    private static GlobalContext _gctx = null;
    protected ConnectionManager _cm;
    protected ConfigInterface _config;
    protected ZipscriptConfig _zsConfig;
    private ArrayList<FtpListener> _ftpListeners = new ArrayList<FtpListener>();
    protected JobManager _jm;
    protected LinkedRemoteFileInterface _root;
    protected SectionManagerInterface _sections;
    private String _shutdownMessage = null;
    protected SlaveManager _slaveManager;
    protected AbstractUserManager _usermanager;
    private Timer _timer = new Timer("GlobalContextTimer");
    protected SlaveSelectionManagerInterface _slaveSelectionManager;
	private String _cfgFileName;

    public void reloadFtpConfig() throws IOException {
    	_config = new FtpConfig(_cfgFileName);
    	_zsConfig = new ZipscriptConfig(this);
    }
    
    public static GlobalContext getGlobalContext() {
		if (_gctx == null) {
    		throw new RuntimeException("GlobalContext was not initialized");
    	}
    	return _gctx;
    }
    
    public static void initGlobalContext(Properties cfg, String cfgFileName, ConnectionManager cm) throws SlaveFileException {
    	if (_gctx != null) {
    		throw new RuntimeException("GlobalContext is already initialized");
    	}
    	_gctx = new GlobalContext(cfg, cfgFileName, cm);
    }
    /**
     * Only used for junit tests
     */
    public GlobalContext() {
    }

    private GlobalContext(Properties cfg, String cfgFileName,
        ConnectionManager cm) throws SlaveFileException {
    	_cfgFileName = cfgFileName;
        _cm = cm;
        _cm.setGlobalContext(this);
        loadUserManager(cfg, cfgFileName);

        try {
            _config = new FtpConfig(cfg, cfgFileName);
        } catch (Throwable ex) {
            throw new FatalException(ex);
        }

        try {
            _zsConfig = new ZipscriptConfig(this);
        } catch (Throwable ex) {
            throw new FatalException(ex);
        }

        loadSlaveManager(cfg);
        loadRSlavesAndRoot();
        listenForSlaves();
        loadSlaveSelectionManager(cfg);
        loadSectionManager(cfg);
        loadPlugins(cfg);
    }

    /**
         *
         */
    private void loadSlaveSelectionManager(Properties cfg) {
        try {
            Constructor c = Class.forName(cfg.getProperty("slaveselection",
                        "org.drftpd.slaveselection.def.DefaultSlaveSelectionManager"))
                                 .getConstructor(new Class[] { GlobalContext.class });
            _slaveSelectionManager = (SlaveSelectionManagerInterface) c.newInstance(new Object[] {
                        this
                    });
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }

            throw new FatalException(e);
        }
    }

    /**
    * Calls init(this) on the argument
    */
    public synchronized void addFtpListener(FtpListener listener) {
        _ftpListeners.add(listener);
    }
    
    public synchronized void delFtpListener(FtpListener listener) {
    	_ftpListeners.remove(listener);
    }

    public void dispatchFtpEvent(Event event) {
		logger.debug("Dispatching " + event + " to " + getFtpListeners());

		for (FtpListener handler : new ArrayList<FtpListener>(getFtpListeners())) {
			try {
				handler.actionPerformed(event);
			} catch (RuntimeException e) {
				logger.warn("RuntimeException dispatching event", e);
			}
		}
	}

    public ConfigInterface getConfig() {
        assert _config != null;
        return _config;
    }

    public ZipscriptConfig getZsConfig() {
        assert _zsConfig != null;
        return _zsConfig;
    }
    
    public ConnectionManager getConnectionManager() {
    	assert _cm != null;
        return _cm;
    }

    public List<FtpListener> getFtpListeners() {
		return new ArrayList<FtpListener>(_ftpListeners);
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

    public SlaveManager getSlaveManager() {
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
            _jm = new JobManager(this);
            getSlaveSelectionManager().reload();
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

    /**
     * Depends on slavemanager being loaded.
     *
     */
    private void loadRSlavesAndRoot() {
        try {
            List rslaves = _slaveManager.getSlaves();
            logger.info("Loading files.mlst");
            _root = MLSTSerialize.loadMLSTFileDatabase(rslaves, _cm);
        } catch (FileNotFoundException e) {
            logger.info("files.mlst not found, creating a new filelist");
            _root = new LinkedRemoteFile(getConfig());
        } catch (IOException e) {
            throw new FatalException(e);
        }
    }

    // depends on having getRoot() working
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

    /**
     * Depends on root loaded if any slaves connect early.
     */
    private void loadSlaveManager(Properties cfg) throws SlaveFileException {
        /** register slavemanager **/
        _slaveManager = new SlaveManager(cfg, this);
    }

    private void listenForSlaves() {
    	new Thread(_slaveManager, "Listening for slave connections - " + _slaveManager.toString()).start();
    }

    protected void loadUserManager(Properties cfg, String cfgFileName) {
        try {
            _usermanager = (AbstractUserManager) Class.forName(PropertyHelper.getProperty(
                        cfg, "master.usermanager")).newInstance();

            // if the below method is not run, JSXUserManager fails when trying to do a reset() on the user logging in
            _usermanager.init(this);
        } catch (Exception e) {
            throw new FatalException(
                "Cannot create instance of usermanager, check master.usermanager in " +
                cfgFileName, e);
        }
    }

    /**
     * Doesn't close connections like ConnectionManager.close() does
     * ConnectionManager.close() calls this method.
     * @see org.drftpd.master.ConnectionManager#shutdown(String)
     */
    public void shutdown(String message) {
        _shutdownMessage = message;
        dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
        getConnectionManager().shutdownPrivate(message);
    }

    public Timer getTimer() {
        return _timer;
    }

    public SlaveSelectionManagerInterface getSlaveSelectionManager() {
        return _slaveSelectionManager;
    }
    public PortRange getPortRange() {
        return getConfig().getPortRange();
    }

	public FtpListener getFtpListener(Class clazz) throws ObjectNotFoundException {
        for (FtpListener listener : new ArrayList<FtpListener>(getFtpListeners())) {

            if (clazz.isInstance(listener)) {
                return listener;
            }
        }

        throw new ObjectNotFoundException();
	}
}
