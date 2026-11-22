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

import org.drftpd.common.vfs.CaseInsensitiveTreeMap;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVTools {

    public static Collection<FileHandle> getSFVFiles(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        Collection<FileHandle> files = new ArrayList<>();
        SFVInfo sfvInfo = sfvData.getSFVInfo();

        for (String name : sfvInfo.getEntries().keySet()) {
            FileHandle file = new FileHandle(dir.getPath() + VirtualFileSystem.separator + name);
            if (file.exists() && file.getXfertime() != -1) {
                files.add(file);
            }
        }
        return files;
    }

    public static long getSFVTotalBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        long totalBytes = 0;

        for (FileHandle file : getSFVFiles(dir, sfvData)) {
            totalBytes += file.getSize();
        }
        return totalBytes;
    }

    public static long getSFVLargestFileBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        long largestFileBytes = 0;

        for (FileHandle file : getSFVFiles(dir, sfvData)) {
            if (file.getSize() > largestFileBytes) {
                largestFileBytes = file.getSize();
            }
        }
        return largestFileBytes;
    }

    public static long getSFVTotalXfertime(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        long totalXfertime = 0;

        for (FileHandle file : getSFVFiles(dir, sfvData)) {
            totalXfertime += file.getXfertime();
        }
        return totalXfertime;
    }

    public static long getXferspeed(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
        long totalXfertime = getSFVTotalXfertime(dir, sfvData);
        if (totalXfertime / 1000 == 0) {
            return 0;
        }

        return getSFVTotalBytes(dir, sfvData) / (totalXfertime / 1000);
    }

    /**
     * Finds the files in a directory whose checksum does not match the entry for them in the sfv.
     * <p>
     * Used when an .sfv is uploaded after (some of) the files it lists: those files were not
     * checked when they were uploaded, because there was no sfv to check them against (#77).
     * <p>
     * Only the checksum recorded during each file's transfer is used, so this never asks a slave
     * to read a file. The rules match the checks done when a file is uploaded after the sfv:
     * <ul>
     *   <li>files not listed in the sfv, and files still being uploaded, are skipped;</li>
     *   <li>a 0-byte file with no checksum is reported, unless the sfv expects 00000000;</li>
     *   <li>a non-empty file with no recorded checksum (checksummed transfers disabled) is skipped,
     *       as there is nothing to compare.</li>
     * </ul>
     *
     * @param sfvInfo the parsed sfv
     * @param dir     the directory holding the sfv and the files
     * @return the files that do not match the sfv, in directory order
     */
    public static List<FileHandle> getFilesNotMatchingSFV(SFVInfo sfvInfo, DirectoryHandle dir)
            throws FileNotFoundException {
        List<FileHandle> mismatched = new ArrayList<>();
        CaseInsensitiveTreeMap<String, Long> sfvEntries = sfvInfo.getEntries();
        for (FileHandle file : dir.getFilesUnchecked()) {
            try {
                Long sfvChecksum = sfvEntries.get(file.getName());
                if (sfvChecksum == null || !file.isFile() || file.isUploading()) {
                    continue;
                }
                long checksum = file.getCheckSumCached();
                if (checksum == sfvChecksum) {
                    continue;
                }
                if (checksum == 0L && file.getSize() != 0L) {
                    // No checksum recorded for this transfer, nothing to compare against
                    continue;
                }
                mismatched.add(file);
            } catch (FileNotFoundException e) {
                // File was deleted while we were checking, nothing to do
            }
        }
        return mismatched;
    }

    public static SFVStatus getSFVStatus(SFVInfo sfvInfo, DirectoryHandle dir)
            throws IOException {
        int offline = 0;
        int present = 0;
        CaseInsensitiveTreeMap<String, Long> sfvEntries = sfvInfo.getEntries();
        for (FileHandle file : dir.getFilesUnchecked()) {
            if (file.isFile() && sfvEntries.containsKey(file.getName())) {
                try {
                    // file.isUploading() returns true if sent to additional slaves with jobmanager
                    // The checksum control should be enough to verify successful upload
                    //if (!file.isUploading()) {
                    // Verify checksum of file
                    try {
                        if (file.getCheckSum() == sfvEntries.get(file.getName())) {
                            present++;
                        }
                    } catch (NoAvailableSlaveException e) {
                        // Unable to get a slave for checksum, caught by check below
                    }
                    //}
                    if (!file.isAvailable()) {
                        offline++;
                    }
                } catch (FileNotFoundException e) {
                    // Ignore, marked as missing
                }
            }
        }
        return new SFVStatus(sfvEntries.size(), offline, present);
    }
}
