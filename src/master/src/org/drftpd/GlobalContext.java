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

import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Timer;

import javax.net.ssl.SSLContext;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventService;
import org.bushe.swing.event.EventServiceExistsException;
import org.bushe.swing.event.EventServiceLocator;
import org.bushe.swing.event.ThreadSafeEventService;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.commandmanager.CommandManagerInterface;
import org.drftpd.config.ConfigManager;
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
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */

public class GlobalContext {

	private static final Logger logger = Logger.getLogger(GlobalContext.class);

	private static GlobalContext _gctx;
	
	private PluginsConfig _pluginsConfig;
	
	private ConfigInterface _config;

	private ArrayList<PluginInterface> _plugins = new ArrayList<PluginInterface>();

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

	private static EventService eventService = new ThreadSafeEventService();

	public void reloadFtpConfig() throws IOException {
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
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint extPoint = manager.getRegistry().getExtensionPoint("master", "SlaveSelection");
		
		String desiredSL = PropertyHelper.getProperty(cfg, "slaveselection");
		for (Extension ext : extPoint.getConnectedExtensions()) {
			if (desiredSL.equals(ext.getDeclaringPluginDescriptor().getId())) {
				try {
					manager.activatePlugin(desiredSL);
					String className = ext.getParameter("class").valueAsString();
					ClassLoader cl = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());
					Class<?> clazz = cl.loadClass(className);
					_slaveSelectionManager = (SlaveSelectionManagerInterface) clazz.newInstance();
				} catch (Throwable t) {
					throw new FatalException("Unable to load the slaveselection plugin, check config.", t);
				}
			}
		}
		
		if (_slaveSelectionManager == null)
			throw new FatalException("Unable to find the SlaveSelection plugin, check the configuration file");
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
		return new ArrayList<PluginInterface>(_plugins);
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
	
	public IndexEngineInterface getIndexingEngine() {
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
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint cmExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"master", "CommandManager");

		/*	Iterate over all extensions that have been connected to the
				CommandManager extension point and return the desired one */

		Properties cfg = GlobalContext.getConfig().getMainProperties();

		Class<?> cmCls = null;

		String desiredCm = PropertyHelper.getProperty(cfg, "commandmanager");

		for (Extension cm : cmExtPoint.getConnectedExtensions()) {
			try {
				if (cm.getDeclaringPluginDescriptor().getId().equals(desiredCm)) {
					// If plugin isn't already activated then activate it
					if (!manager.isPluginActivated(cm.getDeclaringPluginDescriptor())) {
						manager.activatePlugin(cm.getDeclaringPluginDescriptor().getId());
					}
					ClassLoader cmLoader = manager.getPluginClassLoader( 
							cm.getDeclaringPluginDescriptor());
					cmCls = cmLoader.loadClass( 
							cm.getParameter("class").valueAsString());
					CommandManagerInterface commandManager = (CommandManagerInterface) cmCls.newInstance();
					return commandManager;
				}
			}
			catch (Exception e) {
				throw new FatalException(
						"Cannot create instance of commandmanager, check 'commandmanager' in the configuration file",
						e);
			}
		}
		return null;
	}

	private void loadPlugins() {
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"master", "Plugin");

		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
				ClassLoader pluginLoader = manager.getPluginClassLoader( 
						plugin.getDeclaringPluginDescriptor());
				Class<?> pluginCls = pluginLoader.loadClass( 
						plugin.getParameter("class").valueAsString());
				PluginInterface newPlugin = (PluginInterface) pluginCls.newInstance();
				newPlugin.startPlugin();
				_plugins.add(newPlugin);
			}
			catch (Exception e) {
				logger.warn("Error loading plugin " + 
						plugin.getDeclaringPluginDescriptor().getId(),e);
			}
		}
	}
	
	private void loadSectionManager(Properties cfg) {
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint smExtPoint = manager.getRegistry().getExtensionPoint("master", "SectionManager");

		String desiredSm = PropertyHelper.getProperty(cfg, "sectionmanager");

		for (Extension sm : smExtPoint.getConnectedExtensions()) {
			try {
				if (sm.getDeclaringPluginDescriptor().getId().equals(desiredSm)) {
					manager.activatePlugin(desiredSm);
					ClassLoader smLoader = manager.getPluginClassLoader(sm.getDeclaringPluginDescriptor());
					Class<?> smCls = smLoader.loadClass(sm.getParameter("class").valueAsString());
					_sectionManager = (SectionManagerInterface) smCls.newInstance();
				}
			} catch (Exception e) {
				throw new FatalException("Cannot create instance of SectionManager, check 'sectionmanager' in config file", e);
			}
		}
		
		if (_sectionManager == null) {
			logger.fatal("SectionManager plugin not found, check 'sectionmanager' in the configuration file");
		}
	}
	
	private void loadIndexingEngine(Properties cfg) {
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint iExtPoint = manager.getRegistry().getExtensionPoint("master", "IndexingEngine");

		String desiredIe = PropertyHelper.getProperty(cfg, "indexingengine");

		for (Extension ie : iExtPoint.getConnectedExtensions()) {
			try {
				if (ie.getDeclaringPluginDescriptor().getId().equals(desiredIe)) {
					manager.activatePlugin(desiredIe);
					ClassLoader iLoader = manager.getPluginClassLoader(ie.getDeclaringPluginDescriptor());
					Class<?> iCls = iLoader.loadClass(ie.getParameter("class").valueAsString());
					_indexEngine = (IndexEngineInterface) iCls.newInstance();
					_indexEngine.init();
				}
			} catch (Exception e) {
				throw new FatalException("Cannot create instance of IndexingEngine, check 'indexingengine' in config file", e);
			}
		}
		
		if (_indexEngine == null) {
			logger.fatal("IndexingEngine plugin not found, check 'indexingengine' in the configuration file");
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
		//	Find the desired user manager plugin and initialise it
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint umExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"master", "UserManager");
		
		/*	Iterate over all extensions that have been connected to the
			UserManager extension point and init the desired one */

		String desiredUm = PropertyHelper.getProperty(cfg, "usermanager");
		
		for (Extension um : umExtPoint.getConnectedExtensions()) {
			try {
				if (um.getDeclaringPluginDescriptor().getId().equals(desiredUm)) {
					manager.activatePlugin(desiredUm);
					ClassLoader umLoader = manager.getPluginClassLoader( 
							um.getDeclaringPluginDescriptor());
					Class<?> umCls = umLoader.loadClass( 
							um.getParameter("class").valueAsString());
					_usermanager = (AbstractUserManager) umCls.newInstance();
					_usermanager.init();
				}
			}
			catch (Exception e) {
				throw new FatalException(
						"Cannot create instance of usermanager, check 'usermanager' in the configuration file",
						e);
			}
		}
		if (_usermanager == null) {
			logger.fatal("Usermanager plugin not found, check master.usermanager in config file");
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
		EventServiceLocator.getEventBusService().publish(new MessageEvent("SHUTDOWN", message));
		getConnectionManager().shutdownPrivate(message);
		new Thread(new Shutdown()).start();
	}
	
	class Shutdown implements Runnable {
		public void run() {
			while(GlobalContext.getConnectionManager().getConnections().size() > 0) {
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
			logger.warn("Couldn't load SSLContext, SSL/TLS disabled - " + e.getMessage());
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
		HashMap<String,Properties> commandsConfig = new HashMap<String,Properties>();
        LineNumberReader reader = null;
        try {
        	reader = new LineNumberReader(new FileReader(cmdConf));
        	String curLine = null;
        	
        	while (reader.ready()) {
        		curLine = reader.readLine().trim();
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
        			logger.debug("Adding command " + cmdName);

        			commandsConfig.put(cmdName,p);
        		} else {
        			throw new FatalException("Expected line to end with \"{\" at line " + reader.getLineNumber());
        		}
        	}
        	// done reading for new commands, must be finished
        	return commandsConfig;
		} catch (IOException e) {
			throw new FatalException("Error loading "+cmdConf, e);
		} catch (Exception e) {
			if (reader != null) {
				logger.error("Error reading line " + reader.getLineNumber() + " in " + cmdConf);
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
    		curLine = reader.readLine().trim();
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
    	throw new FatalException("Premature end of file, not enough \"}\" characters exist.");
	}

	@EventSubscriber
	public void onUnloadPluginEvent(UnloadPluginEvent event) {
		PluginManager manager = PluginManager.lookup(this);
		String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin) && extension.equals("Plugin")) {
				for (Iterator<PluginInterface> iter = _plugins.iterator(); iter.hasNext();) {
					PluginInterface plugin = iter.next();
					if (manager.getPluginFor(plugin).getDescriptor().getId().equals(event.getPlugin())) {
						plugin.stopPlugin("Plugin being unloaded");
						logger.debug("Unloading plugin "+manager.getPluginFor(plugin).getDescriptor().getId());
						iter.remove();
					}
				}
			}
		}
	}

	@EventSubscriber
	public void onLoadPluginEvent(LoadPluginEvent event) {
		PluginManager manager = PluginManager.lookup(this);
		String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin) && extension.equals("Plugin")) {
				ExtensionPoint pluginExtPoint = 
					manager.getRegistry().getExtensionPoint( 
							"master", "Plugin");
				for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
					if (plugin.getDeclaringPluginDescriptor().getId().equals(event.getPlugin())) {
						try {
							manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
							ClassLoader pluginLoader = manager.getPluginClassLoader( 
									plugin.getDeclaringPluginDescriptor());
							Class<?> pluginCls = pluginLoader.loadClass( 
									plugin.getParameter("class").valueAsString());
							PluginInterface newPlugin = (PluginInterface) pluginCls.newInstance();
							newPlugin.startPlugin();
							_plugins.add(newPlugin);
						}
						catch (Exception e) {
							logger.warn("Error loading plugin " + 
									plugin.getDeclaringPluginDescriptor().getId(),e);
						}
					}
				}
			}
		}
	}
}
