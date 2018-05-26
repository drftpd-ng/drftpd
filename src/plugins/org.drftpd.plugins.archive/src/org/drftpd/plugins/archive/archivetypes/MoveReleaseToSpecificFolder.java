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

import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Properties;

/**
 * @author CyBeR
 */
public class MoveReleaseToSpecificFolder extends ArchiveType {

	/*
	 * Constructor:
	 */
	public MoveReleaseToSpecificFolder(Archive archive, SectionInterface section, Properties props, int confnum) {
		super(archive, section, props, confnum);
	}

	/*
	 *  Not needed cause we are just moving slaves
	 */
	@Override
	public HashSet<RemoteSlave> findDestinationSlaves() {
		return null;
	}

	/*
	 * Checks if files are archived to the right slave, however not needed - return true.
	 */
	protected boolean isArchivedDir(DirectoryHandle lrf) throws IncompleteDirectoryException, OfflineSlaveException, FileNotFoundException {
		return true;
	}

	/*
	 *  Setting this to true will ONLY move the dir and not archive to a different slave
	 */
	@Override
	public boolean moveReleaseOnly() {
		return true;
	}

	/*
	 * Outs this as a string to show what is being archived.
	 */
	@Override
	public String toString() {
		return "MoveReleaseToSpecificFolder=[directory=[" + getDirectory().getPath() + "]dest=[" + _archiveToFolder.getPath() + "]]";
	}

}
