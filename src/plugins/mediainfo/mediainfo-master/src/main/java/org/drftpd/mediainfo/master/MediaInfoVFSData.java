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
package org.drftpd.mediainfo.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.mediainfo.common.AsyncResponseMediaInfo;
import org.drftpd.mediainfo.common.MediaInfo;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author scitz0
 */
public class MediaInfoVFSData {

    private static final Logger logger = LogManager.getLogger(MediaInfoVFSData.class);

    private final FileHandle _file;

    public MediaInfoVFSData(FileHandle file) {
        _file = file;
    }

    private boolean isMediaInfoValid(MediaInfo mediaInfo) {
        if (mediaInfo == null) {
            return false;
        }
        try {
            if (_file.exists()) {
                if (_file.getCheckSum() == mediaInfo.getChecksum()) {
                    return true;
                }
            }
        } catch (FileNotFoundException | NoAvailableSlaveException e) {
            // Fine, file no longer exists or issue with slave
        }
        return false;
    }

    public MediaInfo getMediaInfo() throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        try {
            MediaInfo inodeMediaInfo = null;
            try {
                inodeMediaInfo = getMediaInfoFromInode(_file);
            } catch (KeyNotFoundException ignore1) {
                logger.debug("No MediaInfo found in inode for file: {}", _file);
            }
            if (isMediaInfoValid(inodeMediaInfo)) {
                logger.debug("Valid MediaInfo found in inode for file: {}", _file);
                return inodeMediaInfo;
            } else {
                logger.debug("No valid MediaInfo in inode for file: {} (inodeMediaInfo: {})", _file, inodeMediaInfo);
            }
            // Make sure there is no mediainfo registered
            _file.removePluginMetaData(MediaInfo.MEDIAINFO);
        } catch (Exception e) {
            logger.warn("Exception while checking inode MediaInfo for file {}: {}", _file, e.getMessage());
        }

        MediaInfo mediainfo = null;

        // Make sure the file is not still uploading, has size and transfer time
        if (_file.isUploading()) {
            logger.debug("File is still uploading: {}", _file);
        }
        if (_file.getSize() <= 0) {
            logger.debug("File size is zero or negative: {} (size: {})", _file, _file.getSize());
        }
        if (_file.getXfertime() == -1) {
            logger.debug("File xfertime is -1: {}", _file);
        }
        if (!_file.isUploading() && _file.getSize() > 0 && _file.getXfertime() != -1) {
            try {
                RemoteSlave rslave = _file.getASlaveForFunction();
                logger.debug("Trying to retrieve MEDIAINFO from slave {} for file {}", rslave, _file);
                String index = getMediaInfoIssuer().issueMediaFileToSlave(rslave, _file.getPath());
                mediainfo = fetchMediaInfoFromIndex(rslave, index);
                logger.debug("Fetched MediaInfo from slave {} for file {}: {}", rslave, _file, mediainfo);
            } catch (NoAvailableSlaveException e) {
                // Not an issue, file was available but slave dropped
                logger.warn("No available slave for file {}: {}", _file, e.getMessage());
            } catch (SlaveUnavailableException e) {
                // okay, it went offline while trying, try next file
                logger.warn("Slave unavailable while fetching MediaInfo for file {}: {}", _file, e.getMessage());
            } catch (RemoteIOException e) {
                logger.error("RemoteIOException while fetching MediaInfo for file {}: {}", _file, e.getMessage());
                throw new IOException(e.getMessage());
            } catch (Exception e) {
                logger.error("Unexpected exception while fetching MediaInfo for file {}: {}", _file, e.getMessage());
            }

            if (mediainfo != null) {
                // Check if the sample is not OK (file was invalid and deleted on slave)
                if (!mediainfo.getSampleOk()) {
                    logger.warn("MediaInfo indicates file {} is invalid (sampleOk=false). File should have been deleted on slave.", _file);
                    // Try to remove the file from the VFS if it still exists
                    try {
                        if (_file.exists()) {
                            _file.deleteUnchecked();
                            logger.warn("File {} removed from VFS after invalid MediaInfo.", _file);
                        }
                    } catch (Exception e) {
                        logger.error("Failed to remove file {} from VFS after invalid MediaInfo: {}", _file, e.getMessage());
                    }
                    throw new SlaveUnavailableException("File was invalid and has been deleted: "+_file);
                }
                
                logger.debug("Fetched valid MediaInfo from slave for file: {}", _file);
                // Add data to mediainfo
                mediainfo.setChecksum(_file.getCheckSum());
                mediainfo.setFileName(_file.getName());

                // Write the mediainfo metadata for this file as we got it here
                _file.addPluginMetaData(MediaInfo.MEDIAINFO, mediainfo);

                return mediainfo;
            } else {
                logger.debug("Fetched MediaInfo from slave is null for file: {}", _file);
            }
        }

        // If we get here we did not get valid mediainfo
        logger.error("No valid MediaInfo could be retrieved for file: {}. Throwing SlaveUnavailableException.", _file);
        throw new SlaveUnavailableException("No slave for media file available");
    }

    private MediaInfo getMediaInfoFromInode(FileHandle vfsFileHandle) throws FileNotFoundException, KeyNotFoundException {
        return vfsFileHandle.getPluginMetaData(MediaInfo.MEDIAINFO);
    }

    private MediaInfo fetchMediaInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
        return ((AsyncResponseMediaInfo) rslave.fetchResponse(index)).getMediaInfo();
    }

    private MediaInfoIssuer getMediaInfoIssuer() {
        return (MediaInfoIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(MediaInfoIssuer.class);
    }
}
