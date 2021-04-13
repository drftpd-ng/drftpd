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
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.vfs.DirectoryHandle;

/**
 * @author lh
 */
public class Mirror extends CommandInterface {
    private static final Logger logger = LogManager.getLogger(Mirror.class);

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        reload();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private void reload() {
        MirrorSettings.getSettings().reload();
    }

    public CommandResponse doUNMIRROR(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }
        DirectoryHandle dir;
        try {
            dir = GlobalContext.getGlobalContext().getRoot().getDirectory(request.getArgument(), request.getUserObject());
        } catch (Exception e) {
            return new CommandResponse(500, "Failed getting requested directory: " + e.getMessage());
        }
        try {
            MirrorUtils.unMirrorDir(dir, request.getUserObject(), MirrorSettings.getSettings().getUnmirrorExcludePaths());
        } catch (Exception e) {
            return new CommandResponse(500, "Unmirror error: " + e.getMessage());
        }
        return new CommandResponse(200, "Directory successfully unmirrored!");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        reload();
    }
}
