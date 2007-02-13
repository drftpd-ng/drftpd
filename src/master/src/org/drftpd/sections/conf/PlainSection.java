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
package org.drftpd.sections.conf;

import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import org.drftpd.PropertyHelper;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author mog
 * @version $Id$
 */
public class PlainSection implements SectionInterface {
	private DirectoryHandle _dir;

	private SectionManager _mgr;

	private String _name;

	public PlainSection(SectionManager mgr, int i, Properties p) {
		this(mgr, PropertyHelper.getProperty(p, i + ".name"),
				new DirectoryHandle(PropertyHelper.getProperty(p, i + ".path")));
	}

	public PlainSection(SectionManager mgr, String name, DirectoryHandle path) {
		_mgr = mgr;
		_name = name;
		_dir = path;

	}

	public DirectoryHandle getCurrentDirectory() {
		return _dir;
	}

	public Set<DirectoryHandle> getDirectories() {
		return Collections.singleton(getCurrentDirectory());
	}

	public String getName() {
		return _name;
	}

	public String getPath() {
		return _dir.getPath();
	}

	public String getBasePath() {
		return getPath();
	}

	public DirectoryHandle getBaseDirectory() {
		return getCurrentDirectory();
	}
}
