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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.EventServiceExistsException;
import org.bushe.swing.event.EventServiceLocator;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.config.ConfigManager;
import org.drftpd.event.AsyncThreadSafeEventService;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.MessageEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.FatalException;
import org.drftpd.exceptions.SlaveFileException;
import org.drftpd.master.CommitManager;
import org.drftpd.master.ConnectionManager;
import org.drftpd.master.SlaveManager;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.master.config.PluginsConfig;
import org.drftpd.master.cron.TimeEventInterface;
import org.drftpd.master.cron.TimeManager;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserManager;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.tanukisoftware.wrapper.WrapperManager;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */

public class GlobalContext {

	private static final Logger logger = LogManager.getLogger(GlobalContext.class);

	private static GlobalContext _gctx;

	private PluginsConfig _pluginsConfig;

	private ConfigInterface _config;

	private ArrayList<PluginInterface> _plugins = new ArrayList<>();

	protected SectionManagerInterface _sectionManager;

	private String _shutdownMessage = null;

	protected SlaveManager _slaveManager;

	protected AbstractUserManager _usermanager;

	private Timer _timer = new Timer("GlobalContextTimer");

	protected SlaveSelectionManagerInterface _slaveSelectionManager;

	private SSLContext _sslContext;

	private TimeManager _timeManager;

	private IndexEngineInterface _indexEngine;

	private static DirectoryHandle root = new DirectoryHandle(VirtualFileSystem.separator);

	private static AsyncThreadSafeEventService eventService = new AsyncThreadSafeEventService();

	public static final String VERSION = "DrFTPD 3.4.3";

	public void reloadFtpConfig() {
		_config.reload();
	}

	/**
	 * If you're creating a GlobalContext object and it's not part of a TestCase
	 * you're not doing it correctly, GlobalContext is a Singleton
	 *
	 */
	protected GlobalContext() {
	}

	private void loadSlaveSelectionManager(Properties cfg) {
		String desiredSL = PropertyHelper.getProperty(cfg, "slaveselection");
		try {
			_slaveSelectionManager = CommonPluginUtils.getSinglePluginObject(this, "master", "SlaveSelection", "Class", desiredSL);
		} catch (Exception e) {
			throw new FatalException("Unable to load the slaveselection plugin, check config.", e);
		}
	}

	public PluginsConfig getPluginsConfig() {
		return _pluginsConfig;
	}

	public void loadPluginsConfig() {
		_pluginsConfig = new PluginsConfig();
	}

	public static ConnectionManager getConnectionManager() {
		return ConnectionManager.getConnectionManager();
	}

	public static ConfigInterface getConfig() {
		return getGlobalContext()._config;
	}

	public List<PluginInterface> getPlugins() {
		return new ArrayList<>(_plugins);
	}

	public SectionManagerInterface getSectionManager() {
		if (_sectionManager == null) {
			throw new NullPointerException();
		}

		return _sectionManager;
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

	public IndexEngineInterface getIndexEngine() {
		return _indexEngine;
	}

	public UserManager getUserManager() {
		if (_usermanager == null) {
			throw new NullPointerException();
		}

		return _usermanager;
	}

	public boolean isShutdown() {
		return _shutdownMessage != null;
	}

	public CommandManagerInterface getCommandManager() {
		Properties cfg = GlobalContext.getConfig().getMainProperties();

		String desiredCm = PropertyHelper.getProperty(cfg, "commandmanager");
		try {
			return CommonPluginUtils.getSinglePluginObject(this, "master", "CommandManager", "Class", desiredCm);
		} catch (Exception e) {
			throw new FatalException(
					"Cannot create instance of commandmanager, check 'commandmanager' in the configuration file",
					e);
		}
	}

	private void loadPlugins() {
		try {
			List<PluginInterface> loadedPlugins = CommonPluginUtils.getPluginObjects(this, "master", "Plugin", "Class");
			for (PluginInterface newPlugin : loadedPlugins) {
				newPlugin.startPlugin();
				_plugins.add(newPlugin);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for master extension point 'Plugin', possibly the master extension " +
					"point definition has changed in the plugin.xml",e);
		}
	}

	private void loadSectionManager(Properties cfg) {
		String desiredSm = PropertyHelper.getProperty(cfg, "sectionmanager");
		try {
			_sectionManager = CommonPluginUtils.getSinglePluginObject(this, "master", "SectionManager", "Class", desiredSm);
		} catch (Exception e) {
			throw new FatalException("Cannot create instance of SectionManager, check 'sectionmanager' in config file", e);
		}
	}

	private void loadIndexingEngine(Properties cfg) {
		String desiredIe = PropertyHelper.getProperty(cfg, "indexingengine");
		try {
			_indexEngine = CommonPluginUtils.getSinglePluginObject(this, "master", "IndexingEngine", "Class", desiredIe);
			_indexEngine.init();
		} catch (Exception e) {
			throw new FatalException("Cannot create instance of IndexingEngine, check 'indexingengine' in config file", e);
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
		String desiredUm = PropertyHelper.getProperty(cfg, "usermanager");
		try {
			_usermanager = CommonPluginUtils.getSinglePluginObject(this, "master", "UserManager", "Class", desiredUm);
			_usermanager.init();
		} catch (Exception e) {
			throw new FatalException(
					"Cannot create instance of usermanager, check 'usermanager' in the configuration file",
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
		CommitManager.getCommitManager().enableQueueDrain();
		getEventService().publish(new MessageEvent("SHUTDOWN", message));
		getConnectionManager().shutdownPrivate(message);
		new Thread(new Shutdown()).start();
	}

	static class Shutdown implements Runnable {

		public void run() {
			Thread.currentThread().setName("Shutdown Thread");
			while(GlobalContext.getConnectionManager().getConnections().size() > 0) {
				logger.info("Waiting for connections to be shutdown...");
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			while (GlobalContext.getEventService().getQueueSize() > 0) {
                logger.info("Waiting for queued events to be processed - {} remaining", GlobalContext.getEventService().getQueueSize());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			while (CommitManager.getCommitManager().getQueueSize() > 0) {
                logger.info("Waiting for queued commits to be drained - {} remaining", CommitManager.getCommitManager().getQueueSize());
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
			logger.info("Shutdown complete, exiting");
			WrapperManager.stop(0);
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

	public void removeTimeEvent(TimeEventInterface timeEvent) {
		_timeManager.removeTimeEvent(timeEvent);
	}

	public PortRange getPortRange() {
		return getConfig().getPortRange();
	}

	public static GlobalContext getGlobalContext() {
		if (_gctx == null) {
			_gctx = new GlobalContext();
			try {
				EventServiceLocator.setEventService(EventServiceLocator.SERVICE_NAME_EVENT_BUS, eventService);
			} catch (EventServiceExistsException e) {
				logger.error("Error setting event service, likely something using the event bus before GlobalContext is instantiated",e);
			}
		}
		return _gctx;
	}

	public DirectoryHandle getRoot() {
		return root;
	}

	public void init() {
		_config = new ConfigManager();
		_config.reload();

		CommitManager.getCommitManager().start();
		_timeManager = new TimeManager();
		loadPluginsConfig();
		loadUserManager(getConfig().getMainProperties());
		addTimeEvent(getUserManager());

		try {
			_sslContext = SSLGetContext.getSSLContext();
		} catch (IOException e) {
            logger.warn("Couldn't load SSLContext, SSL/TLS disabled - {}", e.getMessage());
		} catch (Exception e) {
			logger.warn("Couldn't load SSLContext, SSL/TLS disabled", e);
		}

		try {
			loadSlaveManager(getConfig().getMainProperties());
		} catch (SlaveFileException e) {
			throw new RuntimeException(e);
		}
		listenForSlaves();
		loadSlaveSelectionManager(getConfig().getMainProperties());
		loadSectionManager(getConfig().getMainProperties());
		loadIndexingEngine(getConfig().getMainProperties());
		loadPlugins();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}


	/**
	 * Will return null if SSL/TLS is not configured
	 */
	public SSLContext getSSLContext() {
		return _sslContext;
	}

	public static HashMap<String, Properties> loadCommandConfig(String cmdConf) {
		HashMap<String,Properties> commandsConfig = new HashMap<>();
		LineNumberReader reader = null;
		try {
			reader = new LineNumberReader(new FileReader(cmdConf));
			String curLine = null;

			while (reader.ready()) {
				curLine = reader.readLine();
				if (curLine != null) {
					curLine = curLine.trim();
					if (curLine.startsWith("#") || curLine.equals("") || curLine.startsWith("skip")) {
						// comment or blank line, ignore
						continue;
					}
					if (curLine.endsWith("{")) {
						// internal loop
						String cmdName = curLine.substring(0, curLine.lastIndexOf("{")-1).toLowerCase();
						if (commandsConfig.containsKey(cmdName)) {
							throw new FatalException(cmdName + " is already mapped on line " + reader.getLineNumber());
						}
						Properties p = getPropertiesUntilClosed(reader);
                        logger.debug("Adding command {}", cmdName);

						commandsConfig.put(cmdName,p);
					} else {
						throw new FatalException("Expected line to end with \"{\" at line " + reader.getLineNumber());
					}
				}
			}
			// done reading for new commands, must be finished
			return commandsConfig;
		} catch (IOException e) {
			throw new FatalException("Error loading "+cmdConf, e);
		} catch (Exception e) {
			if (reader != null) {
                logger.error("Error reading line {} in {}", reader.getLineNumber(), cmdConf);
			}
			throw new FatalException(e);
		} finally {
			if(reader != null) {
				try {
					reader.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static Properties getPropertiesUntilClosed(LineNumberReader reader) throws IOException {
		Properties p = new Properties();
		String curLine = null;
		while (reader.ready()) {
			curLine = reader.readLine();
			if (curLine != null) {
				curLine = curLine.trim();
				if (curLine.startsWith("#") || curLine.equals("")) {
					// comment or blank line, ignore
					continue;
				}
				if (curLine.equals("}")) {
					// end of this block
					return p;
				}
				// internal loop
				int spaceIndex = curLine.indexOf(" ");
				if (spaceIndex == -1) {
					throw new FatalException("Line " + reader.getLineNumber() + " is not formatted properly");
				}
				String propName = curLine.substring(0, spaceIndex);
				String value = curLine.substring(spaceIndex).trim();
				String concatenate = p.getProperty(propName);
				if (concatenate == null) {
					p.put(propName, value);    			
				} else {
					p.put(propName, concatenate + "\n" + value);
				}
			}
		}
		throw new FatalException("Premature end of file, not enough \"}\" characters exist.");
	}

	public static AsyncThreadSafeEventService getEventService() {
		return eventService;
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		Set<PluginInterface> unloadedExtensions = MasterPluginUtils.getUnloadedExtensionObjects(this, "Plugin", event, _plugins);
		if (!unloadedExtensions.isEmpty()) {
			ArrayList<PluginInterface> clonedPlugins = new ArrayList<>(_plugins);
			boolean pluginRemoved = false;
			for (Iterator<PluginInterface> iter = clonedPlugins.iterator(); iter.hasNext();) {
				PluginInterface plugin = iter.next();
				if (unloadedExtensions.contains(plugin)) {
					plugin.stopPlugin("Plugin being unloaded");
                    logger.debug("Unloading plugin {}", CommonPluginUtils.getPluginIdForObject(plugin));
					iter.remove();
					pluginRemoved = true;
				}
			}
			if (pluginRemoved) {
				_plugins = clonedPlugins;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<PluginInterface> loadedExtensions = MasterPluginUtils.getLoadedExtensionObjects(this, "master", "Plugin", "Class", event);
			if (!loadedExtensions.isEmpty()) {
				ArrayList<PluginInterface> clonedPlugins = new ArrayList<>(_plugins);
				for (PluginInterface newExtension : loadedExtensions) {
					newExtension.startPlugin();
                    logger.debug("Loading plugin {}", CommonPluginUtils.getPluginIdForObject(newExtension));
					clonedPlugins.add(newExtension);
				}
				_plugins = clonedPlugins;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins from a loadplugin event for master extension point 'Plugin', possibly the "
					+"master extension point definition has changed in the plugin.xml",e);
		}
	}
}
