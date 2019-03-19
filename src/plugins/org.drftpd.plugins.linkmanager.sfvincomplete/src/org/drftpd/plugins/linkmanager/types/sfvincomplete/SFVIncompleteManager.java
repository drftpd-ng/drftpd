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
package org.drftpd.plugins.linkmanager.types.sfvincomplete;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.PluginInterface;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.linkmanager.LinkManager;
import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.event.VirtualFileSystemInodeDeletedEvent;

/**
 * @author CyBeR
 * @version $Id: SFVIncompleteManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class SFVIncompleteManager implements PluginInterface {
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