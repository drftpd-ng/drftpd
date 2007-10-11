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

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.ZipscriptIssuer;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.protocol.zipscript.common.async.AsyncResponseSFVInfo;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.CaseInsensitiveTreeMap;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystemDirectory;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataSFV {

	private DirectoryHandle _dir;

	public ZipscriptVFSDataSFV(DirectoryHandle dir) {
		_dir = dir;
	}

	public SFVInfo getSFVInfo() throws FileNotFoundException, NoAvailableSlaveException {
		try {
			SFVInfo sfvInfo = getSFVInfoFromInode(_dir.getInode());
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
			removeSFVInfo();
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		} catch (ObjectNotValidException e) {
			// the previous sfv file is not longer of type VirtualFileSystemFile
			removeSFVInfo();
		}

		for (FileHandle file : _dir.getFilesUnchecked()) {
			if (file.getName().toLowerCase().endsWith(".sfv")) {
				while (true) {
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
						// okay, it had an error while trying, let's try again
						continue;
					}
					setSFVInfo(info);
					return info;
				}
			}
		}
		throw new FileNotFoundException("No SFV file in directory");
	}
	
	public SFVStatus getSFVStatus() throws FileNotFoundException, NoAvailableSlaveException {
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

	private void setSFVInfo(SFVInfo sfvFile) throws FileNotFoundException {
		_dir.getInode().getKeyedMap().setObject(SFVInfo.SFV, sfvFile);
		_dir.getInode().commit();
	}
	
	private SFVInfo getSFVInfoFromInode(VirtualFileSystemDirectory vfsDir) throws KeyNotFoundException {
		return (SFVInfo) vfsDir.getKeyedMap().getObject(SFVInfo.SFV);
	}

	public void removeSFVInfo() throws FileNotFoundException {
		_dir.getInode().getKeyedMap().remove(SFVInfo.SFV);
		_dir.getInode().commit();
	}
	
	public static SFVInfo fetchSFVInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseSFVInfo) rslave.fetchResponse(index)).getSFV();
	}
	
	public ZipscriptIssuer getSFVIssuer() {
		return (ZipscriptIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptIssuer.class);
	}
}
