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
package org.drftpd.commands.dataconnection.hooks;

import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.master.BaseFtpConnection;
import org.drftpd.master.master.RemoteSlave;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;

import java.net.InetAddress;

/**
 * @author djb61
 * @version $Id$
 */
public class DataConnectionPostHooks {

	@CommandHook(commands = "doSTOR", priority = 9999999, type = HookType.POST)
	public void doTransferEvent(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// Transfer failed, skip event
			return;
		}
		try {
			FileHandle transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
			String eventType = request.getCommand().equalsIgnoreCase("RETR") ? "RETR" : "STOR";
			if (transferFile.exists() || eventType.equals("RETR")) {
				try {
					BaseFtpConnection conn = (BaseFtpConnection)request.getSession();
					RemoteSlave transferSlave = response.getObject(DataConnectionHandler.TRANSFER_SLAVE);
					InetAddress transferSlaveInetAddr =
						response.getObject(DataConnectionHandler.TRANSFER_SLAVE_INET_ADDRESS);
					char transferType = response.getObject(DataConnectionHandler.TRANSFER_TYPE);
					GlobalContext.getEventService().publishAsync(
							new TransferEvent(conn, eventType, transferFile,
									conn.getClientAddress(), transferSlave,
									transferSlaveInetAddr, transferType));
				} catch (KeyNotFoundException e1) {
					// one or more bits of information didn't get populated correctly, have to skip the event
				}
				
			}
		} catch (KeyNotFoundException e) {
			// shouldn't have got a 226 response and still ended up here
		}
	}
}
