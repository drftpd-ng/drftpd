/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.plugins.archive.archivetypes;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Set;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author CyBeR
 */
public class MoveReleaseOffSlavesToMostFreeSlaves extends ArchiveType {
	private Set<RemoteSlave> _offOfSlaves;
	
	/*
	 * Constructor, creates archivetype
	 * makes sure all args are setup correctly
	 */
	public MoveReleaseOffSlavesToMostFreeSlaves(Archive archive, SectionInterface section, Properties props) {
		super(archive, section, props);

        _offOfSlaves = getOffOfSlaves(props);

        if (_offOfSlaves.isEmpty()) {
            throw new NullPointerException("Cannot continue, 0 slaves found to move off MoveReleaseOffSlavesToMostFreeSlaves for for section " + getSection().getName());
        }

		if (_slaveList.isEmpty()) {
		    throw new NullPointerException("Cannot continue, 0 destination slaves found for MoveReleaseOffSlavesToMostFreeSlaves for section " + getSection().getName());
		}
		
		_numOfSlaves = _slaveList.size();
		
		if (_numOfSlaves < 1) {
		    throw new IllegalArgumentException("numOfSlaves has to be > 0 for section " + section.getName());
		}
	}
	
	/*
	 *  This finds all the destination slaves listed by free space.
	 */
	public HashSet<RemoteSlave> findDestinationSlaves() {
		return new HashSet<RemoteSlave>(GlobalContext.getGlobalContext().getSlaveManager().findSlavesBySpace(_numOfSlaves,_offOfSlaves, false));
	}

	/*
	 * Checks if the dir/file is already archived
	 * Also checks if it is removed from all slaves.
	 */
    protected boolean isArchivedDir(DirectoryHandle lrf) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException {
    	for (Iterator<InodeHandle> iter = lrf.getInodeHandlesUnchecked().iterator(); iter.hasNext();) {
            InodeHandle inode = (InodeHandle) iter.next();

            if (inode.isLink()) {
            	continue;
            } else if (inode instanceof DirectoryHandle) {
            	if (!isArchivedDir((DirectoryHandle) inode)) {
            		return false;
            	}
            } else {
            	try {
	            	for (Iterator<RemoteSlave> iter2 = ((FileHandle) inode).getAvailableSlaves().iterator(); iter2.hasNext();) {
	            		RemoteSlave rslave = (RemoteSlave) iter2.next();
	            		
	                    if (_offOfSlaves.contains(rslave)) {
	                        return false;
	                    }            		
	            		
	            	}
            	} catch (NoAvailableSlaveException e) {
            		throw new OfflineSlaveException("There were no available slaves for " + inode.getPath());
            	} catch (FileNotFoundException e) {
            		throw new FileNotFoundException("File was not found " + inode.getPath());            		
            	}
            	
            }

        }

        return true;    	
    	
    }

    /*
     * Outs this as a string to show what is being archived.
     */
    public String toString() {
    	return "MoveReleaseOffSlavesToMostFreeSlaves=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
    }	

}
