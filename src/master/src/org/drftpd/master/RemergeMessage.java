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
package org.drftpd.master;

import org.drftpd.slave.LightRemoteInode;
import org.drftpd.slave.async.AsyncResponseRemerge;

import java.util.List;

/**
 * @author mog
 * @version $Id$
 */
public class RemergeMessage {
	private RemoteSlave _rslave;

	private AsyncResponseRemerge _response;

	public RemergeMessage(AsyncResponseRemerge response, RemoteSlave slave) {
		_rslave = slave;
		_response = response;
	}

	public RemergeMessage(RemoteSlave slave) {
		_rslave = slave;
		_response = null;
	}

	public boolean isCompleted() {
		return _response == null;
	}

	public String getDirectory() {
		return _response.getPath();
	}

	public RemoteSlave getRslave() {
		return _rslave;
	}

	public List<LightRemoteInode> getFiles() {
		return _response.getFiles();
	}

	public long getLastModified() {
		return _response.getLastModified();
	}
}
