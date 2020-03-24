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
package org.drftpd.protocol.zipscript.zip.common;

/**
 * @author djb61
 * @version $Id$
 */

public class DizStatus {
	private int _offline;

	private int _present;

	private int _total;

	public DizStatus(int total, int offline, int present) {
		_total = total;
		_offline = offline;
		_present = present;
	}

	/**
	 * Returns the number of files that are available (online).
	 * 
	 * If a file is online, it is of course is also present (exists).
	 * 
	 * @return the number of files that are available (present & online)
	 */
	public int getAvailable() {
		return _present - _offline;
	}

	/**
	 * Returns the number of files that don't exist or are 0byte.
	 * 
	 * @return the number of files that don't exist or are 0byte.
	 */
	public int getMissing() {
		return _total - _present;
	}

	/**
	 * Returns the number of files that are offline.
	 * 
	 * @return the number of files that are offline.
	 */
	public int getOffline() {
		return _offline;
	}

	/**
	 * Returns the number of files that exist and are not 0 byte.
	 * 
	 * @return the number of files that exist and are not 0 byte.
	 */
	public int getPresent() {
		return _present;
	}

	public boolean isFinished() {
		// less strict than for sfv as we could have more zips than the diz total
		return getMissing() <= 0;
	}
}
