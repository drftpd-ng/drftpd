package net.sf.drftpd.slave;

import java.io.Serializable;

/**
 * @author mog
 * @version $Id: TransferStatus.java,v 1.2 2003/11/19 00:20:54 mog Exp $
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

	public long getElapsed() {
		return _elapsed;
	}

	public long getTransfered() {
		return _transfered;
	}

	public int getXferSpeed() {
		if (_transfered == 0)
			return 0;
		if (_elapsed == 0)
			return 0;
		return (int) (_transfered / ((float) _elapsed / (float) 1000));
	}

}
