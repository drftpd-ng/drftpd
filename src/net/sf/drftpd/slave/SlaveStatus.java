package net.sf.drftpd.slave;

import java.io.Serializable;

public class SlaveStatus implements Serializable {
	private long diskSpaceAvailable;
	private long diskSpaceCapacity;
	
	private int throughputReceiving;
	private int throughputSending;
	
	private int transfersSending;
	private int transfersReceiving;
	
	public SlaveStatus(long diskFree, long diskTotal, int throughputReceiving, int transfersReceiving, int throughputSending, int transfersSending) {
		this.diskSpaceAvailable = diskFree;
		this.diskSpaceCapacity = diskTotal;
		
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
		return "[SlaveStatus [diskSpaceAvailable: "+diskSpaceAvailable+"][receiving: "+throughputReceiving+" bps, "+transfersSending+" streams][sending: "+throughputSending+" bps, "+transfersReceiving+" streams]]";
	}
	
	/**
	 * Returns the diskFree.
	 * @return long
	 */
	public long getDiskSpaceAvailable() {
		return diskSpaceAvailable;
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

	/**
	 * @return
	 */
	public long getDiskSpaceCapacity() {
		return diskSpaceCapacity;
	}

}
