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
package org.drftpd.plugins.linkmanager.types.sfvincomplete;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author CyBeR
 * @version $Id: SFVIncomplete.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class SFVIncomplete extends LinkType {

	public SFVIncomplete(Properties p, int confnum, String type) {
		super(p, confnum, type);
	}

	/*
	 * This just passes the target dir through to creating the Link
	 * No special setup is needed for this type.
	 */
	@Override
	public void doCreateLink(DirectoryHandle targetDir) {
		createLink(targetDir,targetDir.getPath(),targetDir.getName());
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
	 * Checks to see if the release is finished.
	 * If it is, it removes the link, and if it isn't
	 * it creates the link.
	 */
	@Override
	public void doFixLink(DirectoryHandle targetDir) {
        ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(targetDir);
		try {
			if (sfvData.getSFVStatus().isFinished()) {
				doDeleteLink(targetDir);
				return;
			}
			doCreateLink(targetDir);
		} catch (FileNotFoundException e) {
			// no .sfv found - ignore
		} catch (IOException e) {
			// can't read .sfv - ignore
		} catch (NoAvailableSlaveException e) {
			// no slaves available for .sfv - ignore
		} catch (SlaveUnavailableException e) {
			// no slaves available for .sfv - ignore
		}
	}
}
