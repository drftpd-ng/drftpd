package net.sf.drftpd.slave;

import java.io.Serializable;

public class SlaveStatus implements Serializable {
	private long diskSpaceAvailable;
	private long diskSpaceCapacity;
	
	private int throughputReceiving;
	private int throughputSending;
	
	private int transfersSending;
	private int transfersReceiving;
	public SlaveStatus() {
		this.diskSpaceAvailable = 0;
		this.diskSpaceCapacity = 0;
		
		this.throughputReceiving = 0;
		this.throughputSending = 0;

		this.transfersSending = 0;
		this.transfersReceiving = 0;		
	}
	public SlaveStatus(long diskFree, long diskTotal, int throughputReceiving, int transfersReceiving, int throughputSending, int transfersSending) {
		this.diskSpaceAvailable = diskFree;
		this.diskSpaceCapacity = diskTotal;
		
		this.throughputReceiving = throughputReceiving;
		this.throughputSending = throughputSending;

		this.transfersSending = transfersSending;
		this.transfersReceiving = transfersReceiving;
	}
	
	public SlaveStatus append(SlaveStatus arg) {
		return new SlaveStatus(getDiskSpaceAvailable()+arg.getDiskSpaceAvailable(),
		getDiskSpaceCapacity()+arg.getDiskSpaceCapacity(),
		getThroughputReceiving()+arg.getThroughputReceiving(),
		getTransfersReceiving()+arg.getTransfersReceiving(),
		getThroughputSending()+arg.getThroughputSending(),
		getTransfersSending()+arg.getTransfersSending()
		);
	}
	public int getThroughput() {
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
	public int getThroughputSending() {
		return throughputSending;
	}

	/**
	 * Returns the throughputUp.
	 * @return float
	 */
	public int getThroughputReceiving() {
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
