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
package org.drftpd.slave;

import org.drftpd.Bytes;

import java.io.Serializable;

/**
 * @author zubov
 * @version $Id$
 */
public class DiskStatus implements Serializable {
	private static final long serialVersionUID = 3573098662042584609L;

	private long _free;

	private long _total;

	public DiskStatus(long free, long total) {
		_free = free;
		_total = total;
	}

	public long getBytesAvailable() {
		return _free;
	}

	public long getBytesCapacity() {
		return _total;
	}

	public String toString() {
		return getClass().getName() + "[free="
				+ Bytes.formatBytes(getBytesAvailable()) + ",total="
				+ Bytes.formatBytes(getBytesCapacity()) + "]";
	}
}
