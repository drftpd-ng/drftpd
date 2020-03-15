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
package org.drftpd.commands.nuke;

/**
 * @author mog
 * @version $Id$
 */
public class NukedUser implements Comparable<NukedUser> {
	private String _username;

	private long _amount;

	public NukedUser(String user, long amount) {
		_username = user;
		_amount = amount;
	}

	public long getBytes() {
		return _amount;
	}

	public int compareTo(NukedUser o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();

		return (Long.compare(anotherVal, thisVal));
	}

	public String getUsername() {
		return _username;
	}

	/**
	 * Returns the amount nuked without multiplier.
	 * 
	 * @return the amount nuked without multiplier.
	 */
	public long getAmount() {
		return _amount;
	}
}
