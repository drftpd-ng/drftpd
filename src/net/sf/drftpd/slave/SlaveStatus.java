package net.sf.drftpd.slave;

import java.io.Serializable;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class SlaveStatus implements Serializable {
	private long diskFree;
	private float throughputReceiving;
	private float throughputSending;
	
	private int transfersSending;
	private int transfersReceiving;
	
	public SlaveStatus(long diskFree, float throughputReceiving, int transfersReceiving, float throughputSending, int transfersSending) {
		this.diskFree = diskFree;
		
		this.throughputReceiving = throughputReceiving;
		this.throughputSending = throughputSending;

		this.transfersSending = transfersSending;
		this.transfersReceiving = transfersReceiving;
	}
	public float getThroughput() {
		return throughputReceiving+throughputSending;
	}
	public int getTransfers() {
		return transfersReceiving+transfersSending;
	}
	public String toString() {
		return "[SlaveStatus [diskFree: "+diskFree+"][receiving: "+throughputReceiving+"bps in "+transfersSending+"][sending "+throughputSending+"bps in "+transfersReceiving+"]]";
	}
	/**
	 * Returns the diskFree.
	 * @return long
	 */
	public long getDiskFree() {
		return diskFree;
	}

	/**
	 * Returns the throughputDown.
	 * @return float
	 */
	public float getThroughputSending() {
		return throughputSending;
	}

	/**
	 * Returns the throughputUp.
	 * @return float
	 */
	public float getThroughputReceiving() {
		return throughputReceiving;
	}

	/**
	 * Returns the transfersDown.
	 * @return int
	 */
	public int getTransfersReceiving() {
		return transfersReceiving;
	}

	/**
	 * Returns the transfersUp.
	 * @return int
	 */
	public int getTransfersSending() {
		return transfersSending;
	}

}
