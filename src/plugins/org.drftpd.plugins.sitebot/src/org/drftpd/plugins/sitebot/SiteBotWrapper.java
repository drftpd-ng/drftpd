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

import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotWrapper implements PluginInterface {

	private ArrayList<SiteBot> _bots = new ArrayList<SiteBot>();

	public void startPlugin() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig()
			.getPropertiesForPlugin("irc/irc.conf");
		if (cfg.getProperty("bot.multiple.enable").equalsIgnoreCase("true")) {
			StringTokenizer st = new StringTokenizer(cfg.getProperty(
					"bot.multiple.directories"));
			while (st.hasMoreTokens()) {
				SiteBot bot = new SiteBot("irc/"+st.nextToken());
				_bots.add(bot);
			}
		}
		SiteBot bot = new SiteBot("irc");
		_bots.add(bot);
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
