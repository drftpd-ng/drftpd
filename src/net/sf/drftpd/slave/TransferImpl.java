package net.sf.drftpd.slave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Collection;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;
import java.util.zip.CheckedOutputStream;

import net.sf.drftpd.AsciiOutputStream;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class TransferImpl extends UnicastRemoteObject implements Transfer {
	private boolean _abort = false;
	private CRC32 _checksum;
	private Connection _conn;
	private char _direction;

	private InputStream _in;
	private OutputStream _out;
	private RootBasket _roots;
	private Collection _transfers;
	private char _mode = 'I';
	private Socket _sock;

	private long _started = 0;
	private long _finished = 0;

	private long _transfered = 0;

	/**
	 * Receive/Upload, read from 'conn' and write to 'out'.
	 * @deprecated
	 */
	public TransferImpl(
		Collection transfers,
		Connection conn,
		OutputStream out)
		throws RemoteException {
		super();
		_direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
		_checksum = new CRC32();
		_conn = conn;
		_out = new CheckedOutputStream(out, _checksum);
		_transfers = transfers;
	}
	/**
	 * Start undefined passive transfer.
	 */
	public TransferImpl(Collection transfers, Connection conn, RootBasket roots) throws RemoteException {
		super();
		_direction = Transfer.TRANSFER_UNKNOWN;
		_conn = conn;
		_transfers = transfers;
		_roots = roots;
	}

	/**
	 * Send/Download, reading from 'in' and write to 'conn' using transfer type 'mode'.
	 * @deprecated
	 */
	public TransferImpl(
		Collection transfers,
		InputStream in,
		Connection conn,
		char mode)
		throws RemoteException {
		super();
		_direction = TRANSFER_SENDING_DOWNLOAD;
		_in = in;
		_conn = conn;
		_mode = mode;
		_transfers = transfers;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.slave.Transfer#abort()
	 */
	public void abort() throws RemoteException {
		_abort = true;
	}
	
	public void downloadFile(String path, char type, long resumePosition, boolean doChecksum) throws IOException {
		_direction = TRANSFER_SENDING_DOWNLOAD;
		
		_in = new FileInputStream(_roots.getFile(path));
		if(doChecksum) {
			_checksum = new CRC32();
			_in = new CheckedInputStream(_in, _checksum);
		}
		_in.skip(resumePosition);
		
		System.out.println("DL:"+path);
		transfer();
	}

	public long getChecksum() {
		return _checksum.getValue();
	}

	public char getDirection() {
		return _direction;
	}

	/**
	 * @deprecated
	 */
	public InetAddress getEndpoint() {
		return _sock.getInetAddress();
	}

	/**
	 * @see net.sf.drftpd.slave.Transfer#getLocalPort()
	 */
	public int getLocalPort() throws RemoteException {
		if (_conn instanceof PassiveConnection) {
			return ((PassiveConnection) _conn).getLocalPort();
		} else {
			throw new IllegalStateException("getLocalPort() called on a non-passive transfer");
		}
	}

	/**
	 * Returns the transfered.
	 * @return long
	 */
	public long getTransfered() {
		return _transfered;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.slave.Transfer#getTransferTime()
	 */
	public long getTransferTime() {
		if(_finished == 0) {
			return System.currentTimeMillis() - _started;
		} else {
			return _finished - _started;
		}
	}

	public int getXferSpeed() {
		long elapsed = getTransferTime();
		
		if (_transfered == 0) {
			return 0;
		}

		if (elapsed == 0) {
			return 0;
		}
		return (int) (_transfered / ((float) elapsed / (float) 1000));
	}

	public boolean isReceivingUploading() {
		return _direction == TRANSFER_RECEIVING_UPLOAD;
	}

	public boolean isSendingUploading() {
		return _direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
	}
	/**
	 * Call sock.connect() and start sending.
	 * @deprecated
	 */
	public void transfer() throws IOException {
		_started = System.currentTimeMillis();
		_sock = _conn.connect();
		if (_in == null) {
			_in = _sock.getInputStream();
		} else if (_out == null) {
			if (_mode == 'A') {
				_out = new AsciiOutputStream(_sock.getOutputStream());
			} else {
				_out = _sock.getOutputStream();
			}
		} else {
			throw new RuntimeException("neither in or out was null");
		}

		_transfers.add(this);
		try {
			byte[] buff = new byte[4096];
			int count;
			while ((count = _in.read(buff)) != -1 && !_abort) {
				_transfered += count;
				_out.write(buff, 0, count);
			}
			_out.close();
		} finally {
			_finished = System.currentTimeMillis();
			_transfers.remove(this);

			_in.close();
			_out.close();
			_sock.close();

			_in = null;
			_out = null;
			_conn = null;
		}
	}
	
	//TODO char mode for uploads?
	public void uploadFile(String dirname, String filename, long offset) throws IOException {
		_direction = TRANSFER_RECEIVING_UPLOAD;
		_checksum = new CRC32();

		String root = _roots.getARootFile(dirname).getPath();

		_out = new FileOutputStream(root + File.separator + filename);

		_out = new CheckedOutputStream(_out, _checksum);
		System.out.println("UL:"+dirname+File.separator+filename);
		transfer();
	}
}
