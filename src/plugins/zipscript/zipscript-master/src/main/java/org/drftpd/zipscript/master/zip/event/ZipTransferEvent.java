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
package org.drftpd.zipscript.master.zip.event;

import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;

import java.net.InetAddress;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipTransferEvent extends TransferEvent {

    private final ZipscriptVFSDataZip _zipData;

    private final DizInfo _dizInfo;

    private final DizStatus _dizStatus;

    public ZipTransferEvent(BaseFtpConnection conn, String command,
                            FileHandle file, InetAddress clientHost,
                            RemoteSlave rslave, InetAddress peer, char type,
                            ZipscriptVFSDataZip zipData, DizInfo dizInfo, DizStatus dizStatus) {
        super(conn, command, file, clientHost, rslave, peer, type);
        _zipData = zipData;
        _dizInfo = dizInfo;
        _dizStatus = dizStatus;
    }

    public ZipscriptVFSDataZip getZipData() {
        return _zipData;
    }

    public DizInfo getDizInfo() {
        return _dizInfo;
    }

    public DizStatus getDizStatus() {
        return _dizStatus;
    }
}
