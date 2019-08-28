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

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 */
public class MoveReleaseOffSlavesToMostFreeSlaves extends ArchiveType {
	private Set<RemoteSlave> _offOfSlaves;

	/*
	 * Constructor:
	 *
	 * Loads offOfSlaves which is unique to this ArchiveType
	 */
	public MoveReleaseOffSlavesToMostFreeSlaves(Archive archive, SectionInterface section, Properties props, int confnum) {
		super(archive, section, props, confnum);

		_offOfSlaves = getOffOfSlaves(props,confnum);
		if (_offOfSlaves.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 slaves found to move off MoveReleaseOffSlavesToMostFreeSlaves for conf number " + confnum);
		}

		if (_slaveList.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 destination slaves found for MoveReleaseOffSlavesToMostFreeSlaves for conf number " + confnum);
		}

		if (_numOfSlaves < 1) {
			throw new IllegalArgumentException("numOfSlaves has to be > 0 for conf number " + confnum);
		}
	}

	/*
	 * Gets configuration for offofslaves
	 */
	private Set<RemoteSlave> getOffOfSlaves(Properties props, int confnum) {
		Set<RemoteSlave> offOfSlaves = new HashSet<>();
		for (int i = 1;; i++) {
			String slavename = null;

			try {
				slavename = PropertyHelper.getProperty(props, confnum + ".offofslave." + i);
			} catch (NullPointerException e) {
				break; // done
			}

			try {
				RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
				if (!_slaveList.contains(rslave)) {
					offOfSlaves.add(rslave);
				}
			} catch (ObjectNotFoundException e) {
				// slave not found
			}
		}
		return offOfSlaves;
	}

	/*
	 *  This finds all the destination slaves listed by free space
	 *  excluding the slaves we do NOT want to send too
	 */
	@Override
	public Set<RemoteSlave> findDestinationSlaves() {
		HashSet<RemoteSlave> destSlaves = new HashSet<>();
		for (RemoteSlave freeslave: GlobalContext.getGlobalContext().getSlaveManager().findSlavesBySpace(_numOfSlaves,_offOfSlaves, false)) {
			for (RemoteSlave confslave: _slaveList) {
				if (freeslave.getName().equals(confslave.getName())) {
					destSlaves.add(confslave);
					break;
				}
			}
		}

		return destSlaves;
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
                    for (RemoteSlave rslave : ((FileHandle) inode).getAvailableSlaves()) {
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

		return isArchivedToSpecificSlaves(lrf, _numOfSlaves,findDestinationSlaves());

	}

	/*
	 * Outs this as a string to show what is being archived.
	 */
	@Override
	public String toString() {
		return "MoveReleaseOffSlavesToMostFreeSlaves=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(findDestinationSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}

}
