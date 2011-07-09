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
package org.drftpd.commands.zipscript.zip.event;

import java.net.InetAddress;

import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.event.TransferEvent;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.vfs.FileHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipTransferEvent extends TransferEvent {

	private ZipscriptVFSDataZip _zipData;
	
	private DizInfo _dizInfo;
	
	private DizStatus _dizStatus;
	
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
