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
package org.drftpd.commands.zipscript.zip;

import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseZipCRCInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipTools {

	public static Collection<FileHandle> getZipFiles(DirectoryHandle dir) 
	throws IOException, FileNotFoundException {
		Collection<FileHandle> files = new ArrayList<>();

		for (FileHandle file : dir.getFilesUnchecked()) {
			if (file.getName().toLowerCase().endsWith(".zip") && file.getXfertime() != -1) {
				files.add(file);
			}
		}
		return files;
	}

	public static long getZipTotalBytes(DirectoryHandle dir) 
	throws IOException, FileNotFoundException {
		long totalBytes = 0;

		for (FileHandle file : getZipFiles(dir)) {
			totalBytes += file.getSize();
		}
		return totalBytes;
	}

	public static long getZipLargestFileBytes(DirectoryHandle dir) 
	throws IOException, FileNotFoundException {
		long largestFileBytes = 0;

		for (FileHandle file : getZipFiles(dir)) {
			if (file.getSize() > largestFileBytes) {
				largestFileBytes = file.getSize();
			}
		}
		return largestFileBytes;
	}

	public static long getZipTotalXfertime(DirectoryHandle dir)
	throws IOException, FileNotFoundException {
		long totalXfertime = 0;

		for (FileHandle file : getZipFiles(dir)) {
			totalXfertime += file.getXfertime();
		}
		return totalXfertime;
	}

	public static long getXferspeed(DirectoryHandle dir)
	throws IOException, FileNotFoundException {
		long totalXfertime = getZipTotalXfertime(dir);
		if (totalXfertime / 1000 == 0) {
			return 0;
		}

		return getZipTotalBytes(dir) / (totalXfertime / 1000);
	}

	public static boolean getZipIntegrityFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseZipCRCInfo) rslave.fetchResponse(index)).isOk();
	}

	public static DizStatus getDizStatus(DizInfo dizInfo, DirectoryHandle dir)
	throws IOException, FileNotFoundException {
		int offline = 0;
		int present = 0;
		for (FileHandle file : dir.getFilesUnchecked()) {
			if (file.isFile() && file.getName().toLowerCase().endsWith(".zip")) {
				if (!file.isUploading()) {
					present++;
				}
				if (!file.isAvailable()) {
					offline++;
				}
			}
		}
		return new DizStatus(dizInfo.getTotal(), offline, present);
	}
}
