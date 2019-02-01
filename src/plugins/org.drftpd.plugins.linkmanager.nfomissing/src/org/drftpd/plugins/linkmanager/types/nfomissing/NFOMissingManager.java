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
package org.drftpd.plugins.linkmanager.types.nfomissing;

import java.io.FileNotFoundException;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.PluginInterface;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.plugins.linkmanager.LinkManager;
import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.event.VirtualFileSystemInodeDeletedEvent;

/**
 * @author CyBeR
 * @version $Id: NFOMissingManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class NFOMissingManager implements PluginInterface {
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
     * Checks to see if the file uploaded ends with .nfo
     * If it does, it deletes the link associated with this folder
     */
    @EventSubscriber
    public void onTransferEvent(TransferEvent event) {
        if (!event.getCommand().equals("STOR")) {
        	return;
        }
        
        if (event.getTransferFile().getName().toLowerCase().endsWith(".nfo")) {
			for (LinkType link : _linkmanager.getLinks()) {
				if (link.getEventType().equals("nfomissing")) {
					link.doDeleteLink(event.getDirectory());
				}
			}
        }
    }
    
    /*
     * Creates missing NFO on new DIR creation
     * 
     * Using DirectryFtpEvent vs VirtualFileSystemInodeCreatedEvent because
     * the VFS event is only called AFTER the dir is created in the VFS, which
     * could cause a problem if the .nfo file was uploaded first, and the asyncevent 
     * for the .nfo was before vfs event.
     */
    @EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent direvent) {
		if ("MKD".equals(direvent.getCommand())) {
			for (LinkType link : _linkmanager.getLinks()) {
				if (link.getEventType().equals("nfomissing")) {
					link.doCreateLink(direvent.getDirectory());
				}
			}
		}  
    }
    
    
    /*
     * This checks to see if there is first a SECOND nfo file in the dir.  If there is is
     * promptly exists.  If there isn't after the delete event, it re-adds a link 
     * for this directory.
     */
	@EventSubscriber
	public void onVirtualFileSystemInodeDeletedEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
		if (vfsevent.getInode().isFile()) {
			if (vfsevent.getInode().getParent().exists()) {
				try {
					for (FileHandle file : vfsevent.getInode().getParent().getFilesUnchecked()) {
						if ((file.getPath().toLowerCase().endsWith(".nfo")) && (!file.getPath().equals(vfsevent.getInode().getPath()))) {
							return;
						}
					}
				} catch (FileNotFoundException e) {
					// files not found....dir must not longer exist - Ignore
				}
				
			}
			for (LinkType link : _linkmanager.getLinks()) {
				if (link.getEventType().equals("nfomissing")) {
					link.doCreateLink(vfsevent.getInode().getParent());
				}
			}
		}
	}
    
}