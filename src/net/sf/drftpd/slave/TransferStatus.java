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
package net.sf.drftpd.slave;

import java.io.Serializable;

/**
 * @author mog
 * @version $Id: TransferStatus.java,v 1.4 2004/02/10 00:03:31 mog Exp $
 */
public class TransferStatus implements Serializable {
	private long _checksum;
	private long _elapsed, _transfered;
	public TransferStatus(long elapsed, long transfered, long checksum) {
		_elapsed = elapsed;
		_transfered = transfered;
		_checksum = checksum;
	}

	public long getChecksum() {
		return _checksum;
	}

	/**
	 * @see TransferImpl#getElapsed()
	 */
	public long getElapsed() {
		return _elapsed;
	}

	/**
	 * @see TransferImpl#getTransfered()
	 */
	public long getTransfered() {
		return _transfered;
	}

	/**
	 * @see TransferImpl#getXferSpeed()
	 */
	public int getXferSpeed() {
		if (_transfered == 0)
			return 0;
		if (_elapsed == 0)
			return 0;
		return (int) (_transfered / ((float) _elapsed / (float) 1000));
	}

}
