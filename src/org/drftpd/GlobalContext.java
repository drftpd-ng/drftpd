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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Timer;

import javax.net.ssl.SSLContext;

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
import org.drftpd.master.CommitManager;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.SlaveManager;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.master.cron.TimeManager;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserManager;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author mog
 * @version $Id$
 */
/**
 * @author zubov
 * 
 */
public class GlobalContext {
	private static final Logger logger = Logger.getLogger(GlobalContext.class);

	private static GlobalContext _gctx;

	protected ZipscriptConfig _zsConfig;

	private ArrayList<FtpListener> _ftpListeners = new ArrayList<FtpListener>();

	protected JobManager _jm;

	protected SectionManagerInterface _sections;

	private String _shutdownMessage = null;

	protected SlaveManager _slaveManager;

	protected AbstractUserManager _usermanager;

	private Timer _timer = new Timer("GlobalContextTimer");

	protected SlaveSelectionManagerInterface _slaveSelectionManager;

	private SSLContext _sslContext;
	
	private TimeManager _timeManager;

	private static DirectoryHandle root = new DirectoryHandle(
			VirtualFileSystem.separator);

	public void reloadFtpConfig() throws IOException {
		_zsConfig = new ZipscriptConfig(this);
		FtpConfig.reload();
	}
	
	/**
	 * If you're creating a GlobalContext object and it's not part of a TestCase
	 * you're not doing it correctly, GlobalContext is a Singleton
	 *
	 */
	protected GlobalContext() {
	}

	public ConfigInterface getConfig() {
		return FtpConfig.getFtpConfig();
	}

	private void loadJobManager() {
		_jm = new JobManager(this);
	}

	/**
	 * 
	 */
	private void loadSlaveSelectionManager(Properties cfg) {
		try {
			Constructor c = Class
					.forName(
							cfg
									.getProperty("slaveselection",
											"org.drftpd.slaveselection.def.DefaultSlaveSelectionManager"))
					.getConstructor(new Class[] { GlobalContext.class });
			_slaveSelectionManager = (SlaveSelectionManagerInterface) c
					.newInstance(new Object[] { this });
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
		listener.init();
		_ftpListeners.add(listener);
	}

	public synchronized void delFtpListener(FtpListener listener) {
		_ftpListeners.remove(listener);
	}

	public void dispatchFtpEvent(Event event) {
		logger.debug("Dispatching " + event + " to " + getFtpListeners());

		for (FtpListener handler : getFtpListeners()) {
			try {
				handler.actionPerformed(event);
			} catch (RuntimeException e) {
				logger.warn("RuntimeException dispatching event", e);
			}
		}
	}

	public ZipscriptConfig getZsConfig() {
		assert _zsConfig != null;
		return _zsConfig;
	}

	public ConnectionManager getConnectionManager() {
		return ConnectionManager.getConnectionManager();
	}

	public List<FtpListener> getFtpListeners() {
		return new ArrayList<FtpListener>(_ftpListeners);
	}

	/**
	 * JobManager is now loaded as an integral part of the daemon If no Jobs are
	 * sent, it utilizes very little resources
	 */
	public JobManager getJobManager() {
		return _jm;
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

	protected void loadPlugins(Properties cfg) {
		for (int i = 1;; i++) {
			String classname = cfg.getProperty("plugins." + i);

			if (classname == null) {
				break;
			}

			try {
				FtpListener ftpListener = (FtpListener) Class
						.forName(classname.trim()).newInstance();
				addFtpListener(ftpListener);
			} catch (Exception e) {
				throw new FatalException("Error loading plugins", e);
			}
		}
	}

	// depends on having getRoot() working
	private void loadSectionManager(Properties cfg) {
		try {
			Class<?> cl = Class.forName(cfg.getProperty("sectionmanager",
					"org.drftpd.sections.def.SectionManager"));
			Constructor c = cl.getConstructor();
			_sections = (SectionManagerInterface) c.newInstance();
		} catch (Exception e) {
			throw new FatalException(e);
		}
	}

	/**
	 * Depends on root loaded if any slaves connect early.
	 */
	private void loadSlaveManager(Properties cfg) throws SlaveFileException {
		/** register slavemanager * */
		_slaveManager = new SlaveManager(cfg);
	}

	private void listenForSlaves() {
		new Thread(_slaveManager, "Listening for slave connections - "
				+ _slaveManager.toString()).start();
	}

	protected void loadUserManager(Properties cfg) {
		try {
			_usermanager = (AbstractUserManager) Class.forName(
					PropertyHelper.getProperty(cfg, "master.usermanager"))
					.newInstance();
			_usermanager.init();
		} catch (Exception e) {
			throw new FatalException(
					"Cannot create instance of usermanager, check master.usermanager in config file",
					e);
		}
	}

	/**
	 * Doesn't close connections like ConnectionManager.close() does
	 * ConnectionManager.close() calls this method.
	 * 
	 * @see org.drftpd.master.ConnectionManager#shutdown(String)
	 */
	public void shutdown(String message) {
		_shutdownMessage = message;
		dispatchFtpEvent(new MessageEvent("SHUTDOWN", message));
		getConnectionManager().shutdownPrivate(message);
		new Thread(new Shutdown()).start();
	}
	
	class Shutdown implements Runnable {
		public void run() {
			while(GlobalContext.getGlobalContext().getConnectionManager().getConnections().size() > 0) {
				logger.info("Waiting for connections to be shutdown...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			logger.info("Shutdown complete, exiting");
			System.exit(0);
		}
	}

	public Timer getTimer() {
		return _timer;
	}

	public SlaveSelectionManagerInterface getSlaveSelectionManager() {
		return _slaveSelectionManager;
	}
	
	public void addTimeEvent(TimeEventInterface timeEvent) {
		_timeManager.addTimeEvent(timeEvent);
	}

	public PortRange getPortRange() {
		return getConfig().getPortRange();
	}

	public FtpListener getFtpListener(Class clazz)
			throws ObjectNotFoundException {
		for (FtpListener listener : new ArrayList<FtpListener>(
				getFtpListeners())) {

			if (clazz.isInstance(listener)) {
				return listener;
			}
		}

		throw new ObjectNotFoundException();
	}

	public static GlobalContext getGlobalContext() {
		if (_gctx == null) {
			_gctx = new GlobalContext();
		}
		return _gctx;
	}

	public DirectoryHandle getRoot() {
		return root;
	}

	public void init() {
		try {
			reloadFtpConfig();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			logger.debug("", e);
		}
		CommitManager.start();
		_timeManager = new TimeManager();
		loadUserManager(getConfig().getProperties());
		addTimeEvent(getUserManager());

		try {
			loadSlaveManager(getConfig().getProperties());
		} catch (SlaveFileException e) {
			throw new RuntimeException(e);
		}
		listenForSlaves();
		loadJobManager();
		getJobManager().startJobs();
		loadSlaveSelectionManager(getConfig().getProperties());
		loadSectionManager(getConfig().getProperties());
		loadPlugins(getConfig().getProperties());
		
		try {
			_sslContext = SSLGetContext.getSSLContext();
		} catch (IOException e) {
			logger.warn("Couldn't load SSLContext, SSL/TLS disabled - " + e.getMessage());
		} catch (Exception e) {
			logger.warn("Couldn't load SSLContext, SSL/TLS disabled", e);
		}
	}

	/**
	 * Will return null if SSL/TLS is not configured
	 */
	public SSLContext getSSLContext() {
		return _sslContext;
	}

	/*	*//**
			 * @return the Bot instance
			 * @throws ObjectNotFoundException
			 *             if the Bot isnt loaded.
			 */
	/*
	 * public SiteBot getIRCBot() throws ObjectNotFoundException { return
	 * (SiteBot) getFtpListener(SiteBot.class); }
	 */
	// Can enable functions for major plugins after the VFS is integrated
}
