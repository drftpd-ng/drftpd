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

import org.drftpd.exceptions.DuplicateElementException;
import socks.server.Ident;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @version $Id$
 */
@SuppressWarnings("serial")
public class HostMaskCollection extends ArrayList<HostMask> {

	public HostMaskCollection() {
	}

	/**
	 * Converts an existing Collection of String-based masks to a
	 * HostMaskCollection
	 *
	 * @param masks
	 */
	public HostMaskCollection(Collection<String> masks) {
		for (String mask : masks) {
			add(new HostMask(mask));
		}
	}

	public void addAllMasks(HostMaskCollection hostMaskCollection) {
		for (HostMask mask : hostMaskCollection) {
			if (!contains(mask)) {
				add(mask);
			}
		}
	}

	public void addMask(String mask) throws DuplicateElementException {
		HostMask newMask = new HostMask(mask);

		for (HostMask hostMask : this) {
			if (hostMask.equals(newMask)) {
				throw new DuplicateElementException();
			}
		}

		add(newMask);
	}

	public boolean check(Socket s) throws PatternSyntaxException {
		return check(null, s.getInetAddress(), s);
	}

	public boolean check(String ident, InetAddress a, Socket s)
			throws PatternSyntaxException {
		if (a == null) {
			throw new NullPointerException();
		}
		for (HostMask mask : this) {
			if (!mask.matchesHost(a)) {
				continue;
			}

			// host matched
			// request ident if no IDNT, ident hasn't been requested
			// and ident matters in this hostmask
			if (mask.isIdentMaskSignificant() && (ident == null)) {
				try {
					ident = new Ident(s).getUserName();
				} catch (IOException e) {
					ident = "";
				}
			}

			if (mask.matchesIdent(ident)) {
				return true;
			}
		}

		return false;
	}

	/**
	 * @param mask
	 * @return
	 */
	public boolean removeMask(String mask) {
		for (Iterator<HostMask> iter = iterator(); iter.hasNext();) {
			if ((iter.next()).getMask().equals(mask)) {
				iter.remove();
				return true;
			}
		}

		return false;
	}

	public String toString() {
		if (isEmpty())
			return "";
		String masks = "";

		for (HostMask hostMask : this) {
			masks = masks + hostMask + "  ";
		}

		return masks.substring(0, masks.length() - 2);
	}
}
