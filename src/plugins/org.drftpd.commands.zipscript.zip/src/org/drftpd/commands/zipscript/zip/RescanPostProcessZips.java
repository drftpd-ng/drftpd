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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.RescanPostProcessDirInterface;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.io.FileNotFoundException;

/**
 * @author djb61
 * @version $Id$
 */
public class RescanPostProcessZips extends ZipTools implements RescanPostProcessDirInterface {

	private static final Logger logger = LogManager.getLogger(RescanPostProcessZips.class);

	public void initialize(StandardCommandManager cManager) {
	}

	public void postProcessDir(CommandRequest workingDirReq, boolean quiet) {
		Session session = workingDirReq.getSession();
		DirectoryHandle dir = workingDirReq.getCurrentDirectory();
		try {
			for (FileHandle file : dir.getSortedFiles(
					session.getUserNull(workingDirReq.getUser()))) {
				if (file.getSize() > 0 && file.getName().endsWith(".zip")) {
					try {
						RemoteSlave rslave = file.getASlaveForFunction();
						String index = ZipscriptVFSDataZip.getZipIssuer().issueZipCRCToSlave(rslave, file.getPath());
						boolean ok = getZipIntegrityFromIndex(rslave, index);
						if (ok) {
							if (!quiet) {
								session.printOutput(200,file.getName() + " - Zip integrity check OK");
							}
						} else {
							session.printOutput(200,file.getName() + " - Zip integrity check failed, deleting file");
							try {
								file.deleteUnchecked();
							} catch (FileNotFoundException e) {
								// file disappeared, not a problem as we wanted it gone anyway
							}
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying
						session.printOutput(200,file.getName() + " - Slave went offline whilst checking zip integrity");
					} catch (RemoteIOException e) {
						session.printOutput(200,file.getName() + " - Slave encountered an error whilst checking zip integrity");
						logger.warn("Error encountered whilst checking zip integrity",e);
					} catch (NoAvailableSlaveException e) {
						session.printOutput(200,file.getName() + " - No available slave found to perform zip integrity check");
					}
				}
			}
		} catch (FileNotFoundException e) {
			session.printOutput(200,dir.getName() + " - Directory deleted  whilst checking integrity of zips");
		}
	}

}
