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
package org.drftpd.protocol.mediainfo.slave;

import org.drftpd.protocol.mediainfo.common.MediaInfo;
import org.drftpd.protocol.mediainfo.common.async.AsyncResponseMediaInfo;
import org.drftpd.protocol.slave.AbstractHandler;
import org.drftpd.protocol.slave.SlaveProtocolCentral;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseException;

import java.io.IOException;

/**
 * Handler for MediaInfo requests.
 * @author scitz0
 */
public class MediaInfoHandler extends AbstractHandler {
	public MediaInfoHandler(SlaveProtocolCentral central) {
		super(central);
	}

	public AsyncResponse handleMediaFile(AsyncCommandArgument ac) {
		try {
			return new AsyncResponseMediaInfo(ac.getIndex(),
					getMediaInfo(getSlaveObject(), getSlaveObject().mapPathToRenameQueue(ac.getArgs())));
		} catch (IOException e) {
			return new AsyncResponseException(ac.getIndex(), e);
		}
	}

	private MediaInfo getMediaInfo(Slave slave, String path) throws IOException {
		return MediaInfo.getMediaInfoFromFile(slave.getRoots().getFile(path));
	}
}
