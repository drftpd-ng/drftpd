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
package org.drftpd.plugins.sitebot;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;

import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotWrapper implements PluginInterface {

	private static final Logger logger = LogManager.getLogger(SiteBotWrapper.class);
	
	private ArrayList<SiteBot> _bots = new ArrayList<>();

	public void startPlugin() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig()
			.getPropertiesForPlugin("irc/irc.conf");
		
		if (cfg.isEmpty()) {
			logger.debug("No configuration found for the SiteBot, skipping initialization");
			return;
		}
		
		SiteBot bot = new SiteBot("irc");
		new Thread(bot).start();
		_bots.add(bot);
		if (cfg.getProperty("bot.multiple.enable").equalsIgnoreCase("true")) {
			StringTokenizer st = new StringTokenizer(cfg.getProperty(
					"bot.multiple.directories"));
			while (st.hasMoreTokens()) {
				bot = new SiteBot("irc/"+st.nextToken());
				new Thread(bot).start();
				_bots.add(bot);
			}
		}
	}

	public void stopPlugin(String reason) {
		for (SiteBot bot : _bots) {
			bot.terminate(reason);
		}
	}

	public ArrayList<SiteBot> getBots() {
		return _bots;
	}
}
