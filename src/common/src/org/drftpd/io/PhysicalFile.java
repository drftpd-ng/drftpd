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
package org.drftpd.io;

import java.io.File;
import java.io.IOException;
import java.net.URI;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class PhysicalFile extends File {

	@SuppressWarnings("hiding")
	public static final String separator = "/";

	public PhysicalFile(String pathname) {
		super(pathname);
	}

	public PhysicalFile(java.io.File file) {
		super(file.getPath());
	}

	public PhysicalFile(String parent, String child) {
		super(parent, child);
	}

	public PhysicalFile(java.io.File parent, String child) {
		super(parent, child);
	}

	public PhysicalFile(URI uri) {
		super(uri);
	}

	public boolean isSymbolicLink() throws IOException {
		return !getCanonicalPath().equals(getAbsolutePath());
	}

	public boolean deleteRecursive() {
		if (isDirectory()) {
			File[] files = listFiles();
            for (File file1 : files) {
                PhysicalFile file = new PhysicalFile(file1);
                file.deleteRecursive();
            }
		}
		return super.delete();
	}

	public void delete2() throws PermissionDeniedException {
		if (!super.delete()) {
			throw new PermissionDeniedException("Failed to delete: "
					+ toString());
		}
	}

	public void mkdirs2() throws PermissionDeniedException {
		if (!exists()) {
			if (!mkdirs()) {
				throw new PermissionDeniedException("mkdirs failed on "
						+ getPath());
			}
		}
	}
}
