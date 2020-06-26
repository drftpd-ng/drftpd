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
package org.drftpd.links.master.types.sfvincomplete;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginDependencies;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.links.master.LinkManager;
import org.drftpd.links.master.LinkType;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.usermanager.encryptedjavabeans.EncryptedBeanUserManager;
import org.drftpd.master.vfs.event.VirtualFileSystemInodeDeletedEvent;
import org.drftpd.zipscript.master.sfv.ZipscriptVFSDataSFV;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author CyBeR
 * @version $Id: SFVIncompleteManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

@PluginDependencies(refs = {LinkManager.class})
public class SFVIncompleteManager implements PluginInterface {

    protected static final Logger logger = LogManager.getLogger(SFVIncompleteManager.class);

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
    public void onReloadEvent() {
        logger.info("Received reload event, reloading");
        loadManager();
    }

    private void loadManager() {
        _linkmanager = LinkManager.getLinkManager();
    }

    /*
     * Checks to see if a .sfv file is uploaded, and creates a missing link
     *
     * It then checks to see if the status is finished in the sfv for the dir
     * (IE another file got upload not an .sfv file) and removes the link
     *
     */
    @EventSubscriber
    public void onTransferEvent(TransferEvent event) {
        if (!event.getCommand().equals("STOR")) {
            return;
        }

        if (event.getTransferFile().getName().toLowerCase().endsWith(".sfv")) {
            for (LinkType link : _linkmanager.getLinks()) {
                if (link.getEventType().equals("sfvincomplete")) {
                    link.doCreateLink(event.getDirectory());
                }
            }
        }

        ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(event.getDirectory());
        try {
            if (sfvData.getSFVStatus().isFinished()) {
                for (LinkType link : _linkmanager.getLinks()) {
                    if (link.getEventType().equals("sfvincomplete")) {
                        link.doDeleteLink(event.getDirectory());
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // no .sfv found - ignore
        } catch (IOException e) {
            // can't read .sfv - ignore
        } catch (NoAvailableSlaveException e) {
            // no slaves available for .sfv - ignore
        } catch (SlaveUnavailableException e) {
            // no slaves available for .sfv - ignore
        }

    }

    /*
     * This checks to see if the file deleted was a .sfv file, and it was, to remove
     * the links for this dir.
     *
     * If it wasn't a .sfv file that was deleted, it checks to see if the rls is still finished
     * and if it isn't, it re-creates the link for the release.
     */
    @EventSubscriber
    public void onVirtualFileSystemInodeDeletedEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
        if (vfsevent.getInode().isFile()) {
            if (vfsevent.getInode().getParent().exists()) {
                // Check to see if file deleted was .sfv
                if (vfsevent.getInode().getName().endsWith(".sfv")) {
                    for (LinkType link : _linkmanager.getLinks()) {
                        if (link.getEventType().equals("sfvincomplete")) {
                            link.doDeleteLink(vfsevent.getInode().getParent());
                        }
                    }
                } else {
                    ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(vfsevent.getInode().getParent());
                    try {
                        if (!sfvData.getSFVStatus().isFinished()) {
                            for (LinkType link : _linkmanager.getLinks()) {
                                if (link.getEventType().equals("sfvincomplete")) {
                                    link.doCreateLink(vfsevent.getInode().getParent());
                                }
                            }
                        }
                    } catch (FileNotFoundException e) {
                        // no .sfv found - ignore
                    } catch (IOException e) {
                        // can't read .sfv - ignore
                    } catch (NoAvailableSlaveException e) {
                        // no slaves available for .sfv - ignore
                    } catch (SlaveUnavailableException e) {
                        // no slaves available for .sfv - ignore
                    }
                }
            }
        }
    }
}