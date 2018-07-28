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
package org.drftpd.commands.zipscript.mp3.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.mp3.common.MP3Info;
import org.drftpd.protocol.zipscript.mp3.common.ID3Tag;
import org.drftpd.protocol.zipscript.mp3.common.async.AsyncResponseMP3Info;
import org.drftpd.protocol.zipscript.mp3.master.ZipscriptMP3Issuer;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataMP3 {

	private InodeHandle _inode;

	private boolean _setDir;

	public ZipscriptVFSDataMP3(InodeHandle inode) {
		_inode = inode;
		_setDir = false;
	}

	public MP3Info getMP3Info() throws IOException, NoAvailableSlaveException {
		try {
			MP3Info mp3info = getMP3InfoFromInode(_inode);
			ID3Tag id3 = mp3info.getID3Tag();
			if (id3 != null && id3.getGenre().length() > 0 && id3.getYear().length() > 0) {
				return mp3info;
			}
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		}
		// There is no existing mp3info so we need to retrieve it and set it
		if (_inode instanceof DirectoryHandle) {
			// Find the info for the first mp3 file we come across and use that
			DirectoryHandle dir = (DirectoryHandle) _inode;
			MP3Info mp3info = null;
			ID3Tag id3 = null;
			for (FileHandle file : dir.getFilesUnchecked()) {
				if (file.getName().toLowerCase().endsWith(".mp3") && file.getSize() > 0 && file.getXfertime() != -1) {
					RemoteSlave rslave = file.getASlaveForFunction();
					String index;
					try {
						index = getMP3Issuer().issueMP3FileToSlave(rslave, file.getPath());
						mp3info = fetchMP3InfoFromIndex(rslave, index);
						id3 = mp3info.getID3Tag();
						if (id3 != null && id3.getGenre().length() > 0 && id3.getYear().length() > 0) {
							break;
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying, try next file
					} catch (RemoteIOException e) {
						// continue, the next mp3 might work
					}
				}
			}
			if (mp3info != null) {
				dir.addPluginMetaData(MP3Info.MP3INFO, mp3info);
				return mp3info;
			}
			throw new FileNotFoundException("No usable mp3 files found in directory");
		} else if (_inode instanceof FileHandle) {
			FileHandle file = (FileHandle) _inode;
			MP3Info mp3info = null;
			ID3Tag id3 = null;
			if (file.getSize() > 0 && file.getXfertime() != -1) {
				for (int i = 0; i < 5; i++) {
					RemoteSlave rslave = file.getASlaveForFunction();
					String index;
					try {
						index = getMP3Issuer().issueMP3FileToSlave(rslave, file.getPath());
						mp3info = fetchMP3InfoFromIndex(rslave, index);
						id3 = mp3info.getID3Tag();
						if (id3 != null && id3.getGenre().length() > 0 && id3.getYear().length() > 0) {
							break;
						} else {
							mp3info = null;
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying, continue
                    } catch (RemoteIOException e) {
						throw new IOException(e.getMessage());
					}
				}
			}
			if (mp3info == null) {
				throw new FileNotFoundException("Unable to obtain info for MP3 file");
			}

			// Update mp3info on parent directory inode
			DirectoryHandle dir = file.getParent();
			try {
				getMP3InfoFromInode(dir);
			} catch (KeyNotFoundException e1) {
				_setDir = true;
				dir.addPluginMetaData(MP3Info.MP3INFO, mp3info);
			}
			
			// Update mp3info on the file inode
			_inode.addPluginMetaData(MP3Info.MP3INFO, mp3info);
			return mp3info;
		} else {
			throw new IllegalArgumentException("Inode type other than directory or file passed in");
		}
	}

	public boolean isFirst() {
		return _setDir;
	}

	private MP3Info getMP3InfoFromInode(InodeHandle vfsInodeHandle) throws FileNotFoundException, KeyNotFoundException {
		return vfsInodeHandle.getPluginMetaData(MP3Info.MP3INFO);
	}

	private MP3Info fetchMP3InfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseMP3Info) rslave.fetchResponse(index)).getMP3Info();
	}

	private ZipscriptMP3Issuer getMP3Issuer() {
		return (ZipscriptMP3Issuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptMP3Issuer.class);
	}
}
