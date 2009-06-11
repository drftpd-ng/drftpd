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
package org.drftpd.util;

import java.net.InetAddress;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

/**
 * @author mog
 * @version $Id$
 */
public class HostMask {

	private String _hostMask;

	private String _identMask;

	public HostMask(String string) {
		int pos = string.indexOf('@');

		if (pos == -1) {
			_identMask = "*";
			_hostMask = string;
		} else {
			_identMask = string.substring(0, pos);
			_hostMask = string.substring(pos + 1);
		}
		if (_identMask.equals("")) {
			_identMask = "*";
		}
		if (_hostMask.equals("")) {
			_hostMask = "*";
		}
	}

	public boolean equals(Object obj) {
		if (obj == null || !(obj instanceof HostMask)) {
			return false;
		}
		
		HostMask h = (HostMask) obj;
		return h.getIdentMask().equals(getIdentMask())
				&& h.getHostMask().equals(getHostMask());
	}

	public String getHostMask() {
		return _hostMask;
	}

	public String getIdentMask() {
		return _identMask;
	}

	public String getMask() {
		return getIdentMask() + "@" + getHostMask();
	}

	/**
	 * Is ident used?
	 * 
	 * @return false is ident mask equals "*"
	 */
	public boolean isIdentMaskSignificant() {
		return !_identMask.equals("*");
	}

	public boolean matchesHost(InetAddress a) throws MalformedPatternException {
		Perl5Matcher m = new Perl5Matcher();
		GlobCompiler c = new GlobCompiler();
		Pattern p = c.compile(getHostMask());

		return (m.matches(a.getHostAddress(), p) || m.matches(a.getHostName(),
				p));
	}

	public boolean matchesIdent(String ident) throws MalformedPatternException {
		Perl5Matcher m = new Perl5Matcher();
		GlobCompiler c = new GlobCompiler();

		if (ident == null) {
			ident = "";
		}

		return !isIdentMaskSignificant()
				|| m.matches(ident, c.compile(getIdentMask()));
	}

	public String toString() {
		return _identMask + "@" + _hostMask;
	}
}
