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
package org.drftpd.zipscript.master.flac.vfs;

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
import org.drftpd.zipscript.common.flac.AsyncResponseFlacInfo;
import org.drftpd.zipscript.common.flac.FlacInfo;
import org.drftpd.zipscript.common.flac.VorbisTag;
import org.drftpd.zipscript.master.flac.ZipscriptFlacIssuer;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author norox
 */
public class ZipscriptVFSDataFlac {

    private static final Logger logger = LogManager.getLogger(ZipscriptVFSDataFlac.class);

    private final InodeHandle _inode;
    private boolean _setDir;

    public ZipscriptVFSDataFlac(InodeHandle inode) {
        _inode = inode;
        _setDir = false;
    }

    private boolean isFLACInfoValid(FlacInfo flacinfo) {
        VorbisTag vorbistag = flacinfo.getVorbisTag();
        if (vorbistag != null && vorbistag.getGenre().length() > 0 && vorbistag.getYear().length() > 0) {
            return true;
        }
        return false;
    }

    public FlacInfo getFlacInfo() throws IOException, NoAvailableSlaveException {
        // If this is a filehandle, we want mp3info for ALL files so it is OK if missing
        // If this is a dirhandle it means no file was processed yet which is also OK
        try {
            if (isFLACInfoValid(getFlacInfoFromInode(_inode))) {
                return getFlacInfoFromInode(_inode);
            }
        } catch (KeyNotFoundException ignore1) {}

        FlacInfo flacinfo = null;
        DirectoryHandle dir = null;

        // There is no existing flacinfo so we need to retrieve it and set it
        if (_inode instanceof DirectoryHandle) {
            // Find the info for the first flac file we come across and use that
            dir = (DirectoryHandle) _inode;

            for (FileHandle file : dir.getFilesUnchecked()) {
                // We only care for files
                if (!file.isFile()) {
                    continue;
                }
                // Only care for files ending in .flac
                if (!file.getName().toLowerCase().endsWith(".flac")) {
                    continue;
                }
                // Ignore any files that are not complete yet
                if (file.isUploading()) {
                    continue;
                }

                if (file.getSize() > 0 && file.getXfertime() != -1) {
                    try {
                        RemoteSlave rslave = file.getASlaveForFunction();
                        logger.debug("Trying to retrieve FLACINFO from slave {} for file {}", rslave, file);
                        String index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
                        flacinfo = fetchFlacInfoFromIndex(rslave, index);
                    } catch (NoAvailableSlaveException e) {
                        // Not an issue, file was available but slave dropped, try next file
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying, try next file
                    } catch (RemoteIOException e) {
                        // continue, the next flac might work
                    }
                }

                if (flacinfo != null && isFLACInfoValid(flacinfo)) {
                    // Write the flacinfo metadata for this file as we got it here
                    // If the directory does not have it yet we handle that below
                    file.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
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
                    logger.debug("Trying to retrieve FLACINFO from slave {} for file {}", rslave, file);
                    String index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
                    flacinfo = fetchFlacInfoFromIndex(rslave, index);
                } catch (SlaveUnavailableException | RemoteIOException e) {
                    throw new IOException(e.getMessage());
                }
            }

            // No point in continuing if the flacinfo we got is not valid
            if (flacinfo != null && isFLACInfoValid(flacinfo)) {
                throw new FileNotFoundException("Unable to obtain info for FLAC file");
            }

            // Write the flacinfo metadata for this file as we got it here
            // If the directory does not have it yet we handle that below
            file.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
        } else {
            throw new IllegalArgumentException("Unsupported Inode passed for FLACINFO extraction");
        }

        // We check the directory here and return dir mp3info if it exists.
        // If it does not exist we set it and mark it as first (_setDir = true)
        try {
            if (isFLACInfoValid(getFlacInfoFromInode(dir))) {
                return getFlacInfoFromInode(dir);
            }
        } catch (KeyNotFoundException ignore2) {}

        if (flacinfo != null) {
            _setDir = true;
            dir.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
            return flacinfo;
        }
        if (_inode instanceof DirectoryHandle) {
            throw new FileNotFoundException("No usable flac files found in directory");
        }

        // We should not end up here, but safety net just in case
        throw new FileNotFoundException("Unable to obtain info for FLAC file");
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
