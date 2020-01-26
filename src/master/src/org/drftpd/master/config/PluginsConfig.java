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

package org.drftpd.master.config;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * Plugin Configuraiton loader.<br>
 * Every file that is in 'conf/plugins' is subject to be loaded.<br>
 * From now on you don't need to create your own configuration reader for your plugin,
 * simply use PluginsConfig.getPropertiesForPlugin("plugin.name").
 * @author fr0w
 * @version $Id$
 */
public class PluginsConfig {
	private static final String pluginsConfPath = "conf/plugins/";
	private static final File pluginsConfFile = new File(pluginsConfPath);

	private static final Logger logger = LogManager.getLogger(PluginsConfig.class);
			
	private HashMap<String, Properties> _propertiesMap = new HashMap<>();

	public PluginsConfig() {
		searchForConfigurations(pluginsConfFile);
		dumpHashMap();
	}
	
	/**
	 * Prints the loaded configurations.
	 * @param args
	 */
	public void dumpHashMap() {
		logger.debug("Dumping Map Information.");
		for (Entry<String, Properties> entry : getPropertiesMap().entrySet()) {
            logger.debug("--> Configuration File: {}", entry.getKey());
			logger.debug("Listing properties.");
			for (Entry<Object,Object> e : entry.getValue().entrySet()) {
				String key = (String) e.getKey();
				String value = (String) e.getValue();
                logger.debug("{}={}", key, value);
			}
		}
	}

	/**
	 * Makes a list of all files in 'conf/plugins' and start reading all files that has the '.conf' extension.
	 */
	protected void searchForConfigurations(File dir) {
		if (!dir.isDirectory())
			throw new RuntimeException(pluginsConfPath + " is not a directory");
		
		for (File file : dir.listFiles()) {
			// TODO: by doing startsWith(".") i'm preveting people using a config file like '.hidden.conf' is that a problem?
			if (file.getName().startsWith(".")) {
            } else if (file.isFile() && file.getName().endsWith(".conf")) {
				loadConf(file);
			} else if (file.isDirectory()){
				searchForConfigurations(file);
			}
		}
	}
	
	/**
	 * @return all configurations.
	 */
	private HashMap<String, Properties> getPropertiesMap() {
		return _propertiesMap;
	}

	/**
	 * Load a java.util.Propreties-like file.
	 * @param file
	 */
	private void loadConf(File file) {
		FileInputStream fis = null;

		try {			
			fis = new FileInputStream(file);

			Properties cfg = new Properties();
			cfg.load(fis);

			String skipTag = cfg.getProperty("skip");
			if (skipTag != null && skipTag.equals("true")) {
				return; // we were told to skip the file.				
			}
			
			String key = file.getPath().substring("conf/plugins/".length()).replace("\\", "/");
			getPropertiesMap().put(key, cfg);
		} catch (FileNotFoundException e) {
			logger.error("Weird the file was just there, how come it's gone?", e);
		} catch (IOException e) {
			logger.error("An error ocurred while loading Properties");
		} finally {
			if (fis != null) {
				try {
					fis.close();
				} catch (IOException e) {
				}
			}
		}
	}

	/**
	 * Accepts either 'pluginName' or 'pluginName.conf' as parameter.
	 * Ex: zipscript and zipscript.conf are valid parameters.
	 * 
	 * If the Properties object is not found on the map, it will return
	 * an empty Properties object.
	 * 
	 * @param pluginName
	 * @return The Properties table for the plugin.
	 */
	public Properties getPropertiesForPlugin(String pluginName) {
		if (!pluginName.endsWith(".conf"))
			pluginName = pluginName + ".conf";

		Properties cfg = getPropertiesMap().get(pluginName);
		
		if (cfg == null) {
			cfg = new Properties();
            logger.error("'{}' configuration file was not found. Returning an empty Properties object.", pluginName);
		}
		
		return cfg;
	}

}