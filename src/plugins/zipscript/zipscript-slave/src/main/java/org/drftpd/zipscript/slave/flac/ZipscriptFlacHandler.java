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
package org.drftpd.zipscript.slave.flac;

import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.slave.protocol.AbstractHandler;
import org.drftpd.slave.Slave;
import org.drftpd.slave.protocol.SlaveProtocolCentral;

import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.zipscript.common.flac.AsyncResponseFlacInfo;
import org.drftpd.zipscript.common.flac.FlacInfo;


import java.io.IOException;

/**
 * Handler for FLAC info requests.
 * @author norox
 */
public class ZipscriptFlacHandler extends AbstractHandler {

	@Override
	public String getProtocolName() {
		return "ZipscriptFlacProtocol";
	}

	public ZipscriptFlacHandler(SlaveProtocolCentral central) {
		super(central);
	}

	public AsyncResponse handleFlacFile(AsyncCommandArgument ac) {
		try {
			return new AsyncResponseFlacInfo(ac.getIndex(),
					getFlacFile(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}
	}

	private FlacInfo getFlacFile(Slave slave, String path) throws IOException {
		FlacParser flacparser = new FlacParser(slave.getRoots().getFile(path));
		FlacInfo flacinfo = flacparser.getFlacInfo();
		return flacinfo;
	}
}
