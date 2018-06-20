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
package org.drftpd.util;

import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.Comparator;
import java.util.TreeSet;

/**
 * @author djb61
 * @version $Id$
 */
public class FileUtils {

	public static FileHandle getOldestFile(DirectoryHandle dir) throws FileNotFoundException {
		TreeSet<FileHandle> files = new TreeSet<>(new FileAgeComparator());
		files.addAll(dir.getFilesUnchecked());
		return files.first();
	}

	static class FileAgeComparator implements Comparator<FileHandle> {

		public int compare(FileHandle f1, FileHandle f2) {

			try {
				return (Long.compare(f1.lastModified(), f2.lastModified()));
			}
			catch (FileNotFoundException e) {
				return 0;
			}
		}
	}
}
