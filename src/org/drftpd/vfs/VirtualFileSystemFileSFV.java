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
package org.drftpd.vfs;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import net.sf.drftpd.NoSFVEntryException;

import org.drftpd.SFVInfo;
import org.drftpd.SFVStatus;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;

public class VirtualFileSystemFileSFV extends VirtualFileSystemFile {

	public VirtualFileSystemFileSFV(String username, String group, long size,
			Set<String> slaves) {
		super(username, group, size, slaves);
	}

	public VirtualFileSystemFileSFV(String username, String group, long size,
			String initialSlave) {
		this(username, group, size, new HashSet<String>(Arrays
				.asList(new String[] { initialSlave })));
	}

	public SFVInfo getSFVInfo() {
		try {
			return (SFVInfo) getKeyedMap().getObject(SFVInfo.SFV);
		} catch (KeyNotFoundException e) {
			return null;
		}
	}

	public void setSFVFile(SFVInfo sfvFile) {
		getKeyedMap().setObject(SFVInfo.SFV, sfvFile);
	}

	public SFVStatus getStatus() {
		int offline = 0;
		int present = 0;
		for (String fileName : getParent().getInodeNames()) {
			VirtualFileSystemInode inode;
			try {
				inode = getParent().getInodeByName(fileName);
			} catch (FileNotFoundException e) {
				// this should not happen, but is not of our concern
				// throw the exception for bug fixing
				logger
						.warn(
								"Inode reported to exist by the directory that does not exist, stop deleting files outside of drftpd",
								e);
				continue;
			}
			if (inode.isFile()) {
				VirtualFileSystemFile file = (VirtualFileSystemFile) inode;
				if (!file.isUploading()) {
					present++;
				}
				if (!file.isAvailable()) {
					offline++;
				}
			}
		}
		return new SFVStatus(getSFVInfo().getEntries().size(), offline, present);
	}

	public long getChecksum(String fileName) throws NoSFVEntryException {
		Long checksum = (Long) getSFVInfo().getEntries().get(fileName);

		if (checksum == null) {
			throw new NoSFVEntryException();
		}

		return checksum.longValue();
	}

	
	// I don't know what these are used for, so I could not write them properly
/*	public long getTotalBytes() {
		long totalBytes = 0;

		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalBytes += ((LinkedRemoteFileInterface) iter.next()).length();
		}

		return totalBytes;
	}

	public long getTotalXfertime() {
		long totalXfertime = 0;

		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
			totalXfertime += ((LinkedRemoteFileInterface) iter.next())
					.getXfertime();
		}

		return totalXfertime;
	}

	public long getXferspeed() {
		if ((getTotalXfertime() / 1000) == 0) {
			return 0;
		}

		return getTotalBytes() / (getTotalXfertime() / 1000);
	}*/

}
