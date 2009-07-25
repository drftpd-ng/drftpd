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
package org.drftpd.protocol.zipscript.mp3.common.async;

import org.drftpd.protocol.zipscript.mp3.common.MP3Info;
import org.drftpd.slave.async.AsyncResponse;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class AsyncResponseMP3Info extends AsyncResponse {

	private MP3Info _mp3info;

	public AsyncResponseMP3Info(String index, MP3Info mp3info) {
		super(index);

		_mp3info = mp3info;
	}

	public MP3Info getMP3Info() {
		return _mp3info;
	}

	public String toString() {
		return getClass().getName();
	}
}
