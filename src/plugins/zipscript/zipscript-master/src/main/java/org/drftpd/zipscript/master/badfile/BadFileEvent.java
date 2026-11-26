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
package org.drftpd.zipscript.master.badfile;

import org.drftpd.master.event.Event;
import org.drftpd.master.vfs.DirectoryHandle;

/**
 * Published when the zipscript refuses or removes an uploaded file. Announced by {@link BadFileAnnouncer}.
 */
public class BadFileEvent extends Event {

    private final String _username;
    private final DirectoryHandle _directory;
    private final String _fileName;
    private final BadFileReason _reason;
    private final String _detail;

    /**
     * @param username  the user who uploaded the file
     * @param directory the directory the file was (to be) uploaded to
     * @param fileName  name of the file
     * @param reason    why the file is bad
     * @param detail    free text with more information (e.g. the CRCs, or the rule that failed), may be null
     */
    public BadFileEvent(String username, DirectoryHandle directory, String fileName,
                        BadFileReason reason, String detail) {
        super("BADFILE");
        _username = username;
        _directory = directory;
        _fileName = fileName;
        _reason = reason;
        _detail = detail == null ? "" : detail;
    }

    public String getUsername() {
        return _username;
    }

    public DirectoryHandle getDirectory() {
        return _directory;
    }

    public String getFileName() {
        return _fileName;
    }

    public BadFileReason getReason() {
        return _reason;
    }

    public String getDetail() {
        return _detail;
    }
}
