package net.sf.drftpd.slave;

import java.io.Serializable;

/**
 * @author mog
 * @version $Id: TransferStatus.java,v 1.1 2003/11/17 20:13:11 mog Exp $
 */
public class TransferStatus implements Serializable {
	private long _elapsed, _transfered;
	public TransferStatus(long elapsed, long transfered) {
		_elapsed = elapsed;
		_transfered = transfered;
	}

	public int getXferSpeed() {

		if (_transfered == 0) {
			return 0;
		}

		if (_elapsed == 0) {
			return 0;
		}
		return (int) (_transfered / ((float) _elapsed / (float) 1000));
	}

	public long getElapsed() {
		return _elapsed;
	}

	public long getTransfered() {
		return _transfered;
	}

}
