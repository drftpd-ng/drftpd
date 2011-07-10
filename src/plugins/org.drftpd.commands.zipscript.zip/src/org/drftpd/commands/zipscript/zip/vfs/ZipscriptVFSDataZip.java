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
package org.drftpd.commands.zipscript.zip.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.drftpd.GlobalContext;
import org.drftpd.commands.zipscript.zip.ZipTools;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.protocol.zipscript.zip.common.async.AsyncResponseDizInfo;
import org.drftpd.protocol.zipscript.zip.master.ZipscriptZipIssuer;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataZip {

	private DirectoryHandle _dir;

	public ZipscriptVFSDataZip(DirectoryHandle dir) {
		_dir = dir;
	}

	public DizInfo getDizInfo() throws IOException, FileNotFoundException, NoAvailableSlaveException {
		try {
			return getDizInfoFromInode(_dir);
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		}
		// There is no existing dizinfo so we need to retrieve it and set it
		// Find the info for the first zip file we come across and use that
		DizInfo dizInfo = null;
		for (FileHandle file : _dir.getFilesUnchecked()) {
			if (file.getName().toLowerCase().endsWith(".zip") && file.getSize() > 0 && file.getXfertime() != -1) {
				RemoteSlave rslave = file.getASlaveForFunction();
				String index;
				try {
					index = getZipIssuer().issueZipDizInfoToSlave(rslave, file.getPath());
					dizInfo = fetchDizInfoFromIndex(rslave, index);
				} catch (SlaveUnavailableException e) {
					// okay, it went offline while trying, try next file
				} catch (RemoteIOException e) {
					// continue, the next zip might work
				}
				if (dizInfo != null && dizInfo.isValid()) {
					break;
				}
			}
		}
		if (dizInfo != null) {
			if (dizInfo.isValid()) {
				_dir.addPluginMetaData(DizInfo.DIZINFO, dizInfo);
				return dizInfo;
			}
		}
		throw new FileNotFoundException("No usable zip files found in directory");
	}

	public DizStatus getDizStatus() throws IOException, FileNotFoundException, NoAvailableSlaveException {
		return ZipTools.getDizStatus(getDizInfo(), _dir);
	}

	private DizInfo getDizInfoFromInode(InodeHandle vfsInodeHandle) throws FileNotFoundException, KeyNotFoundException {
		return vfsInodeHandle.getPluginMetaData(DizInfo.DIZINFO);
	}

	private DizInfo fetchDizInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseDizInfo) rslave.fetchResponse(index)).getDizInfo();
	}

	public static ZipscriptZipIssuer getZipIssuer() {
		return (ZipscriptZipIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptZipIssuer.class);
	}
}
