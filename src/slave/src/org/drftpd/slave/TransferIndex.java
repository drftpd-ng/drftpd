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

import java.io.Serializable;

/**
 * @author zubov
 * @version $Id$
 */
@SuppressWarnings("serial")
public final class TransferIndex implements Serializable {
	private static Object mutex = new Object();
	private static Integer transfers = 0;

	private int _index;

	public TransferIndex(int index) {
		_index = index;
	}

	public TransferIndex() {
		synchronized (mutex) {
			_index = transfers++;
		}
	}

	public boolean equals(Object obj) {
		if (!(obj instanceof TransferIndex)){
			return false;
		}
		
		return _index == ((TransferIndex) obj)._index;
	}

	public int hashCode() {
		return _index;
	}

	public String toString() {
		return Integer.toString(_index);
	}
}
