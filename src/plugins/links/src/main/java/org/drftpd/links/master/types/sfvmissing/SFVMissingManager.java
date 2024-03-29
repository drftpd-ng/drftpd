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
package org.drftpd.links.master.types.sfvmissing;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;

import org.drftpd.common.extensibility.PluginDependencies;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.links.master.LinkManager;
import org.drftpd.links.master.LinkType;
import org.drftpd.links.master.types.zipincomplete.ZipIncompleteManager;
import org.drftpd.master.event.DirectoryFtpEvent;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.event.VirtualFileSystemInodeDeletedEvent;

import java.io.FileNotFoundException;

/**
 * @author CyBeR
 * @version $Id: SFVMissingManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

@PluginDependencies(refs = {LinkManager.class})
public class SFVMissingManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(SFVMissingManager.class);
    private LinkManager _linkmanager;

    @Override
    public void startPlugin() {
        AnnotationProcessor.process(this);
        loadManager();
    }

    @Override
    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        loadManager();
    }

    private void loadManager() {
        _linkmanager = LinkManager.getLinkManager();
    }

    /*
     * Checks to see if the file uploaded ends with .sfv
     * If it does, it deletes the link associated with this folder
     */
    @EventSubscriber
    public void onTransferEvent(TransferEvent event) {
        if (!event.getCommand().equals("STOR")) {
            return;
        }

        if (event.getTransferFile().getName().toLowerCase().endsWith(".sfv")) {
            for (LinkType link : _linkmanager.getLinks()) {
                if (link.getEventType().equals("sfvmissing")) {
                    link.doDeleteLink(event.getDirectory());
                }
            }
        }
    }

    /*
     * Creates missing SFV on new DIR creation
     *
     * Using DirectryFtpEvent vs VirtualFileSystemInodeCreatedEvent because
     * the VFS event is only called AFTER the dir is created in the VFS, which
     * could cause a problem if the .nfo file was uploaded first, and the asyncevent
     * for the .sfv was before vfs event.
     */
    @EventSubscriber
    public void onDirectoryFtpEvent(DirectoryFtpEvent direvent) {
        if ("MKD".equals(direvent.getCommand())) {
            for (LinkType link : _linkmanager.getLinks()) {
                if (link.getEventType().equals("sfvmissing")) {
                    link.doCreateLink(direvent.getDirectory());
                }
            }
        }
    }


    /*
     * This checks to see if there is first a SECOND sfv file in the dir.  If there is is
     * promptly exists.  If there isn't after the delete event, it re-adds a link
     * for this directory.
     */
    @EventSubscriber
    public void onVirtualFileSystemInodeDeletedEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
        if (vfsevent.getInode().isFile()) {
            // logger.debug("Caught VirtualFileSystemInodeDeletedEvent - isFile() - {}", vfsevent.getInode());
            if (vfsevent.getInode().getParent().exists()) {
                try {
                    for (FileHandle file : vfsevent.getInode().getParent().getFilesUnchecked()) {
                        if ((file.getPath().toLowerCase().endsWith(".sfv")) && (!file.getPath().equals(vfsevent.getInode().getPath()))) {
                            return;
                        }
                    }
                } catch (FileNotFoundException e) {
                    // files not found....dir must not longer exist - Ignore
                }

            }
            for (LinkType link : _linkmanager.getLinks()) {
                if (link.getEventType().equals("sfvmissing")) {
                    try {
                        for (DirectoryHandle dir : vfsevent.getInode().getParent().getDirectoriesUnchecked()) {
                            if (dir.getName().matches(link.getAddParentDir())) {
                                // Already has a dir in it that SHOULD have a sfv
                                return;
                            }
                        }
                    } catch (FileNotFoundException e) {
                        // no dirs found....dir must not longer exist - Ignore
                    }
                    link.doCreateLink(vfsevent.getInode().getParent());
                }
            }
        } else if (vfsevent.getInode().isDirectory()) {
            // logger.debug("Caught VirtualFileSystemInodeDeletedEvent - isDirectory() - {}", vfsevent.getInode());
            if (vfsevent.getInode().getParent().exists()) {
                // If the parent exists we could now be missing a .sfv and if so we need a link.
                for (LinkType link : _linkmanager.getLinks()) {
                    if (link.getEventType().equals("sfvmissing")) {
                        try {
                            for (FileHandle file : vfsevent.getInode().getParent().getFilesUnchecked()) {
                                if (file.getName().toLowerCase().endsWith(".sfv")) {
                                    // there is a .sfv in here, ignore
                                    return;
                                }
                            }
                        } catch(FileNotFoundException e) {
                            // Directory does not exist or no files found, not an issue - Ignore
                            return;
                        }
                        link.doCreateLink(vfsevent.getInode().getParent());
                    }
                }
            }
        }
    }
}
