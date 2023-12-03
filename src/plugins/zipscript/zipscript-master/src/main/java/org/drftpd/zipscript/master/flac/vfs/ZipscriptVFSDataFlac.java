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
        try {
            if (isFLACInfoValid(getFlacInfoFromInode(_inode))) {
                return getFlacInfoFromInode(_inode);
            }
        } catch (KeyNotFoundException e1) {
            logger.debug("No FLACINFO registered for inode - {}", _inode);
        }

        // There is no existing flacinfo so we need to retrieve it and set it
        if (_inode instanceof DirectoryHandle) {
            // Find the info for the first flac file we come across and use that
            DirectoryHandle dir = (DirectoryHandle) _inode;
            FlacInfo flacinfo = null;
            for (FileHandle file : dir.getFilesUnchecked()) {
                if (file.getName().toLowerCase().endsWith(".flac") && file.getSize() > 0 && file.getXfertime() != -1) {
                    RemoteSlave rslave = file.getASlaveForFunction();
                    String index;
                    try {
                        index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
                        flacinfo = fetchFlacInfoFromIndex(rslave, index);
                        if (isFLACInfoValid(flacinfo)) {
                            break;
                        }
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying, try next file
                    } catch (RemoteIOException e) {
                        // continue, the next flac might work
                    }
                }
            }

            // We wait potentially very long above for flac info and we could have gotten flacinfo by now, so check that
            try {
                if (isFLACInfoValid(getFlacInfoFromInode(_inode))) {
                    return getFlacInfoFromInode(_inode);
                }
            } catch (KeyNotFoundException e1) {
                logger.debug("No FLACINFO registered for inode (2) - {}", _inode);
            }

            if (flacinfo != null) {
                dir.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
                return flacinfo;
            }
            throw new FileNotFoundException("No usable flac files found in directory");
        } else if (_inode instanceof FileHandle) {
            FileHandle file = (FileHandle) _inode;

            // Get Parent (directory)
            DirectoryHandle dir = file.getParent();

            FlacInfo flacinfo = null;
            if (file.getSize() > 0 && file.getXfertime() != -1) {
                for (int i = 0; i < 2; i++) { // TODO: Changed this from 5 to 2, why do we retry?
                    RemoteSlave rslave = file.getASlaveForFunction();
                    String index;
                    try {
                        index = getFlacIssuer().issueFlacFileToSlave(rslave, file.getPath());
                        flacinfo = fetchFlacInfoFromIndex(rslave, index);
                        if (isFLACInfoValid(flacinfo)) {
                            break;
                        }

                        flacinfo = null;
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying, continue
                    } catch (RemoteIOException e) {
                        throw new IOException(e.getMessage());
                    }
                }
            }

            // We wait potentially very long above for flac info and we could have gotten flacinfo by now, so check that
            try {
                if (isFLACInfoValid(getFlacInfoFromInode(_inode))) {
                    return getFlacInfoFromInode(_inode);
                }
            } catch (KeyNotFoundException e1) {
                logger.debug("No FLACINFO registered for inode (2) - {}", _inode);
            }

            if (flacinfo != null) {
                _setDir = true;

                // Update flacinfo on the file and parent (dir) inode
                dir.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
                _inode.addPluginMetaData(FlacInfo.FLACINFO, flacinfo);
                return flacinfo;
            }

            throw new FileNotFoundException("Unable to obtain info for flac file");
        }

        throw new IllegalArgumentException("Inode type other than directory or file passed in");
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
