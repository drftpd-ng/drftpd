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
package org.drftpd.plugins.linkmanager.types.nfomissing;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.Set;

import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

/**
 * @author CyBeR
 * @version $Id: NFOMissing.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class NFOMissing extends LinkType {

	public NFOMissing(Properties p, int confnum, String type) {
		super(p, confnum, type);
	}

	/*
	 * This checks if any Dir from inside the targetDir matches
	 * AddParentDir, and if it does, add the link, if not don't create one
	 * 
	 * IE: /section/subsection/rls - we do not want a no nfo for /section/subsection
	 */
	@Override
	public void doCreateLink(DirectoryHandle targetDir) {
		boolean foundMatch = false;
		try {
			Set<DirectoryHandle> dirs = targetDir.getDirectoriesUnchecked();
			if (dirs.size() > 0) {
				for (DirectoryHandle dir : dirs) {
					if (dir.getPath().matches(getAddParentDir())) {
						foundMatch = true;
					}
				}
			} else {
				// No SubDirs found - Create Link
				foundMatch = true;
			}
		} catch (FileNotFoundException e) {
			// targetDir No Longer Exists
		}
		if (foundMatch) {
			createLink(targetDir,targetDir.getPath(),targetDir.getName());
		}
	}
	
	/*
	 * This just passes the target dir through to creating the Link
	 * No special setup is needed for this type.
	 */
	@Override
	public void doDeleteLink(DirectoryHandle targetDir) {
		deleteLink(targetDir,targetDir.getPath(),targetDir.getName());
	}

	/*
	 * This loops though the files, and checks to see if any end with .nfo
	 * If one does, it creates the link, if not, it deletes the link 
	 */
	@Override
	public void doFixLink(DirectoryHandle targetDir) {
		try {
			for (FileHandle file : targetDir.getFilesUnchecked()) {
				if (file.getName().toLowerCase().endsWith(".nfo")) {
					doDeleteLink(targetDir);
					return;
				}
			}
			doCreateLink(targetDir);
		} catch (FileNotFoundException e) {
			// No Files found - Ignore
		}
	}

}
