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

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 */
public class MoveReleaseOffMultipleSlavesToSpecificSlaves extends ArchiveType {

	/*
	 * Constructor:
	 */
	public MoveReleaseOffMultipleSlavesToSpecificSlaves(Archive archive, SectionInterface section, Properties props, int confnum) {
		super(archive, section, props, confnum);

		if (_slaveList.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 destination slaves found for MoveReleaseOffSlavesToMostFreeSlaves for conf number " + confnum);
		}

		if (_numOfSlaves < 1) {
			throw new IllegalArgumentException("numOfSlaves has to be > 0 for conf number " + confnum);
		}
	}

	/*
	 *  This finds
	 */
	@Override
	public Set<RemoteSlave> findDestinationSlaves() {
		return _slaveList == null ? null : Collections.unmodifiableSet(_slaveList);
	}

	/*
	 * Checks if the dir/file is already archived
	 * Also checks if it is removed from all slaves.
	 */
	@Override
	protected boolean isArchivedDir(DirectoryHandle lrf) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException {
        for (InodeHandle inode : lrf.getInodeHandlesUnchecked()) {
            if (inode.isLink()) {
            } else if (inode instanceof DirectoryHandle) {
                if (!isArchivedDir((DirectoryHandle) inode)) {
                    return false;
                }
            } else {
                try {

                    int found = 0;
                    for (RemoteSlave rslave : ((FileHandle) inode).getAvailableSlaves()) {
                        if (findDestinationSlaves().contains(rslave)) {
                            found++;
                        }

                        if (found > _numOfSlaves) {
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
		return isArchivedToSpecificSlaves(lrf, _numOfSlaves,_slaveList);
	}

	/*
	 * Outs this as a string to show what is being archived.
	 */
	@Override
	public String toString() {
		return "MoveReleaseOffSlavesToMostFreeSlaves=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(findDestinationSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}
}
