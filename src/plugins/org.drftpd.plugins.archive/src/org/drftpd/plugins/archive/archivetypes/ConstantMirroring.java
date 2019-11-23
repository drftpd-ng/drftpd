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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author CyBeR
 */
public class ConstantMirroring extends ArchiveType {
	private long _slaveDeadAfter;

	private static final Logger logger = LogManager.getLogger(ConstantMirroring.class);

	/*
	 * Consturctor:
	 *
	 * Loads slaveDeadAfter which is a special config just for this archivetype
	 */
	public ConstantMirroring(Archive archive, SectionInterface section,Properties p, int confnum) {
		super(archive, section, p, confnum);
		_slaveDeadAfter = 1000 * 60 * Long.parseLong(p.getProperty(confnum + ".slavedeadafter", "0"));
		int size = 0;

		if (_slaveList.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 destination slaves found for ConstantMirroring for conf number " + confnum);
		}
		size = _slaveList.size();

		if (_numOfSlaves > size && _numOfSlaves < 1) {
			throw new IllegalArgumentException("numOfSlaves has to be 1 <= numOfSlaves <= the size of the destination slave for conf number " + confnum);
		}
	}

	/*
	 *  We do NOT want to return any other destination slaves than what is listed
	 *  inside the .conf file
	 */
	@Override
	public Set<RemoteSlave> findDestinationSlaves() {
		return _slaveList == null ? null : Collections.unmodifiableSet(_slaveList);
	}

	@Override
	protected boolean isArchivedDir(DirectoryHandle lrf) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException {
		for (FileHandle src : lrf.getFilesUnchecked()) {

			Collection<RemoteSlave> slaves;

			slaves = src.getSlaves();

			/*
			 * Only check if this slave is dead if slaveDeadAfter is
			 * configured to a non-zero value
			 */

			if (_slaveDeadAfter > 0) {
				for (Iterator<RemoteSlave> slaveIter = slaves.iterator(); slaveIter.hasNext();) {
					RemoteSlave rslave = slaveIter.next();
					if (!rslave.isAvailable()) {
						long offlineTime = System.currentTimeMillis() - rslave.getLastTimeOnline();
						if (offlineTime > _slaveDeadAfter) {
							// slave is considered dead
							slaveIter.remove();
						}
					}
				}
			}

			if (!findDestinationSlaves().containsAll(slaves)) {
				return false;
			}

            logger.debug("Constant Mirroring DEBUG - FILE: '{}' - NumOfSlaves: '{}' - Slave Size: '{}'", lrf.getName(), _numOfSlaves, slaves.size());

			if (slaves.size() != _numOfSlaves) {
				return false;
			}

		}
		for (DirectoryHandle dir : lrf.getDirectoriesUnchecked()) {
			if (!isArchivedDir(dir)) {
				return false;
			}
		}
		return true;
	}

	@Override
	public String toString() {
		return "ConstantMirroring=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(findDestinationSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}
}
