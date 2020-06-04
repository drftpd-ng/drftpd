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
package org.drftpd.autonuke.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * @author scitz0
 */
public class ConfigChain {
    private static final Logger logger = LogManager.getLogger(ConfigChain.class);

    private static final Class<?>[] SIG = new Class<?>[]{int.class, Properties.class};

    private ArrayList<Config> _configs;

    private CaseInsensitiveHashMap<String, Class<Config>> _configsMap;

    protected ConfigChain() {
    }

    public ConfigChain(CaseInsensitiveHashMap<String, Class<Config>> configsMap) {
        _configsMap = configsMap;
        reload();
    }

    public Collection<Config> getConfigs() {
        return new ArrayList<>(_configs);
    }

    public boolean checkConfig(DirectoryHandle dir) {
        int configsProcessed = 0;
        for (Config config : getConfigs()) {
            ConfigData data = new ConfigData();
            if (config.handleDirectory(data, dir)) {
                // Dir processed by this config, too old or section invalid
                configsProcessed++;
            }
            if (data.getNukeItem() != null) {
                // Config found that dir should get nuked, no need to check the rest of the configs
                return true;
            }
        }
        // All configs processed?
        return configsProcessed == getConfigs().size();
    }

    public NukeItem simpleConfigCheck(DirectoryHandle dir) {
        for (Config config : getConfigs()) {
            ConfigData data = new ConfigData();
            config.checkDirectory(data, dir);
            if (data.getNukeItem() != null) {
                return data.getNukeItem();
            }
        }
        return null;
    }

    public void reload() {
        reload(ConfigLoader.loadPluginConfig("autonuke.conf"));
    }

    public void reload(Properties p) {
        ArrayList<Config> configs = new ArrayList<>();
        int i = 1;

        for (; ; i++) {
            String configName = p.getProperty(i + ".type");

            if (configName == null) {
                break;
            }

            if (!_configsMap.containsKey(configName)) {
                logger.error("Can not find config '{}', check that config is added in plugin.xml", configName);
            }

            try {
                Class<Config> clazz = _configsMap.get(configName);
                Config config = clazz.getConstructor(SIG).newInstance(i, p);
                configs.add(config);
            } catch (Exception e) {
                throw new FatalException(i + ".type = " + configName, e);
            }
        }

        configs.trimToSize();
        _configs = configs;
    }

    public GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }
}
