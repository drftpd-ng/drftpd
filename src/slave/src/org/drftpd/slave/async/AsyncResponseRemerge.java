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
package org.drftpd.slave.async;

import org.drftpd.slave.LightRemoteInode;

import java.io.File;
import java.util.Collections;
import java.util.List;

/**
 * @author zubov
 * @version $Id$
 */
@SuppressWarnings("serial")
public class AsyncResponseRemerge extends AsyncResponse {
	private List<LightRemoteInode> _inodes;

	private String _path;
	
	private long _lastModified;

	public AsyncResponseRemerge(String directoryPath,
			List<LightRemoteInode> inodes, long lastModified) {
		super("Remerge");
		if (File.separatorChar == '\\') { // stupid win32 hack
			directoryPath = directoryPath.replaceAll("\\\\", "/");
		}
		if (directoryPath.indexOf('\\') != -1) {
			throw new RuntimeException(
					"\\ is not an acceptable character in a directory path");
		}
		if (directoryPath.equals("")) {
			directoryPath = File.separator;
		}
		_path = directoryPath;
		_inodes = inodes;
		_lastModified = lastModified;
	}

	public String getPath() {
		return _path;
	}

	public List<LightRemoteInode> getFiles() {
		return Collections.unmodifiableList(_inodes);
	}
	
	public long getLastModified() {
		return _lastModified;
	}

	public String toString() {
		return getClass().getName() + "[path=" + getPath() + "]";
	}
}
