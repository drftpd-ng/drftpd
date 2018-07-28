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
package org.drftpd.commands.zipscript.flac.vfs;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;
import org.drftpd.protocol.zipscript.flac.common.async.AsyncResponseFlacInfo;
import org.drftpd.protocol.zipscript.flac.master.ZipscriptFlacIssuer;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author norox
 */
public class ZipscriptVFSDataFlac {

	private InodeHandle _inode;

	private boolean _setDir;

	public ZipscriptVFSDataFlac(InodeHandle inode) {
		_inode = inode;
		_setDir = false;
	}

	public FlacInfo getFlacInfo() throws IOException, FileNotFoundException, NoAvailableSlaveException {
		try {
			FlacInfo flacinfo = getFlacInfoFromInode(_inode);
			VorbisTag vorbistag = flacinfo.getVorbisTag();
			if (vorbistag != null && vorbistag.getGenre().length() > 0 && vorbistag.getYear().length() > 0) {
				return flacinfo;
			}
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		}
		// There is no existing flacinfo so we need to retrieve it and set it
		if (_inode instanceof DirectoryHandle) {
			// Find the info for the first flac file we come across and use that
			DirectoryHandle dir = (DirectoryHandle) _inode;
			FlacInfo flacinfo = null;
			VorbisTag vorbistag = null;
			for (FileHandle file : dir.getFilesUnchecked()) {
				if (file.getName().toLowerCase().endsWith(".flac") && file.getSize() > 0 && file.getXfertime() != -1) {
					RemoteSlave rslave = file.getASlaveForFunction();
					String index;
					try {
						index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
						flacinfo = fetchFlacInfoFromIndex(rslave, index);
						vorbistag = flacinfo.getVorbisTag();
						if (vorbistag != null && vorbistag.getGenre().length() > 0 && vorbistag.getYear().length() > 0) {
							break;
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying, try next file
					} catch (RemoteIOException e) {
						// continue, the next flac might work
					}
				}
			}
			if (flacinfo != null) {
				dir.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
				return flacinfo;
			}
			throw new FileNotFoundException("No usable flac files found in directory");
		} else if (_inode instanceof FileHandle) {
			FileHandle file = (FileHandle) _inode;
			FlacInfo flacinfo = null;
			VorbisTag vorbistag = null;
			if (file.getSize() > 0 && file.getXfertime() != -1) {
				for (int i = 0; i < 5; i++) {
					RemoteSlave rslave = file.getASlaveForFunction();
					String index;
					try {
						index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
						flacinfo = fetchFlacInfoFromIndex(rslave, index);
						vorbistag = flacinfo.getVorbisTag();
						if (vorbistag != null && vorbistag.getGenre().length() > 0 && vorbistag.getYear().length() > 0) {
							break;
						} else {
							flacinfo = null;
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying, continue
                    } catch (RemoteIOException e) {
						throw new IOException(e.getMessage());
					}
				}
			}
			if (flacinfo == null) {
				throw new FileNotFoundException("Unable to obtain info for flac file");
			}

			// Update flacinfo on parent directory inode
			DirectoryHandle dir = file.getParent();
			try {
				getFlacInfoFromInode(dir);
			} catch (KeyNotFoundException e1) {
				_setDir = true;
				dir.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
			}
			
			// Update flacinfo on the file inode
			_inode.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
			return flacinfo;
		} else {
			throw new IllegalArgumentException("Inode type other than directory or file passed in");
		}
	}

	public boolean isFirst() {
		return _setDir;
	}

	private FlacInfo getFlacInfoFromInode(InodeHandle vfsInodeHandle) throws FileNotFoundException, KeyNotFoundException {
		return vfsInodeHandle.getPluginMetaData(FlacInfo.FLACINFO);
	}

	private FlacInfo fetchFlacInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseFlacInfo) rslave.fetchResponse(index)).getFlacInfo();
	}

	private ZipscriptFlacIssuer getFlacIssuer() {
		return (ZipscriptFlacIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptFlacIssuer.class);
	}
}
