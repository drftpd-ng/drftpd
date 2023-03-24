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
package org.drftpd.imdb.master.vfs;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.imdb.common.AsyncResponseIMDBInfo;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.imdb.master.IMDBIssuer;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author lh
 */
public class IMDBVFSDataNFO {
    private final DirectoryHandle _dir;

    public IMDBVFSDataNFO(DirectoryHandle dir) {
        _dir = dir;
    }

    public IMDBInfo getIMDBInfoFromCache() {
        try {
            return getIMDBInfoFromInode(_dir);
        } catch (KeyNotFoundException e1) {
            // No IMDB info found, return null
        } catch (FileNotFoundException e2) {
            // Hmm...
        }
        return null;
    }

    public IMDBInfo getIMDBInfo() throws IOException, NoAvailableSlaveException, SlaveUnavailableException, FileNotFoundException {
        try {
            IMDBInfo imdbInfo = getIMDBInfoFromInode(_dir);
            try {
                FileHandle nfoFile = _dir.getFileUnchecked(imdbInfo.getNFOFileName());
                if (nfoFile.exists()) {
                    if (nfoFile.getCheckSum() == imdbInfo.getChecksum()) {
                        // 	passed all tests
                        return imdbInfo;
                    }
                }
            } catch (FileNotFoundException e) {
                // just continue, it couldn't find the previous nfo file, the line below here will remove it
                // we will then continue to try to find a new one right afterward
            }
            _dir.removePluginMetaData(IMDBInfo.IMDBINFO);
        } catch (KeyNotFoundException e1) {
            // bah, let's load it
        } catch (ObjectNotValidException e) {
            // the previous nfo file is no longer of type VirtualFileSystemFile
            _dir.removePluginMetaData(IMDBInfo.IMDBINFO);
        }

        for (FileHandle file : _dir.getFilesUnchecked()) {
            if (file.getName().toLowerCase().endsWith(".nfo")) {
                for (int i = 0; i < 5; i++) {
                    IMDBInfo info;
                    RemoteSlave rslave = file.getASlaveForFunction();
                    String index;
                    try {
                        index = getIMDBIssuer().issueNFOFileToSlave(rslave, file.getPath());
                        info = fetchIMDBInfoFromIndex(rslave, index);
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying, continue
                        continue;
                    } catch (RemoteIOException e) {
                        throw new IOException(e.getMessage());
                    }
                    if (info.getURL() != null) {
                        return info;
                    }
                }
                throw new SlaveUnavailableException("No slave for NFO file available");
            }
        }
        throw new FileNotFoundException("No NFO file with IMDB link in directory");
    }

    private IMDBInfo getIMDBInfoFromInode(DirectoryHandle vfsDirHandle) throws FileNotFoundException, KeyNotFoundException {
        return vfsDirHandle.getPluginMetaData(IMDBInfo.IMDBINFO);
    }

    private IMDBInfo fetchIMDBInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
        return ((AsyncResponseIMDBInfo) rslave.fetchResponse(index)).getIMDB();
    }

    private IMDBIssuer getIMDBIssuer() {
        return (IMDBIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(IMDBIssuer.class);
    }
}
