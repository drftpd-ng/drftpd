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
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 */
public class MoveReleaseToMostFreeSlaves extends ArchiveType {

	/*
	 * Constructor:
	 */
	public MoveReleaseToMostFreeSlaves(Archive archive, SectionInterface section, Properties props, int confnum) {
		super(archive, section, props,confnum);

		if (_slaveList.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 destination slaves found for MoveReleaseToMostFreeSlaves for conf number " + confnum);
		}

		if (_numOfSlaves < 1) {
			throw new IllegalArgumentException("numOfSlaves has to be > 0 for conf number " + confnum);
		}
	}

	/*
	 *  This finds all the destination slaves listed by free space.
	 */
	@Override
	public Set<RemoteSlave> findDestinationSlaves() {
		HashSet<RemoteSlave> destSlaves = new HashSet<>();
		for (RemoteSlave freeslave: GlobalContext.getGlobalContext().getSlaveManager().findSlavesBySpace(_numOfSlaves, new HashSet<>(), false)) {
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
	 * Checks if the dir is already archived
	 */
	@Override
	protected boolean isArchivedDir(DirectoryHandle lrf) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException {
		return isArchivedToSpecificSlaves(lrf, _numOfSlaves,findDestinationSlaves());
	}

	/*
	 * Outs this as a string to show what is being archived.
	 */
	@Override
	public String toString() {
		return "MoveReleaseToMostFreeSlaves=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(findDestinationSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}

}
