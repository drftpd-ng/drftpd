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
package org.drftpd.links.master.types.nosample;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginDependencies;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.links.master.LinkManager;
import org.drftpd.links.master.LinkType;
import org.drftpd.master.event.DirectoryFtpEvent;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.event.VirtualFileSystemInodeDeletedEvent;

@PluginDependencies(refs = {LinkManager.class})
public class NoSampleManager implements PluginInterface {

    private static final Logger logger = LogManager.getLogger(NoSampleManager.class);
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
     * Upload into a Sample dir: the parent now has a sample, remove its link.
     * Upload anywhere else: the dir has files, add a link unless it has a Sample.
     */
    @EventSubscriber
    public void onTransferEvent(TransferEvent event) {
        if (!event.getCommand().equals("STOR")) {
            return;
        }
        DirectoryHandle dir = event.getDirectory();
        for (LinkType link : _linkmanager.getLinks()) {
            if (link instanceof NoSample) {
                if (((NoSample) link).isSampleDirName(dir.getName())) {
                    link.doDeleteLink(dir.getParent());
                } else {
                    link.doCreateLink(dir);
                }
            }
        }
    }

    /*
     * Creating a Sample dir removes the link of its parent.
     */
    @EventSubscriber
    public void onDirectoryFtpEvent(DirectoryFtpEvent direvent) {
        if (!"MKD".equals(direvent.getCommand())) {
            return;
        }
        DirectoryHandle dir = direvent.getDirectory();
        for (LinkType link : _linkmanager.getLinks()) {
            if (link instanceof NoSample && ((NoSample) link).isSampleDirName(dir.getName())) {
                link.doDeleteLink(dir.getParent());
            }
        }
    }

    /*
     * Removing a Sample dir from a dir that still exists re-adds the link
     * if the parent has files (doFixLink decides). Release deletion and
     * rename are handled by LinkManager through the deleteon setting.
     */
    @EventSubscriber
    public void onVirtualFileSystemInodeDeletedEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
        if (!vfsevent.getInode().isDirectory()) {
            return;
        }
        DirectoryHandle parent = vfsevent.getInode().getParent();
        if (!parent.exists()) {
            return;
        }
        for (LinkType link : _linkmanager.getLinks()) {
            if (link instanceof NoSample && ((NoSample) link).isSampleDirName(vfsevent.getInode().getName())) {
                link.doFixLink(parent);
            }
        }
    }
}
