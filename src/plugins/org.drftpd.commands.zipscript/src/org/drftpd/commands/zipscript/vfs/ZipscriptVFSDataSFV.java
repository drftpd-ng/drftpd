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

import java.io.FileNotFoundException;
import java.io.IOException;

import org.drftpd.GlobalContext;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.ZipscriptIssuer;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.async.AsyncResponseSFVInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.CaseInsensitiveTreeMap;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ObjectNotValidException;

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
			_dir.removeKey(SFVInfo.SFV);
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		} catch (ObjectNotValidException e) {
			// the previous sfv file is no longer of type VirtualFileSystemFile
			_dir.removeKey(SFVInfo.SFV);
		}

		for (FileHandle file : _dir.getFilesUnchecked()) {
			if (file.getName().toLowerCase().endsWith(".sfv")) {
				for (int i = 0; i < 5; i++) {
					SFVInfo info = null;
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
					_dir.addKey(SFVInfo.SFV, info);
					return info;
				}
				throw new SlaveUnavailableException("No slave for SFV file available");
			}
		}
		throw new FileNotFoundException("No SFV file in directory");
	}
	
	public SFVStatus getSFVStatus() throws IOException, FileNotFoundException, NoAvailableSlaveException, SlaveUnavailableException {
		int offline = 0;
		int present = 0;
		SFVInfo sfvInfo = getSFVInfo();
		CaseInsensitiveTreeMap<String, Long> sfvEntries = sfvInfo.getEntries();
		for (FileHandle file : _dir.getFilesUnchecked()) {
			if (file.isFile() && sfvEntries.containsKey(file.getName())) {
				if (!file.isUploading()) {
					present++;
				}
				if (!file.isAvailable()) {
					offline++;
				}
			}
		}
		return new SFVStatus(sfvEntries.size(), offline, present);
	}
	
	private SFVInfo getSFVInfoFromInode(DirectoryHandle vfsDirHandle) throws FileNotFoundException, KeyNotFoundException {
		return (SFVInfo) vfsDirHandle.getKey(SFVInfo.SFV);
	}
	
	private SFVInfo fetchSFVInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseSFVInfo) rslave.fetchResponse(index)).getSFV();
	}
	
	private ZipscriptIssuer getSFVIssuer() {
		return (ZipscriptIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptIssuer.class);
	}
}
