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

import java.util.Collection;
import java.util.regex.PatternSyntaxException;

public class CreditLimitPathPermission extends GlobPathPermission {
	private int _direction;
	private String _period;
	private long _bytes;

	/**
	 * @param pattern
	 * @param direction
	 * @param period
	 * @param bytes
	 * @param users
	 * @throws PatternSyntaxException
	 */
	public CreditLimitPathPermission(String pattern, int direction, String period, long bytes, Collection<String> users)
			throws PatternSyntaxException {
		super(pattern, users);
		_direction = direction;
		_period = period;
		_bytes = bytes;
	}

	public int getDirection() {
		return _direction;
	}

	public String getPeriod() {
		return _period;
	}

	public long getBytes() {
		return _bytes;
	}
}
