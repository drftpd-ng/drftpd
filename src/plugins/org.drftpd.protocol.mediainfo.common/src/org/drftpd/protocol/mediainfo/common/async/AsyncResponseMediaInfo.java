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
package org.drftpd.protocol.mediainfo.common.async;

import org.drftpd.protocol.mediainfo.common.MediaInfo;
import org.drftpd.slave.async.AsyncResponse;

/**
 * @author scitz0
 */
@SuppressWarnings("serial")
public class AsyncResponseMediaInfo extends AsyncResponse {
	private MediaInfo _mediaInfo;

	public AsyncResponseMediaInfo(String index, MediaInfo mediaInfo) {
		super(index);
		_mediaInfo = mediaInfo;
	}

	public MediaInfo getMediaInfo() {
		return _mediaInfo;
	}

	public String toString() {
		return getClass().getName();
	}
}
