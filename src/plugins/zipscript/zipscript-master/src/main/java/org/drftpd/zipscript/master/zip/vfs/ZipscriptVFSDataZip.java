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
package org.drftpd.zipscript.master.zip.vfs;

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
import org.drftpd.zipscript.common.zip.AsyncResponseDizInfo;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.zip.ZipTools;
import org.drftpd.zipscript.master.zip.ZipscriptZipIssuer;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataZip {

    private static final Logger logger = LogManager.getLogger(ZipscriptVFSDataZip.class);
    private final DirectoryHandle _dir;

    public ZipscriptVFSDataZip(DirectoryHandle dir) {
        _dir = dir;
    }

    public static ZipscriptZipIssuer getZipIssuer() {
        return (ZipscriptZipIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(ZipscriptZipIssuer.class);
    }

    public DizInfo getDizInfo() throws IOException, NoAvailableSlaveException {
        try {
            return getDizInfoFromInode(_dir);
        } catch (KeyNotFoundException ignore1) {}

        // There is no existing dizinfo so we need to retrieve it and set it
        // Find the info for the first zip file we come across and use that
        DizInfo dizInfo = null;
        for (FileHandle file : _dir.getFilesUnchecked()) {
            // We only care for files
            if (!file.isFile()) {
                continue;
            }
            // Only care for files ending in .zip
            if (!file.getName().toLowerCase().endsWith(".zip")) {
                continue;
            }
            // Ignore any files that are not complete yet
            if (file.isUploading()) {
                continue;
            }

            // If we get here we have a .diz file that should be complete and we can get valid dizinfo
            if (file.getSize() > 0 && file.getXfertime() != -1) {
                try {
                    RemoteSlave rslave = file.getASlaveForFunction();
                    logger.debug("Trying to retrieve DIZINFO from slave {} for file {}", rslave, file);
                    String index = getZipIssuer().issueZipDizInfoToSlave(rslave, file.getPath());
                    dizInfo = fetchDizInfoFromIndex(rslave, index);
                } catch (NoAvailableSlaveException e) {
                    // Not an issue, file was available but slave dropped, try next file
                } catch (SlaveUnavailableException e) {
                    // okay, it went offline while trying, try next file
                } catch (RemoteIOException e) {
                    // continue, the next zip might work
                }

                // If we have valid dizinfo stop the loop
                if (dizInfo != null && dizInfo.isValid()) {
                    break;
                }
            }
        }

        // We wait potentially very long above for diz info and we could have gotten dizinfo by now, so check that
        try {
            return getDizInfoFromInode(_dir);
        } catch (KeyNotFoundException ignore2) {}

        // We still have no valid DIZINFO on inode, so check if we have valid and register it!
        if (dizInfo != null) {
            if (dizInfo.isValid()) {
                logger.debug("Storing DIZINFO on inode in VFS as it is valid - {}", _dir);
                _dir.addPluginMetaData(DizInfo.DIZINFO, dizInfo);
                return dizInfo;
            } else {
                logger.warn("Inode has no DIZINFO and the one we got from slave(s) is not valid - {}", _dir);
            }
        }
        throw new FileNotFoundException("No usable zip files found in directory");
    }

    public DizStatus getDizStatus() throws IOException, NoAvailableSlaveException {
        return ZipTools.getDizStatus(getDizInfo(), _dir);
    }

    private DizInfo getDizInfoFromInode(InodeHandle vfsInodeHandle) throws FileNotFoundException, KeyNotFoundException {
        return vfsInodeHandle.getPluginMetaData(DizInfo.DIZINFO);
    }

    private DizInfo fetchDizInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
        return ((AsyncResponseDizInfo) rslave.fetchResponse(index)).getDizInfo();
    }
}
