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
 * @version $Id: SlaveStatus.java,v 1.12 2004/08/03 20:14:03 zubov Exp $
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

    public SlaveStatus(long diskFree, long diskTotal, long bytesSent,
        long bytesReceived, int throughputReceiving, int transfersReceiving,
        int throughputSending, int transfersSending) {
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
        return new SlaveStatus(getDiskSpaceAvailable() +
            arg.getDiskSpaceAvailable(),
            getDiskSpaceCapacity() + arg.getDiskSpaceCapacity(),
            getBytesSent() + arg.getBytesSent(),
            getBytesReceived() + arg.getBytesReceived(),
            getThroughputReceiving() + arg.getThroughputReceiving(),
            getTransfersReceiving() + arg.getTransfersReceiving(),
            getThroughputSending() + arg.getThroughputSending(),
            getTransfersSending() + arg.getTransfersSending());
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
        return getDiskSpaceCapacity() - getDiskSpaceAvailable();
    }

    public int getThroughput() {
        return _throughputReceiving + _throughputSending;
    }

    public int getThroughputReceiving() {
        return _throughputReceiving;
    }

    public int getThroughputSending() {
        return _throughputSending;
    }

    public int getTransfers() {
        return _transfersReceiving + _transfersSending;
    }

    public int getTransfersReceiving() {
        return _transfersReceiving;
    }

    public int getTransfersSending() {
        return _transfersSending;
    }

    public String toString() {
        return "[SlaveStatus [diskSpaceAvailable: " + _diskSpaceAvailable +
        "][receiving: " + _throughputReceiving + " bps, " + _transfersSending +
        " streams][sending: " + _throughputSending + " bps, " +
        _transfersReceiving + " streams]]";
    }

    public int getThroughputDirection(char c) {
        switch (c) {
        case Transfer.TRANSFER_RECEIVING_UPLOAD:
            return getThroughputReceiving();

        case Transfer.TRANSFER_SENDING_DOWNLOAD:
            return getThroughputSending();

        case Transfer.TRANSFER_THROUGHPUT:
            return getThroughput();

        default:
            throw new IllegalArgumentException();
        }
    }
}
