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
 * @author mog
 * @version $Id$
 */
@SuppressWarnings("serial")
public class TransferStatus implements Serializable {
	private long _checksum;

	private long _elapsed;

	private boolean _isFinished;

	private Throwable _throwable;

	private long _transfered;

	private TransferIndex _transferIndex;

	public TransferStatus(long elapsed, long transfered, long checksum,
			boolean isFinished, TransferIndex transferIndex) {
		_transferIndex = transferIndex;
		_elapsed = elapsed;
		_transfered = transfered;
		_checksum = checksum;
		_isFinished = isFinished;
		_throwable = null;
	}

	public TransferStatus(TransferIndex transferIndex, Throwable t) {
		this(0, 0, 0, true, transferIndex);
		_throwable = t;
	}

	public long getChecksum() {
		return _checksum;
	}

	public long getElapsed() {
		return _elapsed;
	}

	public Throwable getThrowable() {
		return _throwable;
	}

	public long getTransfered() {
		return _transfered;
	}

	public TransferIndex getTransferIndex() {
		return _transferIndex;
	}

	public long getXferSpeed() {
		if (_transfered == 0) {
			return 0;
		}

		if (_elapsed == 0) {
			return 0;
		}

		return (long) (_transfered / ((float) _elapsed / (float) 1000));
	}

	public boolean isFinished() {
		return _isFinished;
	}

	public boolean threwException() {
		return (_throwable != null);
	}

	public String toString() {
		return "TransferStatus=[xferspeed=" + getXferSpeed() + "][transfered="
				+ getTransfered() + "][elapsed=" + getElapsed() + "]";
	}

}
