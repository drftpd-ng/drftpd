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
package org.drftpd.slave.socket;

import java.io.IOException;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.util.Hashtable;

import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferStatus;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: SocketTransferImpl.java,v 1.1 2004/05/20 20:17:24 zombiewoof64 Exp $
 */
public class SocketTransferImpl implements Transfer {

	private static final Logger logger = Logger.getLogger(SocketTransferImpl.class);

        private long        _conn;
        private String      _addr;
        private int         _port;
	
        private boolean     _abort = false;

        private char        _direction;
        private long        _started = 0;
        private long        _finished = 0;
        private long        _transfered = 0;
        private long        _checksum = 0;
        private long        _error = 0;
        private String      _status = "";
        
	private SocketSlaveImpl _slave;

        /**
	 * Start undefined passive transfer.
	 */
	public SocketTransferImpl(SocketSlaveImpl slave, Hashtable data)
		throws RemoteException 
        {
		_slave = slave;
		_direction = Transfer.TRANSFER_UNKNOWN;
		_conn = Long.parseLong((String)data.get("conn"));
                _port = 0;
                _addr = "";
                if (data.containsKey("addr")) {
                    String tmp = (String)data.get("addr");
                    String[] items = tmp.split(":");
                    _addr = items[0];
                    _port = Integer.parseInt(items[1]);
                }
	}

	public long getChecksum() {
            return _checksum;
	}

	public char getDirection() {
		return _direction;
	}

	public int getLocalPort() throws RemoteException {
            return _port;
	}

	public long getID() {
            return _conn;
	}

	public long getTransfered() {
		return _transfered;
	}

	public long getElapsed() {
		if (_finished == 0) {
			return System.currentTimeMillis() - _started;
		} else {
			return _finished - _started;
		}
	}

	public int getXferSpeed() {
		long elapsed = getElapsed();

		if (_transfered == 0) {
			return 0;
		}

		if (elapsed == 0) {
			return 0;
		}
		return (int) (_transfered / ((float) elapsed / (float) 1000));
	}

	public TransferStatus getStatus() {
		try {
			return new TransferStatus(getElapsed(), getTransfered(), getChecksum(), InetAddress.getLocalHost());
		} catch (Exception e) {
			return null;
		}
	}

	public boolean isReceivingUploading() {
		return _direction == TRANSFER_RECEIVING_UPLOAD;
	}

	public boolean isSendingUploading() {
		return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
	}

	public TransferStatus sendFile(
		String path,
		char type,
		long resumePosition
        ) throws IOException {
            Hashtable args = new Hashtable();
            
            logger.info("Send: path=" + path);

            _direction = TRANSFER_SENDING_DOWNLOAD;
            args.put("path", path);
            args.put("offs", Long.toString(resumePosition));
            args.put("conn", Long.toString(_conn));
            Hashtable data = _slave.doCommand("send", args);
            _started = System.currentTimeMillis();
            if (data != null) {
                _slave.addTransfer(this);
                while (!_status.equals("C")) {
                    try { wait(200); } catch (Exception e) {}
                }
                _slave.removeTransfer(this);
            }
            TransferStatus tmp = getStatus();
            return tmp;
	}

	public synchronized TransferStatus receiveFile(
		String dirname,
		char mode,
		String filename,
		long offset
        ) throws IOException {
            Hashtable args = new Hashtable();

            logger.info("Recv: path=" + dirname + "/" + filename);

            _direction = TRANSFER_RECEIVING_UPLOAD;
            args.put("path", dirname + "/" + filename);
            args.put("offs", Long.toString(offset));
            args.put("conn", Long.toString(_conn));
            Hashtable data = _slave.doCommand("recv", args);
            _started = System.currentTimeMillis();
            if (data != null) {
                _status = "I";
                _slave.addTransfer(this);
                while (!_status.equals("C")) {
                    try { wait(200); } catch (Exception e) {}
                }
                _slave.removeTransfer(this);
            }
            TransferStatus tmp = getStatus();
            return tmp;
	}

      	public void abort() throws RemoteException {
            _abort = true;
            Hashtable args = new Hashtable();
            args.put("conn", Long.toString(_conn));
            Hashtable data = _slave.doCommand("abrt", args);
	}


        public void updateStats(String sta, long byt, long crc, long err, String addr)
        {
            _status = sta;
            _transfered = byt;
            _checksum = crc;
            _error = err;
		_addr = addr;
        }
}
