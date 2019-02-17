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
package org.drftpd.plugins.sitebot.config;

import org.drftpd.util.GlobPattern;
import org.drftpd.vfs.InodeHandle;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;


/**
 * @author djb61
 * @version $Id$
 */
public class PathMatcher {

	private java.util.regex.Pattern _globPat;
	private Pattern _regexPat;

	private boolean _regex;

	private String _pathPattern;

	public PathMatcher(String pathPattern, boolean regex) throws PatternSyntaxException {
		if (regex) {
			_regexPat = Pattern.compile(pathPattern, Pattern.CASE_INSENSITIVE);
		} else {
			_globPat = GlobPattern.compile(pathPattern);
		}
		_regex = regex;
		_pathPattern = pathPattern;
	}

	public boolean checkPath(InodeHandle inode) {
		String path = inode.isDirectory() ? inode.getPath().concat("/") : inode.getPath();

		if (_regex) {
			Matcher m = _regexPat.matcher(path);
			return m.matches();
		} else {
			Matcher m = _globPat.matcher(path);
			return m.matches();
		}
	}

	public String getPathSuffix() {
		int index = _pathPattern.lastIndexOf('/');
		return _pathPattern.substring(0,index);
	}

	public String getRelativePath(InodeHandle inode) {
		if (_regex) {
			String path = inode.getPath().concat("/");
			Matcher m = _regexPat.matcher(path);
			if (m.matches()) {
				try {
					String announce = m.group("announce");
					if (announce != null) {
						return announce;
					}
				} catch (IllegalArgumentException e) {
					// No "announce" group specified in regex, this is fine
				}
				try {
					String truncate = m.group("truncate");
					if (truncate != null) {
						return inode.getPath().substring(truncate.length());
					}
				} catch (IllegalArgumentException e) {
					// No "truncate" group specified in regex, this is fine
				}
			}
			return path;
		} else {
			return inode.getPath().substring(getPathSuffix().length()+1);
		}
	}
}
