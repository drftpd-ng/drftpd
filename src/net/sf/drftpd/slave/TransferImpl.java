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

	private long started = 0, finished = 0;

	private long transfered = 0;
	private char direction;

	private InputStream in;
	private OutputStream out;
	private Connection conn;
	private Socket sock;
	private char mode = 'I';
	private Collection transfers;
	private CRC32 checksum;

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
		direction = TRANSFER_SENDING_DOWNLOAD;
		this.in = in;
		this.conn = conn;
		this.mode = mode;
		this.transfers = transfers;
	}

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
		this.direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
		this.checksum = new CRC32();
		this.conn = conn;
		this.out = new CheckedOutputStream(out, this.checksum);
		this.transfers = transfers;
	}
	private RootBasket roots;
	/**
	 * Start undefined passive transfer.
	 */
	public TransferImpl(Collection transfers, Connection conn, RootBasket roots) throws RemoteException {
		super();
		this.direction = Transfer.TRANSFER_UNKNOWN;
		this.conn = conn;
		this.transfers = transfers;
		this.roots = roots;
	}
	
	//TODO char mode for uploads?
	public void uploadFile(String dirname, String filename, long offset) throws IOException {
		this.direction = TRANSFER_RECEIVING_UPLOAD;
		this.checksum = new CRC32();

		String root = roots.getARoot(dirname).getPath();

		FileOutputStream out = new FileOutputStream(root + File.separator + filename);

		this.out = new CheckedOutputStream(out, this.checksum);
		transfer();
	}
	
	public void downloadFile(String path, char type, long resumePosition) throws IOException {
		this.direction = TRANSFER_SENDING_DOWNLOAD;
		
		this.in = new FileInputStream(roots.getFile(path));
		this.in.skip(resumePosition);

		transfer();
	}
	/**
	 * Call sock.connect() and start sending.
	 * @deprecated
	 */
	public void transfer() throws IOException {
		this.transfers.add(this);
		this.started = System.currentTimeMillis();
		this.sock = conn.connect();
		this.sock.setSoTimeout(600);
		if (in == null) {
			this.in = sock.getInputStream();
		} else if (out == null) {
			if (this.mode == 'A') {
				out = new AsciiOutputStream(sock.getOutputStream());
			} else {
				out = sock.getOutputStream();
			}
		} else {
			transfers.remove(this);
			throw new RuntimeException("neither in or out was null");
		}

		try {
			byte[] buff = new byte[1024];
			int count;
			while ((count = in.read(buff)) != -1) {
				this.transfered += count;
				out.write(buff, 0, count);
			}
			out.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		} finally {
			finished = System.currentTimeMillis();
			transfers.remove(this);

			out.close();
			in.close();
			sock.close();

			in = null;
			out = null;
			conn = null;
		}
	}

	public boolean isSendingUploading() {
		return direction == Transfer.TRANSFER_SENDING_DOWNLOAD;
	}

	public char getDirection() {
		return direction;
	}

	/**
	 * @deprecated
	 */
	public InetAddress getEndpoint() {
		return sock.getInetAddress();
	}

	public long getChecksum() {
		return this.checksum.getValue();
	}

	public boolean isReceivingUploading() {
		return direction == TRANSFER_RECEIVING_UPLOAD;
	}

	public int getTransferSpeed() {
		long elapsed = System.currentTimeMillis() - started;
		if (this.transfered == 0) {
			return 0;
		}
		if (elapsed == 0) {
			return 0;
		}
		return (int) (this.transfered / ((float) elapsed / (float) 1000));
	}

	/**
	 * @see net.sf.drftpd.slave.Transfer#getLocalPort()
	 */
	public int getLocalPort() throws RemoteException {
		if (conn instanceof PassiveConnection) {
			return ((PassiveConnection) conn).getLocalPort();
		} else {
			throw new IllegalStateException("getLocalPort() called on a non-passive transfer");
		}
	}

	/**
	 * Returns the transfered.
	 * @return long
	 */
	public long getTransfered() {
		return this.transfered;
	}
}
