/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.zipscript.master.sfv.list;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.common.slave.LightRemoteInode;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.commands.list.AddListElementsInterface;
import org.drftpd.master.commands.list.ListElementsContainer;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.master.sfv.SFVTools;
import org.drftpd.zipscript.master.sfv.ZipscriptVFSDataSFV;
import org.reflections.Reflections;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptList extends SFVTools implements AddListElementsInterface {

    private static final Logger logger = LogManager.getLogger(ZipscriptList.class);

    private ArrayList<ZipscriptListStatusBarInterface> _statusBarProviders = new ArrayList<>();

    public void initialize() {
        // Subscribe to events
        AnnotationProcessor.process(this);
        // TODO @ JRI [Done] ListStatusBarProvider
        try {
            Set<Class<? extends ZipscriptListStatusBarInterface>> bars = new Reflections("org.drftpd")
                    .getSubTypesOf(ZipscriptListStatusBarInterface.class);
            for (Class<? extends ZipscriptListStatusBarInterface> sbAddon : bars) {
                ZipscriptListStatusBarInterface barInterface = sbAddon.getConstructor().newInstance();
                _statusBarProviders.add(barInterface);
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for org.drftpd.master.commands.zipscript extension point 'ListStatusBarProvider'" +
                    ", possibly the org.drftpd.master.commands.zipscript extension point definition has changed in the plugin.xml", e);
        }
    }

    public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {
        ResourceBundle bundle = container.getCommandManager().getResourceBundle();
        // Check config
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        boolean statusBarEnabled = cfg.getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
        boolean missingFilesEnabled = cfg.getProperty("files.missing.enabled", "false").equalsIgnoreCase("true");
        if (statusBarEnabled || missingFilesEnabled) {
            ArrayList<String> statusBarEntries = new ArrayList<>();
            Map<String, Object> env = new HashMap<>();
            try {
                ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
                SFVInfo sfvfile = sfvData.getSFVInfo();
                SFVStatus sfvstatus = sfvData.getSFVStatus();

                if (statusBarEnabled) {
                    if (sfvfile.getSize() != 0) {
                        env.put("complete.total", "" + sfvfile.getSize());
                        env.put("complete.number", "" + sfvstatus.getPresent());
                        env.put("complete.percent", "" + (sfvstatus.getPresent() * 100)
                                / sfvfile.getSize());
                        env.put("complete.totalbytes", Bytes.formatBytes(getSFVTotalBytes(dir, sfvData)));
                        statusBarEntries.add(container.getSession().jprintf(bundle, "zip.statusbar.complete", env, container.getUser()));

                        if (sfvstatus.getOffline() != 0) {
                            env.put("offline.number", "" + sfvstatus.getOffline());
                            env.put("offline.percent", "" + (sfvstatus.getOffline() * 100) / sfvstatus.getPresent());
                            env.put("online.number", "" + sfvstatus.getPresent());
                            env.put("online.percent", "" + (sfvstatus.getAvailable() * 100) / sfvstatus.getPresent());
                            statusBarEntries.add(container.getSession().jprintf(bundle, "zip.statusbar.offline", env, container.getUser()));
                        }
                    }
                }
                if (missingFilesEnabled && sfvfile.getSize() != 0) {
                    for (String fileName : sfvfile.getEntries().keySet()) {
                        FileHandle file = new FileHandle(dir.getPath() + VirtualFileSystem.separator + fileName);
                        if (!file.exists()) {
                            env.put("mfilename", fileName);
                            container.getElements().add(new LightRemoteInode(
                                    container.getSession().jprintf(bundle, "zip.files.missing.filename", env, container.getUser()),
                                    "drftpd", "drftpd", dir.lastModified(), 0L));
                        }
                    }
                }
            } catch (NoAvailableSlaveException e) {
                logger.warn("No available slaves for SFV file in{}", dir.getPath());
            } catch (FileNotFoundException e) {
                // no sfv file in directory - just skip it
            } catch (IOException e) {
                // unable to read sfv - just skip it
            } catch (SlaveUnavailableException e) {
                logger.warn("No available slaves for SFV file in{}", dir.getPath());
            }
            if (statusBarEnabled) {
                for (ZipscriptListStatusBarInterface zle : _statusBarProviders) {
                    try {
                        for (String statusEntry : zle.getStatusBarEntry(dir, container)) {
                            statusBarEntries.add(statusEntry);
                        }
                    } catch (NoEntryAvailableException e) {
                        // Nothing to add at this time, carry on
                    }
                }
                String entrySeparator = container.getSession().jprintf(bundle, "zip.statusbar.separator", env, container.getUser());
                StringBuilder statusBarBuilder = new StringBuilder();
                for (Iterator<String> iter = statusBarEntries.iterator(); iter.hasNext(); ) {
                    String statusBarElement = iter.next();
                    statusBarBuilder.append(statusBarElement);
                    if (iter.hasNext()) {
                        statusBarBuilder.append(" ");
                        statusBarBuilder.append(entrySeparator);
                        statusBarBuilder.append(" ");
                    }
                }
                if (statusBarBuilder.length() > 0) {
                    env.put("statusbar", statusBarBuilder.toString());
                    String statusDirName = container.getSession().jprintf(bundle, "zip.statusbar.format", env, container.getUser());

                    if (statusDirName == null) {
                        throw new RuntimeException();
                    }

                    try {
                        boolean statusBarIsDir = cfg.getProperty("statusbar.directory").equalsIgnoreCase("true");
                        container.getElements().add(
                                new LightRemoteInode(statusDirName, "drftpd", "drftpd", statusBarIsDir, dir.lastModified(), 0L));
                    } catch (FileNotFoundException e) {
                        // dir was deleted during list operation
                    }
                }
            }
        }
        return container;
    }

    public void unload() {
        AnnotationProcessor.unprocess(this);
    }
}
