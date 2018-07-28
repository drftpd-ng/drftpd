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
package org.drftpd.commands.zipscript.vfs;

import org.drftpd.GlobalContext;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.protocol.zipscript.common.async.AsyncResponseSFVInfo;
import org.drftpd.protocol.zipscript.master.ZipscriptIssuer;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataSFV {

	private DirectoryHandle _dir;

	public ZipscriptVFSDataSFV(DirectoryHandle dir) {
		_dir = dir;
	}

	public SFVInfo getSFVInfo() throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		try {
			SFVInfo sfvInfo = getSFVInfoFromInode(_dir);
			try {
				FileHandle sfvFile = _dir.getFileUnchecked(sfvInfo.getSFVFileName());
				if (sfvFile.exists()) {
					if (sfvFile.getCheckSum() == sfvInfo.getChecksum()) {
						// 	passed all tests
						return sfvInfo;
					}
				}
			} catch (FileNotFoundException e) {
				// just continue, it couldn't find the previous sfv file, the line below here will remove it
				// we will then continue to try to find a new one right afterward
			}
			_dir.removePluginMetaData(SFVInfo.SFVINFO);
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		} catch (ObjectNotValidException e) {
			// the previous sfv file is no longer of type VirtualFileSystemFile
			_dir.removePluginMetaData(SFVInfo.SFVINFO);
		}

		for (FileHandle file : _dir.getFilesUnchecked()) {
			if (file.getSize() > 0 && file.getXfertime() != -1 && file.getName().toLowerCase().endsWith(".sfv")) {
				for (int i = 0; i < 5; i++) {
					SFVInfo info;
					RemoteSlave rslave = file.getASlaveForFunction();
					String index;
					try {
						index = getSFVIssuer().issueSFVFileToSlave(rslave, file.getPath());
						info = fetchSFVInfoFromIndex(rslave, index);
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying, continue
						continue;
					} catch (RemoteIOException e) {
						throw new IOException(e.getMessage());
					}
					_dir.addPluginMetaData(SFVInfo.SFVINFO, info);
					return info;
				}
				throw new SlaveUnavailableException("No slave for SFV file available");
			}
		}
		throw new FileNotFoundException("No SFV file in directory");
	}
	
	public SFVStatus getSFVStatus() throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		return SFVTools.getSFVStatus(getSFVInfo(), _dir);
	}
	
	private SFVInfo getSFVInfoFromInode(DirectoryHandle vfsDirHandle) throws FileNotFoundException, KeyNotFoundException {
		return vfsDirHandle.getPluginMetaData(SFVInfo.SFVINFO);
	}
	
	private SFVInfo fetchSFVInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseSFVInfo) rslave.fetchResponse(index)).getSFV();
	}
	
	private ZipscriptIssuer getSFVIssuer() {
		return (ZipscriptIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptIssuer.class);
	}
}
