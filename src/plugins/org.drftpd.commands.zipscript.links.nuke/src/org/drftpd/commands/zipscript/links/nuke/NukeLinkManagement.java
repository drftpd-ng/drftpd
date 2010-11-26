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
package org.drftpd.commands.zipscript.links.nuke;

import java.io.FileNotFoundException;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.PluginInterface;
import org.drftpd.event.NukeEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author CyBeR
 * @version $Id: NukeLinkManagement.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class NukeLinkManagement implements PluginInterface {
	private static String _prefix = "[NUKED]-";
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
	}

    @EventSubscriber
	public void onNukeEvent(NukeEvent event) {
    	DirectoryHandle fromDir = null;
		DirectoryHandle toDir = null;
		if (event.getCommand().equalsIgnoreCase("nuke")) {
			fromDir = new DirectoryHandle(VirtualFileSystem.separator).getNonExistentDirectoryHandle(event.getPath());
			toDir  = new DirectoryHandle(VirtualFileSystem.separator).getNonExistentDirectoryHandle(fromDir.getParent().getPath() + VirtualFileSystem.separator + _prefix + fromDir.getName());
		} else {
			toDir = new DirectoryHandle(VirtualFileSystem.separator).getNonExistentDirectoryHandle(event.getPath());
			fromDir  = new DirectoryHandle(VirtualFileSystem.separator).getNonExistentDirectoryHandle(toDir.getParent().getPath() + VirtualFileSystem.separator + _prefix + toDir.getName());
		}

		// Checks nuked dir for linkes inside to move
		try {
			for (LinkHandle link :  toDir.getLinksUnchecked()) {
				try {
					link.getTargetDirectoryUnchecked();
				} catch (FileNotFoundException e1) {
					// Link target no longer exists, remove it
					if (link.getTargetString().startsWith(fromDir.getPath())) {
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
		
		// Check the Parent dir to see if any links are there
		if (!fromDir.isRoot()) {
			try {
				for (LinkHandle link : fromDir.getParent().getLinksUnchecked()) {
					try {
						link.getTargetDirectoryUnchecked();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remove it
    					if (link.getTargetString().startsWith(fromDir.getPath())) {
    						LinkHandle newlink = toDir.getParent().getNonExistentLinkHandle(link.getName().replace(fromDir.getName(), _prefix + fromDir.getName()));
    						if (event.getCommand().equalsIgnoreCase("unnuke")) {
    							newlink = toDir.getParent().getNonExistentLinkHandle(link.getName().replace(_prefix,""));
    						}
        					try {
        						link.setTarget(link.getTargetString().replace(fromDir.getPath(),toDir.getPath()));
        						link.renameToUnchecked(newlink);
							} catch (FileExistsException e) {
								// couldn't rename it, it already exists ignore
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
