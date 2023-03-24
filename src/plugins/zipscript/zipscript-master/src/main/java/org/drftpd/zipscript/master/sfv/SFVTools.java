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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVTools {

    public static Collection<FileHandle> getSFVFiles(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException, FileNotFoundException {
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
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException, FileNotFoundException {
        long totalBytes = 0;

        for (FileHandle file : getSFVFiles(dir, sfvData)) {
            totalBytes += file.getSize();
        }
        return totalBytes;
    }

    public static long getSFVLargestFileBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException, FileNotFoundException {
        long largestFileBytes = 0;

        for (FileHandle file : getSFVFiles(dir, sfvData)) {
            if (file.getSize() > largestFileBytes) {
                largestFileBytes = file.getSize();
            }
        }
        return largestFileBytes;
    }

    public static long getSFVTotalXfertime(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData)
            throws IOException, NoAvailableSlaveException, SlaveUnavailableException, FileNotFoundException {
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

    public static SFVStatus getSFVStatus(SFVInfo sfvInfo, DirectoryHandle dir)
            throws FileNotFoundException {
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
