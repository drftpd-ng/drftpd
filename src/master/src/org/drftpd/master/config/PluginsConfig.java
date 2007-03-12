package org.drftpd.master.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

public class PluginsConfig {
	private final static String pluginsConfPath = "conf/plugins/";

	private static final Logger logger = Logger.getLogger(PluginsConfig.class);
			
	private HashMap<String, Properties> _propertiesMap = new HashMap<String, Properties>();

	public PluginsConfig() {
		loadConfigs();
		dumpHashMap();
	}
	
	/**
	 * Prints the load configurations.
	 * @param args
	 */
	public void dumpHashMap() {
		logger.debug("Dumping Map Information.");
		for (Entry<String, Properties> entry : getPropertiesMap().entrySet()) {
			logger.debug("Configuration File: "+ entry.getKey());
			logger.debug("Listing properties.");
			for (Entry e : entry.getValue().entrySet()) {
				String key = (String) e.getKey();
				String value = (String) e.getValue();
				logger.debug(key+"="+value);
			}
		}
	}

	public void loadConfigs() {
		File dir = new File(pluginsConfPath);
		if (!dir.isDirectory())
			throw new RuntimeException(pluginsConfPath + " is not a directory");
		
		for (File file : dir.listFiles()) {
			if (file.isFile()) {
				loadConf(file);
			} // else, ignore it.
		}
	}

	public HashMap<String, Properties> getPropertiesMap() {
		return _propertiesMap;
	}

	private void loadConf(File file) {
		FileInputStream fis = null;

		try {			
			fis = new FileInputStream(file);

			Properties cfg = new Properties();
			cfg.load(fis);

			getPropertiesMap().put(file.getName(), cfg);
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
	 * @param pluginName
	 * @return The Properties table for the plugin.
	 */
	public Properties getPropertiesForPlugin(String pluginName) {
		if (!pluginName.endsWith(".conf"))
			pluginName.concat(".conf");

		return getPropertiesMap().get(pluginName);
	}

}