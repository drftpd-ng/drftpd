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
package org.drftpd.zipscript.master.sfv.event;

import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.common.sfv.SFVStatus;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;

import java.net.InetAddress;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVMemberTransferEvent extends TransferEvent {

    private final ZipscriptVFSDataSFV _sfvData;

    private final SFVInfo _sfvInfo;

    private final SFVStatus _sfvStatus;

    public SFVMemberTransferEvent(BaseFtpConnection conn, String command,
                                  FileHandle file, InetAddress clientHost,
                                  RemoteSlave rslave, InetAddress peer, char type,
                                  ZipscriptVFSDataSFV sfvData, SFVInfo sfvInfo, SFVStatus sfvStatus) {
        super(conn, command, file, clientHost, rslave, peer, type);
        _sfvData = sfvData;
        _sfvInfo = sfvInfo;
        _sfvStatus = sfvStatus;
    }

    public ZipscriptVFSDataSFV getSFVData() {
        return _sfvData;
    }

    public SFVInfo getSFVInfo() {
        return _sfvInfo;
    }

    public SFVStatus getSFVStatus() {
        return _sfvStatus;
    }
}
