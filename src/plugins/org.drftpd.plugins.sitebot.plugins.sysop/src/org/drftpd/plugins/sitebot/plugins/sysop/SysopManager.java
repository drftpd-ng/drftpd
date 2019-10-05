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
package org.drftpd.plugins.sitebot.plugins.sysop;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.event.ReloadEvent;

import java.util.HashMap;
import java.util.Properties;

/**
 * SysopManager is a class that handles the sysop configuration.
 * @author scitz0
 */
public class SysopManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(SysopManager.class);

	public static HashMap<String,Integer> CONFIG;
    
	public void startPlugin() {
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
		logger.debug("Loaded the Sysop plugin successfully");
	}

	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
		logger.debug("Unloaded the Sysop plugin successfully");
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
	
	private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("sysop.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/sysop.conf not found");
			return;
		}

		if (CONFIG == null) {
			CONFIG = new HashMap<>();
		} else {
			CONFIG.clear();
		}

		CONFIG.put("PASS", Integer.parseInt(cfg.getProperty("PASS","0")));
		CONFIG.put("IDNT", Integer.parseInt(cfg.getProperty("IDNT","0")));
		CONFIG.put("USER", Integer.parseInt(cfg.getProperty("USER","0")));

		CONFIG.put("ADDIP", Integer.parseInt(cfg.getProperty("ADDIP","0")));
		CONFIG.put("ADDUSER", Integer.parseInt(cfg.getProperty("ADDUSER","0")));
		CONFIG.put("BAN", Integer.parseInt(cfg.getProperty("BAN","0")));
		CONFIG.put("CHANGE", Integer.parseInt(cfg.getProperty("CHANGE","0")));
		CONFIG.put("CHGRP", Integer.parseInt(cfg.getProperty("CHGRP","0")));
		CONFIG.put("CHPASS", Integer.parseInt(cfg.getProperty("CHPASS","0")));
		CONFIG.put("DELIP", Integer.parseInt(cfg.getProperty("DELIP","0")));
		CONFIG.put("DELUSER", Integer.parseInt(cfg.getProperty("DELUSER","0")));
		CONFIG.put("GADDUSER", Integer.parseInt(cfg.getProperty("GADDUSER","0")));
		CONFIG.put("GIVE", Integer.parseInt(cfg.getProperty("GIVE","0")));
		CONFIG.put("GRPREN", Integer.parseInt(cfg.getProperty("GRPREN","0")));
		CONFIG.put("KICK", Integer.parseInt(cfg.getProperty("KICK","0")));
		CONFIG.put("PASSWD", Integer.parseInt(cfg.getProperty("PASSWD","0")));
		CONFIG.put("PURGE", Integer.parseInt(cfg.getProperty("PURGE","0")));
		CONFIG.put("READD", Integer.parseInt(cfg.getProperty("READD","0")));
		CONFIG.put("RENUSER", Integer.parseInt(cfg.getProperty("RENUSER","0")));
		CONFIG.put("TAGLINE", Integer.parseInt(cfg.getProperty("TAGLINE","0")));
		CONFIG.put("TAKE", Integer.parseInt(cfg.getProperty("TAKE","0")));
		CONFIG.put("UNBAN", Integer.parseInt(cfg.getProperty("UNBAN","0")));
	}
}
