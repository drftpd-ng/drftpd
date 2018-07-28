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
package org.drftpd.master;

import java.io.Serializable;

/**
 * @author zubov
 * @version $Id$
 */
public class QueuedOperation implements Serializable {

	private static final long serialVersionUID = 3258125869099659321L;

	private String _source;

	private String _destination;

	public boolean equals(Object obj) {
		if (!(obj instanceof QueuedOperation)) {
			return false;
		}
		
		QueuedOperation arg = (QueuedOperation) obj;
		return arg.getSource().equals(getSource());
	}

	public int hashCode() {
		return _source.hashCode();
	}

	public QueuedOperation(String src, String dest) {
		if (src == null) {
			throw new IllegalStateException("Source cannot be null");
		}
		this._source = src;
		this._destination = dest;
	}

	public String getSource() {
		return _source;
	}

	public String getDestination() {
		return _destination;
	}
}
