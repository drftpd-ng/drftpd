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
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.vfs.DirectoryHandle;
import org.reflections.Reflections;

import java.util.Properties;
import java.util.Set;

/**
 * Configuration settings for AutoNuke plugin
 *
 * @author scitz0
 */
public class AutoNukeManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(AutoNukeManager.class);

    private ScanTask _scanTask;
    private NukeTask _nukeTask;

    private CaseInsensitiveHashMap<String, Class<? extends Config>> _configsMap;

    private ConfigChain _configChain;

    public static synchronized AutoNukeManager getANC() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof AutoNukeManager) {
                return (AutoNukeManager) plugin;
            }
        }

        throw new RuntimeException("AutoNuke plugin is not loaded.");
    }

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
        logger.info("Received reload event, reloading");
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
        Properties cfg = ConfigLoader.loadPluginConfig("autonuke.conf");
        // excluded sections
        AutoNukeSettings.getSettings().clearExcludedSections();
        for (String section : cfg.getProperty("exclude.sections", "").split(",")) {
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
        CaseInsensitiveHashMap<String, Class<? extends Config>> configsMap = new CaseInsensitiveHashMap<>();

        // TODO @k2r [DONE] Load config
        Set<Class<? extends Config>> configHandlers = new Reflections("org.drftpd")
                .getSubTypesOf(Config.class);
        for (Class<? extends Config> configHandler : configHandlers) {
            String name = configHandler.getSimpleName().replace("Config", "");
            configsMap.put(name, configHandler);
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
     *
     * @param dir Directory currently being handled
     * @return Returns true if dir should be removed, else false
     */
    public boolean checkConfigs(DirectoryHandle dir) {
        return getConfigChain().checkConfig(dir);
    }

}
