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
package org.drftpd.permissions;

import org.drftpd.util.GlobPattern;
import org.drftpd.vfs.InodeHandle;

import java.util.Collection;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @author Epochyx
 * @version $Id$
 */
public class GlobPathPermission extends PathPermission {
	Pattern _pat;

	public GlobPathPermission(Pattern pat, Collection<String> users) {
		super(users);
		_pat = pat;
	}

	public GlobPathPermission(String pattern, Collection<String> users) throws PatternSyntaxException {
		super(users);
		_pat = GlobPattern.compile(pattern);
	}

	public boolean checkPath(InodeHandle inode) {

		String path = inode.getPath();
		if (inode.isDirectory() && !inode.getPath().endsWith("/")) {
			path = inode.getPath().concat("/");
		}

		return _pat.matcher(path).matches();
	}

	public Pattern getPattern() {
		return _pat;
	}

	public String toString() {
		return getClass().getCanonicalName() + ",pat=" + _pat.pattern() + ",users=" + _users.toString();
	}
}
