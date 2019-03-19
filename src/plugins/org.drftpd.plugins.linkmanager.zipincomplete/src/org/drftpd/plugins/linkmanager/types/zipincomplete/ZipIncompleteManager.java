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
package org.drftpd.plugins.linkmanager.types.zipincomplete;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.PluginInterface;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.plugins.linkmanager.LinkManager;
import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.event.VirtualFileSystemInodeDeletedEvent;

/**
 * @author CyBeR
 * @version $Id: ZipIncompleteManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class ZipIncompleteManager implements PluginInterface {
	LinkManager _linkmanager;
	
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
    	loadManager();
    }
    
    private void loadManager() {
    	_linkmanager = LinkManager.getLinkManager();
    }
    
    /*
     * Check to see if a .zip file has been uploaded
     * then it checks to see if the dir is finished, and either creates a link
     * or removes the old one.
     */
    @EventSubscriber
    public void onTransferEvent(TransferEvent event) {
        if (!event.getCommand().equals("STOR")) {
        	return;
        }
        
        if (event.getTransferFile().getName().toLowerCase().endsWith(".zip")) {
			ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(event.getDirectory());
			try {
				if (zipData.getDizStatus().isFinished()) {
					for (LinkType link : _linkmanager.getLinks()) {
						if (link.getEventType().equals("zipincomplete")) {
							link.doDeleteLink(event.getDirectory());
						}
					}
				} else {
					for (LinkType link : _linkmanager.getLinks()) {
						if (link.getEventType().equals("zipincomplete")) {
							link.doCreateLink(event.getDirectory());
						}
					}
				}
			} catch (FileNotFoundException e) {
				// no .zip found - ignore
			} catch (IOException e) {
				// can't read .zip - ignore
			} catch (NoAvailableSlaveException e) {
				// no slaves available for .zip - ignore
			}
        }
    }
    
    /*
     * This checks to see if the file uploaded was a .zip file.  Then checks
     * if the dir is finished, if it isn't then it creates the link and exits.
     * 
     * If the dir has no .zip left in there, the .zip incomplete link is removed.
     */
	@EventSubscriber
	public void onVirtualFileSystemInodeDeletedEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
		if (vfsevent.getInode().isFile()) {
			if (vfsevent.getInode().getParent().exists()) {
				if ((new FileHandle(vfsevent.getInode().getPath())).getName().endsWith(".zip")) {
					ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(vfsevent.getInode().getParent());
					try {
						if (!zipData.getDizStatus().isFinished()) {
							for (LinkType link : _linkmanager.getLinks()) {
								if (link.getEventType().equals("zipincomplete")) {
									link.doCreateLink(vfsevent.getInode().getParent());
								}
							}
							return;
						}
					} catch (FileNotFoundException e) {
						// no zip found - ignore
					} catch (IOException e) {
						// can't read zip - ignore
					} catch (NoAvailableSlaveException e) {
						// no slaves available for zip - ignore
					}
					
					/* Can't find the zip file - lets delete the links */
					for (LinkType link : _linkmanager.getLinks()) {
						if (link.getEventType().equals("zipincomplete")) {
							link.doDeleteLink(vfsevent.getInode().getParent());
						}
					}
				}
			}
		}
	}
}