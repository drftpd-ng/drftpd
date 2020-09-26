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
package org.drftpd.nukefilter.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;

/**
 * @author phew
 */
public class NukeFilterManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(NukeFilterManager.class);

    private final NukeFilterSettings _nfs;

    public NukeFilterManager() {
        _nfs = new NukeFilterSettings();
    }

    public static NukeFilterManager getNukeFilterManager() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof NukeFilterManager) {
                return (NukeFilterManager) plugin;
            }
        }
        throw new RuntimeException("NukeFilter plugin is not loaded.");
    }

    public void startPlugin() {
        AnnotationProcessor.process(this);
        _nfs.reloadConfigs();
        logger.debug("Loaded the NukeFilter plugin successfully");
    }

    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
        logger.debug("Unloaded the NukeFilter plugin successfully");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        _nfs.reloadConfigs();
    }

    protected NukeFilterSettings getNukeFilterSettings() {
        return _nfs;
    }


}
