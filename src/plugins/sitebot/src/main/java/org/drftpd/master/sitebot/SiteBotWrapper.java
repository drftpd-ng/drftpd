/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.sitebot;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.ConfigLoader;

import java.util.ArrayList;
import java.util.Properties;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
public class SiteBotWrapper implements PluginInterface {

    private static final Logger logger = LogManager.getLogger(SiteBotWrapper.class);

    private final ArrayList<SiteBot> _bots = new ArrayList<>();

    public void startPlugin() {
        // Load config properties
        Properties cfg = ConfigLoader.loadPluginConfig("irc.conf");

        // Bail if config is empty
        if (cfg.isEmpty()) {
            logger.debug("No configuration found for the SiteBot, skipping initialization");
            return;
        }

        // Bail if config we are not activated
        if(!cfg.getProperty("activated").equalsIgnoreCase("true")) {
            logger.info("SiteBot is not enabled in configuration, skipping initialization");
            return;
        }

        // We should initialize our main bot, do that here
        _bots.add(new SiteBot(""));

        // Handle the case were we need to create more than 1 bot
        if (cfg.getProperty("bot.multiple.enable").equalsIgnoreCase("true")) {
            StringTokenizer st = new StringTokenizer(cfg.getProperty("bot.multiple.directories"));
            while (st.hasMoreTokens()) {
                _bots.add(new SiteBot(st.nextToken()));
            }
        }
        logger.debug("Creating {} threads for SiteBots", _bots.size());
        for (SiteBot bot : _bots) {
            new Thread(bot).start();
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
