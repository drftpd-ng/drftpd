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
package org.drftpd.tests;

import org.drftpd.exceptions.FatalException;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author fr0w
 * @version $Id$
 */
public class DummySectionManager implements SectionManagerInterface {
	private DummySection _section;

	public DummySectionManager() {
		_section = new DummySection();
	}
	public SectionInterface getSection(String string) {
		return _section;
	}

	public Collection<SectionInterface> getSections() {
		ArrayList<SectionInterface> list = new ArrayList<>(1);
		list.add(_section);
		return list;
	}

	public Map<String, SectionInterface> getSectionsMap() {
		HashMap<String, SectionInterface> map = new HashMap<>(1);
		map.put(_section.getName(), _section);
		return map;
	}

	public SectionInterface lookup(DirectoryHandle dir) {
		return _section;
	}

	public void reload() {	}
	
}

class DummySection implements SectionInterface {

	public DirectoryHandle getBaseDirectory() {
		return new DirectoryHandle("/");
	}

	public DirectoryHandle getCurrentDirectory() {
		return new DirectoryHandle("/");
	}

	public Set<DirectoryHandle> getDirectories() {
		try {
			return new DirectoryHandle("/").getDirectoriesUnchecked();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
	}

	public String getColor() {
		return "DummyColor.";
	}
	public String getName() {
		return "DummySection.";
	}
	
}
