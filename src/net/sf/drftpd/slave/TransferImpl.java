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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import net.sf.drftpd.util.AddAsciiOutputStream;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: TransferImpl.java,v 1.41 2004/02/18 14:24:05 zubov Exp $
 */
public class TransferImpl extends UnicastRemoteObject implements Transfer {
	private static final Logger logger = Logger.getLogger(TransferImpl.class);
	private boolean _abort = false;
	private CRC32 _checksum = null;
	private Connection _conn;
	private char _direction;

	private InputStream _in;
	private OutputStream _out;
	private char _mode = 'I';
	private Socket _sock;

	private long _started = 0;
	private long _finished = 0;

	private long _transfered = 0;
	private SlaveImpl _slave;
	/**
	 * Start undefined passive transfer.
	 */
	public TransferImpl(Connection conn, SlaveImpl slave)
		throws RemoteException {
		super(0);
		_slave = slave;
		_direction = Transfer.TRANSFER_UNKNOWN;
		_conn = conn;
	}

	public void abort() throws RemoteException {
		_abort = true;
		if (_conn != null)
			_conn.abort();
		if (_sock == null )
			// already closed
			return;
		try {
			_sock.close();
		} catch (IOException e) {
			logger.warn("abort() failed to close() the socket", e);
		}
	}

	public TransferStatus sendFile(
		String path,
		char type,
		long resumePosition,
		boolean doChecksum)
		throws IOException {
		_direction = TRANSFER_SENDING_DOWNLOAD;

		_in = new FileInputStream(_slave.getRoots().getFile(path));
		if (doChecksum && _slave.getDownloadChecksums()) {
			_checksum = new CRC32();
			_in = new CheckedInputStream(_in, _checksum);
		}
		_in.skip(resumePosition);

		System.out.println("DL:" + path);
		transfer();
		return getStatus();
	}

	public long getChecksum() {
		if (_checksum == null)
			return 0;
		return _checksum.getValue();
	}

	public char getDirection() {
		return _direction;
	}

	public int getLocalPort() throws RemoteException {
		if (_conn instanceof PassiveConnection) {
			return ((PassiveConnection) _conn).getLocalPort();
		} else {
			throw new IllegalStateException("getLocalPort() called on a non-passive transfer");
		}
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
		return new TransferStatus(getElapsed(), getTransfered(), getChecksum());
	}

	public boolean isReceivingUploading() {
		return _direction == TRANSFER_RECEIVING_UPLOAD;
	}

	public boolean isSendingUploading() {
		return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
	}

	/**
	 * Call sock.connect() and start sending.
	 * 
	 * Read about buffers here:
	 * http://groups.google.com/groups?hl=sv&lr=&ie=UTF-8&oe=UTF-8&threadm=9eomqe%24rtr%241%40to-gate.itd.utech.de&rnum=22&prev=/groups%3Fq%3Dtcp%2Bgood%2Bbuffer%2Bsize%26start%3D20%26hl%3Dsv%26lr%3D%26ie%3DUTF-8%26oe%3DUTF-8%26selm%3D9eomqe%2524rtr%25241%2540to-gate.itd.utech.de%26rnum%3D22
	 * 
	 * Quote:
	 * Short answer is: if memory is not limited make your buffer big; TCP will flow
	 *   control itself and only use what it needs.
	 *
	 *Longer answer:  for optimal throughput (assuming TCP is not flow controlling itself
	 *  for other reasons) you want your buffer size to at least be
	 *
	 *  channel bandwidth * channel round-trip-delay.
	 *
	 *  So on a long slow link, if you can get 100K bps throughput,
	 *  but your delay -s 8 seconds, you want:
	 *
	 *   100Kbps * / bits-per-byte * 8 seconds
	 *   = 100 Kbytes
	 *
	 *  That way TCP can keep transmitting data for 8 seconds before it
	 *  would have to stop and wait for an ack (to clear space in the
	 *  buffer for new data so it can put new TX data in there and
	 *  on the line).  (The idea is to get the ack before you have to stop
	 *  transmitting.)
	 */
	private void transfer() throws IOException {
		_started = System.currentTimeMillis();
		_sock = _conn.connect();
		_conn = null;
		if (_in == null) {
			_sock.setReceiveBufferSize(65536);
			_in = _sock.getInputStream();
		} else if (_out == null) {
			_sock.setSendBufferSize(65536);
			_out = _sock.getOutputStream();
		} else {
			throw new IllegalStateException("neither in or out was null");
		}
		if (_mode == 'A') {
			_out = new AddAsciiOutputStream(_out);
		}

		_slave.addTransfer(this);
		try {
			byte[] buff = new byte[65536];
			int count;
			while ((count = _in.read(buff)) != -1 && !_abort) {
				_transfered += count;
				_out.write(buff, 0, count);
			}
			_out.flush();
		} finally {
			_finished = System.currentTimeMillis();
			_slave.removeTransfer(this);

			_in.close();
			_out.close();
			_sock.close();

			_in = null;
			_out = null;
			_sock = null;
		}
		if (_abort)
			throw new IOException("Transfer was aborted");
	}

	public TransferStatus receiveFile(
		String dirname,
		char mode,
		String filename,
		long offset)
		throws IOException {
		_direction = TRANSFER_RECEIVING_UPLOAD;
		try {
			_slave.getRoots().getFile(dirname + File.separator + filename);
			throw new IOException("File exists");
		} catch (FileNotFoundException ex) {
		}

		String root = _slave.getRoots().getARootFileDir(dirname).getPath();

		_out = new FileOutputStream(root + File.separator + filename);

		if (_slave.getUploadChecksums()) {
			_checksum = new CRC32();
			_out = new CheckedOutputStream(_out, _checksum);
		}
		System.out.println("UL:" + dirname + File.separator + filename);
		transfer();
		return getStatus();
	}
}
