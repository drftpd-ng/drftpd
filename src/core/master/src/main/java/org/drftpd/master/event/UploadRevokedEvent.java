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
package org.drftpd.master.event;

import org.drftpd.master.vfs.DirectoryHandle;

/**
 * Published when the site removes an already uploaded file on the uploader's behalf (for example
 * zipscript deleting a file that doesn't match an .sfv uploaded after it), so that plugins which
 * awarded something for the upload, such as credits and upload stats, can take it back.
 * <p>
 * Publish it before deleting the file; all details are captured in the event, so subscribers
 * don't need the file to still exist.
 */
public class UploadRevokedEvent extends Event {

    private final DirectoryHandle _directory;
    private final String _fileName;
    private final String _username;
    private final long _size;
    private final long _xferTime;
    private final long _uploadTime;

    /**
     * @param directory  the directory the file was uploaded to
     * @param fileName   name of the file
     * @param username   the user who uploaded the file
     * @param size       size of the file in bytes
     * @param xferTime   transfer time of the upload in milliseconds
     * @param uploadTime when the file was uploaded (its last-modified time)
     */
    public UploadRevokedEvent(DirectoryHandle directory, String fileName, String username,
                              long size, long xferTime, long uploadTime) {
        super("UPLOADREVOKED");
        _directory = directory;
        _fileName = fileName;
        _username = username;
        _size = size;
        _xferTime = xferTime;
        _uploadTime = uploadTime;
    }

    public DirectoryHandle getDirectory() {
        return _directory;
    }

    public String getFileName() {
        return _fileName;
    }

    public String getUsername() {
        return _username;
    }

    public long getSize() {
        return _size;
    }

    public long getXferTime() {
        return _xferTime;
    }

    public long getUploadTime() {
        return _uploadTime;
    }
}
