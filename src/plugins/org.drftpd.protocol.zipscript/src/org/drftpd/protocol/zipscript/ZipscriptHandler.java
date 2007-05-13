/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.zipscript;

import java.io.IOException;

import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.slave.async.AsyncResponseSFVInfo;

/**
 * Handler for SFV requests.
 * @author fr0w
 * @version $Id$
 */
public class ZipscriptHandler extends AbstractHandler {
	public ZipscriptHandler(SlaveProtocolCentral central) {
		super(central);
	}

	public AsyncResponse handleSfvFile(AsyncCommandArgument ac) {
		try {
			return new AsyncResponseSFVInfo(ac.getIndex(), 
					getSlaveObject().getSFVFile(getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}
	}
}
