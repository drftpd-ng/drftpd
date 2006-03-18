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
package org.drftpd.sections.def;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;

import org.drftpd.GlobalContext;
import org.drftpd.master.ConnectionManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @version $Id$
 */
public class SectionManager implements SectionManagerInterface {

	public SectionManager() {
	}

	public ConnectionManager getConnectionManager() {
		return ConnectionManager.getConnectionManager();
	}

	public SectionInterface getSection(String string) {
		try {
			return new Section(getGlobalContext().getRoot()
					.getDirectory(string));
		} catch (FileNotFoundException e) {
			return new Section(getGlobalContext().getRoot());
		}
	}

	private GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public Collection<SectionInterface> getSections() {
		ArrayList<SectionInterface> sections = new ArrayList<SectionInterface>();

		try {
			for (Iterator<DirectoryHandle> iter = getGlobalContext().getRoot()
					.getDirectories().iterator(); iter.hasNext();) {
				sections.add(new Section(iter.next()));
			}
		} catch (FileNotFoundException e) {
			// no sections, return the empty set
			return Collections.EMPTY_SET;
		}

		return sections;
	}

	public void reload() {
	}

	public SectionInterface lookup(DirectoryHandle dir) {
		try {
			DirectoryHandle parent = dir.getParent();
			if (parent.isRoot()) {
				return new Section(dir);
			}
			return lookup(parent);
		} catch (IllegalStateException e) {
			throw new IllegalStateException(
					"The RootDirectory does not have a section");
		}

	}

	public class Section implements SectionInterface {
		private DirectoryHandle _lrf;

		public Section(DirectoryHandle lrf) {
			_lrf = lrf;
		}

		public DirectoryHandle getCurrentDirectory() {
			return _lrf;
		}

		public Set<DirectoryHandle> getDirectories() {
			try {
				return _lrf.getDirectories();
			} catch (FileNotFoundException e) {
				return Collections.EMPTY_SET;
			}
		}

		public String getName() {
			return _lrf.getName();
		}

		public String getPath() {
			return _lrf.getPath();
		}

		public DirectoryHandle getBaseDirectory() {
			return _lrf;
		}

		public String getBasePath() {
			return getPath();
		}
	}
}
