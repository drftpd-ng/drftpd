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
package org.drftpd.commands.autonuke;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.event.ReloadEvent;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.vfs.DirectoryHandle;

import java.util.List;
import java.util.Properties;

/**
 * Configuration settings for AutoNuke plugin
 * @author scitz0
 */
public class AutoNukeManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(AutoNukeManager.class);

	private ScanTask _scanTask;
	private NukeTask _nukeTask;

	private CaseInsensitiveHashMap<String, Class<Config>> _configsMap;

	private ConfigChain _configChain;

	public void startPlugin() {
		initConfigs();
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
		logger.debug("Loaded the AutoNuke plugin successfully");
	}

	public void stopPlugin(String reason) {
		cancelTimers();
		AnnotationProcessor.unprocess(this);
		logger.debug("Unloaded the AutoNuke plugin successfully");
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadConf();
    }

	private void cancelTimers() {
		// Cancel timers!
		if (_scanTask != null) _scanTask.cancel();
		if (_nukeTask != null) _nukeTask.cancel();
		GlobalContext.getGlobalContext().getTimer().purge();
	}

	

	private void loadConf() {
		cancelTimers();

        Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("autonuke.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/autonuke.conf not found");
			return;
		}
        // excluded sections
		AutoNukeSettings.getSettings().clearExcludedSections();
        for (String section : cfg.getProperty("exclude.sections","").split(",")) {
            AutoNukeSettings.getSettings().addExcludedSection(
					GlobalContext.getGlobalContext().getSectionManager().getSection(section));
        }

		// excluded directories
		AutoNukeSettings.getSettings().setExcludedDirs(cfg.getProperty("exclude.dirs", ""));

		// excluded sub directories
		AutoNukeSettings.getSettings().setExcludedSubDirs(cfg.getProperty("exclude.subdirs", ""));

        // debug
		AutoNukeSettings.getSettings().setDebug(
				cfg.getProperty("debug", "true").equalsIgnoreCase("true"));

		// Nuke User
		AutoNukeSettings.getSettings().setNukeUser(cfg.getProperty("user", "drftpd"));

		_configChain = new ConfigChain(getConfigsMap());

		_scanTask = new ScanTask();
		_nukeTask = new NukeTask();
        try {
		    GlobalContext.getGlobalContext().getTimer().schedule(_scanTask, 60000L, 60000L);
		    GlobalContext.getGlobalContext().getTimer().schedule(_nukeTask, 90000L, 60000L);
        } catch (IllegalStateException e) {
            logger.error("Unable to start autonuke timer task, reload and try again");
        }
    }

	private void initConfigs() {
		CaseInsensitiveHashMap<String, Class<Config>> configsMap = new CaseInsensitiveHashMap<>();

		try {
			List<PluginObjectContainer<Config>> loadedConfigs =
				CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.commands.autonuke", "Config", "ClassName", false);
			for (PluginObjectContainer<Config> container : loadedConfigs) {
				String configName = container.getPluginExtension().getParameter("ConfigName").valueAsString();
				configsMap.put(configName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.autonuke extension point 'Config'"
					+", possibly the org.drftpd.commands.autonuke"
					+" extension point definition has changed in the plugin.xml",e);
		}

		_configsMap = configsMap;
	}

	@SuppressWarnings("unchecked")
	public CaseInsensitiveHashMap<String, Class<Config>> getConfigsMap() {
		// we don't want to pass this object around allowing it to be modified, make a copy of it.
		return (CaseInsensitiveHashMap<String, Class<Config>>) _configsMap.clone();
	}

	public ConfigChain getConfigChain() {
		return _configChain;
	}

	/**
	 * Method to check the type's status of the directory being scanned.
	 * @param 	dir 		Directory currently being handled
	 * @return				Returns true if dir should be removed, else false
	 */
	public boolean checkConfigs(DirectoryHandle dir) {
		return getConfigChain().checkConfig(dir);
	}

    public static synchronized AutoNukeManager getANC() {
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof AutoNukeManager) {
    			return (AutoNukeManager) plugin;
    		}
    	}

    	throw new RuntimeException("AutoNuke plugin is not loaded.");
    }

}
