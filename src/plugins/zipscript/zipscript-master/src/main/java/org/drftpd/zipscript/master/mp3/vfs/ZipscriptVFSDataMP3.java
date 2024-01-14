/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.zipscript.master.mp3.vfs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.zipscript.common.mp3.AsyncResponseMP3Info;
import org.drftpd.zipscript.common.mp3.ID3Tag;
import org.drftpd.zipscript.common.mp3.MP3Info;
import org.drftpd.zipscript.master.mp3.ZipscriptMP3Issuer;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataMP3 {

    private static final Logger logger = LogManager.getLogger(ZipscriptVFSDataMP3.class);

    private final InodeHandle _inode;
    private boolean _setDir;

    public ZipscriptVFSDataMP3(InodeHandle inode) {
        _inode = inode;
        _setDir = false;
    }

    private boolean isMP3InfoValid(MP3Info mp3info) {
        ID3Tag id3 = mp3info.getID3Tag();
        if (id3 != null && id3.getGenre().length() > 0 && id3.getYear().length() > 0) {
            return true;
        }
        return false;
    }

    public MP3Info getMP3Info() throws IOException, NoAvailableSlaveException {
        try {
            if (isMP3InfoValid(getMP3InfoFromInode(_inode))) {
                return getMP3InfoFromInode(_inode);
            }
        } catch (KeyNotFoundException ignore1) {}

        // There is no existing mp3info so we need to retrieve it and set it
        MP3Info mp3info = null;
        DirectoryHandle dir = null;

        if (_inode instanceof DirectoryHandle) {
            // Find the info for the first mp3 file we come across and use that
            dir = (DirectoryHandle) _inode;

            for (FileHandle file : dir.getFilesUnchecked()) {
                // We only care for files
                if (!file.isFile()) {
                    continue;
                }
                // Only care for files ending in .mp3
                if (!file.getName().toLowerCase().endsWith(".mp3")) {
                    continue;
                }
                // Ignore any files that are not complete yet
                if (file.isUploading()) {
                    continue;
                }

                if (file.getSize() > 0 && file.getXfertime() != -1) {
                    try {
                        RemoteSlave rslave = file.getASlaveForFunction();
                        logger.debug("Trying to retrieve MP3INFO from slave {} for file {}", rslave, file);
                        String index = getMP3Issuer().issueMP3FileToSlave(rslave, file.getPath());
                        mp3info = fetchMP3InfoFromIndex(rslave, index);
                    } catch (NoAvailableSlaveException e) {
                        // Not an issue, file was available but slave dropped, try next file
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying, try next file
                    } catch (RemoteIOException e) {
                        // continue, the next mp3 might work
                    }
                }

                if (mp3info != null && isMP3InfoValid(mp3info)) {
                    break;
                }
            }
        } else if (_inode instanceof FileHandle) {
            FileHandle file = (FileHandle) _inode;

            // Get Parent (directory)
            dir = file.getParent();

            if (!file.isUploading() && file.getSize() > 0 && file.getXfertime() != -1) {
                try {
                    RemoteSlave rslave = file.getASlaveForFunction();
                    logger.debug("Trying to retrieve MP3INFO from slave {} for file {}", rslave, file);
                    String index = getMP3Issuer().issueMP3FileToSlave(rslave, file.getPath());
                    mp3info = fetchMP3InfoFromIndex(rslave, index);
                } catch (SlaveUnavailableException | RemoteIOException e) {
                    throw new IOException(e.getMessage());
                }
            }

            if (mp3info != null && isMP3InfoValid(mp3info)) {
                _setDir = true;
            }
        } else {
            throw new IllegalArgumentException("Unsupported Inode passed for MP3INFO extraction");
        }

        // We wait potentially very long above for mp3 info and we could have gotten mp3info by now, so check that
        try {
            if (isMP3InfoValid(getMP3InfoFromInode(_inode))) {
                return getMP3InfoFromInode(_inode);
            }
        } catch (KeyNotFoundException ignore2) {}

        if (mp3info != null) {
            dir.addPluginMetaData(MP3Info.MP3INFO, mp3info);
            if (_inode instanceof FileHandle) {
                _inode.addPluginMetaData(MP3Info.MP3INFO, mp3info);
            }
            return mp3info;
        }
        if (_inode instanceof DirectoryHandle) {
            throw new FileNotFoundException("No usable mp3 files found in directory");
        }
        throw new FileNotFoundException("Unable to obtain info for MP3 file");
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
