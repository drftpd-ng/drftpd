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
package org.drftpd.mirror.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.*;

/**
 * @author lh
 */
public class MirrorSettings {
    private static final Logger logger = LogManager.getLogger(MirrorSettings.class);

    private static MirrorSettings ref;

    private List<MirrorConfiguration> _configurations;
    private List<String> _unmirrorExcludePaths;

    private MirrorSettings() {
        // Set defaults (just in case)
        _configurations = new ArrayList<>();
        _unmirrorExcludePaths = new ArrayList<>();
        reload();
    }

    public static synchronized MirrorSettings getSettings() {
        if (ref == null) {
            // it's ok, we can call this constructor
            ref = new MirrorSettings();
        }
        return ref;
    }

    public void reload() {
        logger.debug("Loading configation");
        Properties cfg = ConfigLoader.loadPluginConfig("mirror.conf");

        List<MirrorConfiguration> configurations = new ArrayList<>();
        List<String> unmirrorExcludePaths = new ArrayList<>();
        int id = 1;
        int j;
        String nbrOfMirrors;

        // Handle sections
        while ((nbrOfMirrors = PropertyHelper.getProperty(cfg, id + ".nbrOfMirrors", null)) != null) {
            id++;
            int nbrOfMirrorsInt;
            try {
                nbrOfMirrorsInt = Integer.parseInt(nbrOfMirrors);
            } catch (NumberFormatException e) {
                logger.error("{}.nbrOfMirrors is not an integer", id);
                continue;
            }
            if (nbrOfMirrorsInt < 2) {
                logger.error("Invalid setting for {}.nbrOfMirrors, must be greater than 2", id);
                continue;
            }
            int priority;
            try {
                priority = Integer.parseInt(cfg.getProperty(id + ".priority", "3"));
            } catch (NumberFormatException e) {
                logger.error("{}.priority is not an integer", id);
                continue;
            }
            if (priority < 1) {
                logger.error("Invalid setting for {}.priority, must be greater than 0", id);
                continue;
            }

            List<String> paths = new ArrayList<>();
            j = 1;
            String path;
            while ((path = PropertyHelper.getProperty(cfg, id + ".path" + j, null)) != null) {
                paths.add(path);
                j++;
            }

            List<String> excludedPaths = new ArrayList<>();
            j = 1;
            String excludePath;
            while ((excludePath = PropertyHelper.getProperty(cfg, id + ".excludePath" + j, null)) != null) {
                excludedPaths.add(excludePath);
                j++;
            }

            // Handle slaves
            List<String> slaves = new ArrayList<>(Arrays.asList(cfg.getProperty(id + ".slaves", "").trim().split("\\s")));
            for (String slaveName : slaves) {
                try {
                    GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
                } catch(ObjectNotFoundException e) {
                    logger.error("Slave with name [{}] does not exist, config error for id {}", slaveName, id, e);
                }
            }

            // Handle excludeSlaves
            List<String> excludeSlaves = new ArrayList<>(Arrays.asList(cfg.getProperty(id + "excludeSlaves", "").trim().split("\\s")));
            for (String slaveName : excludeSlaves) {
                try {
                    GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
                } catch(ObjectNotFoundException e) {
                    logger.error("Slave with name [{}] does not exist, config error for id {}", slaveName, id, e);
                }
            }
            configurations.add(new MirrorConfiguration(nbrOfMirrorsInt, priority, paths, excludedPaths, slaves, excludeSlaves));

            String unmirrorExclude = cfg.getProperty(id + ".unmirrorExclude");
            if (unmirrorExclude != null) {
                unmirrorExcludePaths.add(unmirrorExclude);
            }

            id++;
        }
        _configurations = configurations;
        _unmirrorExcludePaths = unmirrorExcludePaths;
    }

    public List<MirrorConfiguration> getConfigurations() {
        return _configurations;
    }

    public List<String> getUnmirrorExcludePaths() {
        return _unmirrorExcludePaths;
    }

    public static class MirrorConfiguration {
        private final int _nbrOfMirrors;
        private final int _priority;
        private final List<String> _paths;
        private final List<String> _excludedPaths;
        private final List<String> _slaves;
        private final List<String> _excludedSlaves;

        public MirrorConfiguration(int nbrOfMirrors, int priority, List<String> paths, List<String> excludedPaths, List<String> slaves, List<String> excludedSlaves) {
            _nbrOfMirrors = nbrOfMirrors;
            _priority = priority;
            _paths = paths;
            _excludedPaths = excludedPaths;
            _slaves = slaves;
            _excludedSlaves = excludedSlaves;
        }

        public int getNbrOfMirrors() {
            return _nbrOfMirrors;
        }

        public int getPriority() {
            return _priority;
        }

        public List<String> getPaths() {
            return _paths;
        }

        public List<String> getExcludedPaths() {
            return _excludedPaths;
        }

        public List<String> getSlaves() {
            return _slaves;
        }

        public List<String> getExcludedSlaves() {
            return _excludedSlaves;
        }
    }
}
