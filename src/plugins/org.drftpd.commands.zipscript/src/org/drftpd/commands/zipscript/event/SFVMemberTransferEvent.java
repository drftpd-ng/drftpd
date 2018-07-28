/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.commands.zipscript.event;

import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.TransferEvent;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.vfs.FileHandle;

import java.net.InetAddress;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVMemberTransferEvent extends TransferEvent {

	private ZipscriptVFSDataSFV _sfvData;
	
	private SFVInfo _sfvInfo;
	
	private SFVStatus _sfvStatus;
	
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
