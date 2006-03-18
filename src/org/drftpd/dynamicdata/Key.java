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
package org.drftpd.dynamicdata;

import java.io.Serializable;

/**
 * @author mog
 * @version $Id$
 */
public class Key implements Serializable {
	private String _key;

	private Class _owner;

	private Class _type;

	public Key(Class owner, String key, Class type) {
		assert owner != null;
		assert key != null;
		assert type != null;
		_owner = owner;
		_key = key;
		_type = type;
	}

	public boolean equals(Object o) {
		if (o == null) {
			return false;
		}

		Key ok = (Key) o;

		return ok.getOwner() == getOwner() && ok.getKey().equals(getKey());
	}

	public String getKey() {
		return _key;
	}

	public Class getOwner() {
		return _owner;
	}

	public Class getType() {
		return _type;
	}

	public String toString() {
		return getOwner().getName() + '@' + getKey();
	}

	public String toString(Object value) {
		return value.toString();
	}

	public int hashCode() {
		return toString().hashCode();
	}
}
