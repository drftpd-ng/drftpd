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
package org.drftpd.commands.autonuke;

import org.drftpd.vfs.DirectoryHandle;

import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds a queue of items(Directory & NukeConfig) to scan
 * @author scitz0
 */
public class DirsToCheck {
	private static DirsToCheck ref;
	private ConcurrentLinkedQueue<DirectoryHandle> _dirsToCheck;

	private DirsToCheck() {
		_dirsToCheck = new ConcurrentLinkedQueue<>();
	}

	public ConcurrentLinkedQueue<DirectoryHandle> get() {
		return _dirsToCheck;
	}

	public void add(DirectoryHandle dir) {
	   	_dirsToCheck.add(dir);
	}

	public void del(String path) {
        _dirsToCheck.removeIf(dir -> dir.getPath().equals(path));
	}

	public int clear() {
		int dirsInList = _dirsToCheck.size();
		_dirsToCheck.clear();
		return dirsInList;
	}

	public boolean empty() {
		return _dirsToCheck.isEmpty();
	}

	public int size() {
		return _dirsToCheck.size();
	}

	public static synchronized DirsToCheck getDirsToCheck() {
	  	if (ref == null)
			// it's ok, we can call this constructor
			ref = new DirsToCheck();
		return ref;
	}
}
