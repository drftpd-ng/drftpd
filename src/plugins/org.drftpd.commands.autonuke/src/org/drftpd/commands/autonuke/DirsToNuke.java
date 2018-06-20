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

import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Holds a queue of NukeItems to nuke
 * @author scitz0
 */
public class DirsToNuke {
    private static DirsToNuke ref;
    private ConcurrentLinkedQueue<NukeItem> _dirsToNuke;

    private DirsToNuke() {
        _dirsToNuke = new ConcurrentLinkedQueue<>();
    }

    public ConcurrentLinkedQueue<NukeItem> get() {
        return _dirsToNuke;
    }

    public boolean add(NukeItem ni) {
		for (NukeItem tmpni : _dirsToNuke) {
			if (tmpni.getDir().equals(ni.getDir())) {
				// Dir already exist in nuke queue
				return false;
			}
		}
		_dirsToNuke.add(ni);
		return true;
    }

	public boolean del(DirectoryHandle dir) {
		return del(dir.getPath());
    }

	public boolean del(String path) {
		for (Iterator<NukeItem> iter = _dirsToNuke.iterator(); iter.hasNext();) {
			NukeItem ni = iter.next();
			DirectoryHandle dir = ni.getDir();
			if (ni.isSubdir()) {
				dir = dir.getParent();
			}
			if (dir.getPath().equals(path)) {
				iter.remove();
				return true;
			}
		}
		return false;
    }

    public int clear() {
		int itemsInList = _dirsToNuke.size();
        _dirsToNuke.clear();
		return itemsInList;
    }

	public boolean empty() {
		return _dirsToNuke.isEmpty();
	}

	public int size() {
		return _dirsToNuke.size();
	}

    public static synchronized DirsToNuke getDirsToNuke() {
      if (ref == null)
          // it's ok, we can call this constructor
          ref = new DirsToNuke();
      return ref;
    }
}
