package net.sf.drftpd.slave;

import java.io.Serializable;

/**
 * @author mog
 * @version $Id: SlaveStatus.java,v 1.9 2003/11/17 21:57:08 mog Exp $
 */
public class SlaveStatus implements Serializable {
	static final long serialVersionUID = -5171512270937436414L;
	private long _bytesReceived;
	private long _bytesSent;
	private long _diskSpaceAvailable;
	private long _diskSpaceCapacity;
	
	private int _throughputReceiving;
	private int _throughputSending;
	private int _transfersReceiving;
	
	private int _transfersSending;
	public SlaveStatus() {
		_diskSpaceAvailable = 0;
		_diskSpaceCapacity = 0;
		
		_throughputReceiving = 0;
		_throughputSending = 0;

		_transfersSending = 0;
		_transfersReceiving = 0;		
	}
	public SlaveStatus(long diskFree, long diskTotal, long bytesSent, long bytesReceived, int throughputReceiving, int transfersReceiving, int throughputSending, int transfersSending) {
		_diskSpaceAvailable = diskFree;
		_diskSpaceCapacity = diskTotal;
		
		_bytesSent = bytesSent;
		_bytesReceived = bytesReceived;
		
		_throughputReceiving = throughputReceiving;
		_throughputSending = throughputSending;

		_transfersSending = transfersSending;
		_transfersReceiving = transfersReceiving;
	}
	
	public SlaveStatus append(SlaveStatus arg) {
		return new SlaveStatus(getDiskSpaceAvailable()+arg.getDiskSpaceAvailable(),
		getDiskSpaceCapacity()+arg.getDiskSpaceCapacity(),
		getBytesSent()+arg.getBytesSent(),
		getBytesReceived()+arg.getBytesReceived(),
		getThroughputReceiving()+arg.getThroughputReceiving(),
		getTransfersReceiving()+arg.getTransfersReceiving(),
		getThroughputSending()+arg.getThroughputSending(),
		getTransfersSending()+arg.getTransfersSending()
		);
	}

	public long getBytesReceived() {
		return _bytesReceived;
	}

	public long getBytesSent() {
		return _bytesSent;
	}
	
	public long getDiskSpaceAvailable() {
		return _diskSpaceAvailable;
	}

	public long getDiskSpaceCapacity() {
		return _diskSpaceCapacity;
	}

	public long getDiskSpaceUsed() {
		return getDiskSpaceCapacity()-getDiskSpaceAvailable();
	}
	public int getThroughput() {
		return _throughputReceiving+_throughputSending;
	}

	public int getThroughputReceiving() {
		return _throughputReceiving;
	}

	public int getThroughputSending() {
		return _throughputSending;
	}
	
	public int getTransfers() {
		return _transfersReceiving+_transfersSending;
	}

	public int getTransfersReceiving() {
		return _transfersReceiving;
	}

	public int getTransfersSending() {
		return _transfersSending;
	}
	
	public String toString() {
		return "[SlaveStatus [diskSpaceAvailable: "+_diskSpaceAvailable+"][receiving: "+_throughputReceiving+" bps, "+_transfersSending+" streams][sending: "+_throughputSending+" bps, "+_transfersReceiving+" streams]]";
	}
}
