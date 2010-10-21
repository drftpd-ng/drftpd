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
package org.drftpd.vfs.index;

/**
 * Any exception throw by a IndexingEngine must be encapsulated in this exception
 * in order to be handled properly.
 * @author fr0w
 * @version $Id$
 */
@SuppressWarnings("serial")
public class IndexException extends Exception {
	public IndexException(Throwable cause) {
		super(cause);
	}

	public IndexException(String message) {
		super(message);
	}

	public IndexException(String message, Throwable cause) {
		super(message, cause);
	}
}
