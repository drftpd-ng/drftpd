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
import java.util.Locale;

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
        DizInfo dizInfo = readFromSlaves().getDizInfo();

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

    /**
     * Reads the DIZ info from the slaves, ignoring and not touching the cached value on the inode.
     * Unlike {@link #getDizInfo()} this tells apart a DIZ that is really not there from a DIZ that could not be
     * read (no slave answered, or no zip is complete yet), so callers can avoid dropping a good cached value.
     */
    public DizReadResult readDizInfoFresh() {
        try {
            DizReadResult result = readFromSlaves();
            if (result.getStatus() == DizReadResult.Status.FOUND) {
                return result;
            }
            return new DizReadResult(result.getStatus(), null);
        } catch (FileNotFoundException e) {
            // directory or file deleted meanwhile, nothing could be read
            logger.debug("Unable to read DIZINFO, {} or one of its files is gone", _dir, e);
            return new DizReadResult(DizReadResult.Status.UNAVAILABLE, null);
        }
    }

    /**
     * Asks the slaves for the DIZ info of the eligible zips until a valid one is found.
     * The returned DizInfo is the last one read, valid or not, or null if nothing was read.
     */
    private DizReadResult readFromSlaves() throws FileNotFoundException {
        DizInfo dizInfo = null;
        boolean anyRead = false;
        for (FileHandle file : _dir.getFilesUnchecked()) {
            // Cheap name/type checks first, so the other files of the dir (.nfo, .sfv...) are never queried
            if (!file.isFile() || !isZipName(file.getName())) {
                continue;
            }
            try {
                if (!isEligibleZip(file.getName(), true, file.isUploading(), file.getSize(), file.getXfertime())) {
                    continue;
                }
            } catch (FileNotFoundException e) {
                // The zip was deleted while we were looking at the dir, try the next one
                continue;
            }
            try {
                RemoteSlave rslave = file.getASlaveForFunction();
                logger.debug("Trying to retrieve DIZINFO from slave {} for file {}", rslave, file);
                String index = getZipIssuer().issueZipDizInfoToSlave(rslave, file.getPath());
                dizInfo = fetchDizInfoFromIndex(rslave, index);
                anyRead = true;
            } catch (NoAvailableSlaveException e) {
                // Not an issue, file was available but slave dropped, try next file
                logger.debug("No available slave to retrieve DIZINFO for {}, trying next file", file.getPath());
            } catch (SlaveUnavailableException e) {
                // okay, it went offline while trying, try next file
                logger.debug("Slave went offline while retrieving DIZINFO for {}, trying next file", file.getPath());
            } catch (RemoteIOException e) {
                // continue, the next zip might work
                logger.warn("Slave failed to retrieve DIZINFO for {}: {}", file.getPath(), e.getCause() != null ? e.getCause().getMessage() : e.getMessage(), e);
            }

            // If we have valid dizinfo stop the loop
            if (dizInfo != null && dizInfo.isValid()) {
                break;
            }
        }
        return new DizReadResult(decideStatus(anyRead, dizInfo), dizInfo);
    }

    /**
     * Only complete .zip files can give valid dizinfo.
     */
    static boolean isZipName(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    static boolean isEligibleZip(String name, boolean isFile, boolean isUploading, long size, long xfertime) {
        return isFile
                && isZipName(name)
                && !isUploading
                && size > 0
                && xfertime != -1;
    }

    static DizReadResult.Status decideStatus(boolean anyRead, DizInfo dizInfo) {
        if (dizInfo != null && dizInfo.isValid()) {
            return DizReadResult.Status.FOUND;
        }
        return anyRead ? DizReadResult.Status.NOT_FOUND : DizReadResult.Status.UNAVAILABLE;
    }

    /**
     * Outcome of reading the DIZ info from the slaves.
     */
    public static final class DizReadResult {
        public enum Status {
            /** A valid DIZ was read. */
            FOUND,
            /** At least one eligible zip was read from a slave and no valid DIZ was found. */
            NOT_FOUND,
            /** No eligible zip could be read: no slave answered or no zip is complete yet. */
            UNAVAILABLE
        }

        private final Status _status;
        private final DizInfo _dizInfo;

        DizReadResult(Status status, DizInfo dizInfo) {
            _status = status;
            _dizInfo = dizInfo;
        }

        public Status getStatus() {
            return _status;
        }

        /** @return the valid DizInfo for FOUND, null otherwise */
        public DizInfo getDizInfo() {
            return _dizInfo;
        }
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
