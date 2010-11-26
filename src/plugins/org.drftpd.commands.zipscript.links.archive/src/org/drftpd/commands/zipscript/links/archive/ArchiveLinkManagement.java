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
package org.drftpd.commands.zipscript.links.archive;

import java.io.FileNotFoundException;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.PluginInterface;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.plugins.archive.event.ArchiveFinishEvent;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author CyBeR
 * @version $Id: ArchiveLinkManagement.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class ArchiveLinkManagement implements PluginInterface {
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
	}

    @EventSubscriber
	public void onArchiveFinishEvent(ArchiveFinishEvent event) {
    	// Check to see if directory moved
    	if (event.getArchiveType().getDestinationDirectory().getPath() != null) {
    		
    		DirectoryHandle fromDir = event.getArchiveType().getDirectory();
    		DirectoryHandle toDir = event.getArchiveType().getDestinationDirectory();
    		
    		// THis is for links inside the moved DIR
    		try {
    			for (LinkHandle link :  toDir.getLinksUnchecked()) {
    				try {
    					link.getTargetDirectoryUnchecked();
    				} catch (FileNotFoundException e1) {
    					// Link target no longer exists, remove it
    					
    					if (link.getTargetStringWithSlash().startsWith(fromDir.getPath() + VirtualFileSystem.separator)) {
    						link.setTarget(link.getTargetString().replace(fromDir.getPath(),toDir.getPath()));
    					} else {
    						link.deleteUnchecked();
    					}
    				} catch (ObjectNotValidException e1) {
    					// Link target isn't a directory, delete the link as it is bad
    					link.deleteUnchecked();
    				}
    			}
    		} catch (FileNotFoundException e2) {
    			//ignore - dir probably doesn't exist anymore as it was move
    		}
    		
    		// THis is for links in the Parent DIR
    		if (!fromDir.isRoot()) {
    			try {
    				for (LinkHandle link : fromDir.getParent().getLinksUnchecked()) {
    					try {
    						link.getTargetDirectoryUnchecked();
    					} catch (FileNotFoundException e1) {
    						// Link target no longer exists, remove it

    						if (link.getTargetStringWithSlash().startsWith(fromDir.getPath() + VirtualFileSystem.separator)) {
        						
        						LinkHandle newlink = toDir.getParent().getNonExistentLinkHandle(link.getName());
            					try {
            						link.setTarget(link.getTargetString().replace(fromDir.getPath(),toDir.getPath()));
    								link.renameToUnchecked(newlink);
    							} catch (FileExistsException e) {
    								// couldn't rename it, it already exists - ignore
    							} catch (FileNotFoundException e2) {
    								// couldn't set target
    								link.deleteUnchecked();
    							}
            					
        					} else {
        						link.deleteUnchecked();
        					}
    						
    					} catch (ObjectNotValidException e1) {
    						// Link target isn't a directory, delete the link as it is bad
    						link.deleteUnchecked();
    					}
    				}
    			} catch (FileNotFoundException e2) {
    				//ignore - dir probably doesn't exist anymore as it was move
    			}
    		}    		
    	}
    }

}
