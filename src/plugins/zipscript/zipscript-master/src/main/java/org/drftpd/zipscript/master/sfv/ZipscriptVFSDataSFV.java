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
package org.drftpd.zipscript.master.sfv;

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
import org.drftpd.master.vfs.ObjectNotValidException;
import org.drftpd.zipscript.common.sfv.AsyncResponseSFVInfo;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.common.sfv.SFVStatus;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptVFSDataSFV {

    private static final Logger logger = LogManager.getLogger(ZipscriptVFSDataSFV.class);

    private final DirectoryHandle _dir;

    public ZipscriptVFSDataSFV(DirectoryHandle dir) {
        _dir = dir;
    }

    public SFVInfo getSFVInfo() throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        try {
            SFVInfo sfvInfo = getSFVInfoFromInode(_dir);
            try {
                FileHandle sfvFile = _dir.getFileUnchecked(sfvInfo.getSFVFileName());
                if (sfvFile.exists()) {
                    if (sfvFile.getCheckSum() == sfvInfo.getChecksum()) {
                        // passed all tests
                        return sfvInfo;
                    }
                }
            } catch (FileNotFoundException e) {
                // just continue, it couldn't find the previous sfv file, the line below here will remove it
                // we will then continue to try to find a new one right afterward
            }
            logger.warn("Removing SFVINFO as it failed checks - {}", _dir);
            _dir.removePluginMetaData(SFVInfo.SFVINFO);
        } catch (KeyNotFoundException e1) {
            logger.debug("No SFVINFO registered for inode - {}", _dir);
        } catch (ObjectNotValidException e) {
            // the previous sfv file is no longer of type VirtualFileSystemFile
            logger.warn("Removing SFVINFO as it is no longer valid - {}", _dir);
            _dir.removePluginMetaData(SFVInfo.SFVINFO);
        }

        for (FileHandle file : _dir.getFilesUnchecked()) {
            if (file.getSize() > 0 && file.getXfertime() != -1 && file.getName().toLowerCase().endsWith(".sfv")) {
                SFVInfo info = null;

                // TODO: This used to be 5, changed it to 2 in the hopes we speed up this process
                // Do not understand why we retry here?
                for (int i = 0; i < 2; i++) {
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
                }

                // Try one more time to load from vfs
                // We assume the cleanup above is not needed here (again)
                try {
                    return getSFVInfoFromInode(_dir);
                } catch (KeyNotFoundException e1) {
                    logger.debug("No SFVINFO registered for inode (2) - {}", _dir);
                }

                if (info != null) {
                    _dir.addPluginMetaData(SFVInfo.SFVINFO, info);
                    return info;
                }

                throw new SlaveUnavailableException("No slave for SFV file available");
            }
        }
        throw new FileNotFoundException("No SFV file in directory");
    }

    public SFVStatus getSFVStatus() throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
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
