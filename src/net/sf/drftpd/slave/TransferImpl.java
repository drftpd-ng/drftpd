package net.sf.drftpd.slave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

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
	
	private long started=0, finished=0;
	
	private long transfered=0;
	private char direction;
	
	private InputStream in;
	private OutputStream out;
	private Connection conn;
	private Socket sock;
	private char mode='I';
	private Collection transfers;
	private CRC32 checksum;
	
	/**
	 * Send, reading from 'in' and write to 'conn' using transfer type 'mode'.
	 * 
	 */
	public TransferImpl(Collection transfers, InputStream in, Connection conn, char mode) throws RemoteException {
		super();
		direction = TRANSFER_SENDING;
		this.in = in;
		this.conn = conn;
		this.mode = mode;
		this.transfers = transfers;
	}

	/**
	 * Receive, read from 'conn' and write to 'out'.
	 */
	public TransferImpl(Collection transfers, Connection conn, OutputStream out) throws RemoteException {
		super();
		direction = TRANSFER_RECEIVING;
		checksum = new CRC32();
		this.conn = conn;
		this.out = new CheckedOutputStream(out, checksum);
		this.transfers = transfers;
	}
	
	/**
	 * Call sock.connect() and start sending.
	 */
	public void transfer() throws IOException {
		transfers.add(this);
		started=System.currentTimeMillis();
			sock = conn.connect();
		if(in == null) {
			System.out.println("Receiving "+mode+" stream from "+sock.getInetAddress()+":"+sock.getPort());
			in = sock.getInputStream();
		} else if(out == null) {
			System.out.println("Sending "+mode+" stream to "+sock.getInetAddress()+":"+sock.getPort());
			if(mode == 'A') {
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
				transfered += count;
				out.write(buff, 0, count);
			}
			out.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		finished=System.currentTimeMillis();
		transfers.remove(this);
		in=null;
		out=null;
		conn=null;
	}
	
	public boolean isSending() {
		return direction == TRANSFER_SENDING;
	}
	
	public char getDirection() {
		return direction;	
	}
	
	public boolean isReceiving() {
		return direction == TRANSFER_RECEIVING;
	}
	
	public int getTransferSpeed() {
		return (int) (transfered/((System.currentTimeMillis()-started)/1000));
	}
	
	/**
	 * @see net.sf.drftpd.slave.Transfer#getLocalPort()
	 */
	public int getLocalPort() throws RemoteException {
		if(conn instanceof PassiveConnection) {
			return ((PassiveConnection)conn).getLocalPort();
		} else {
			throw new IllegalStateException("getLocalPort() called on a non-passive transfer");
		}
	}
	
	/**
	 * Returns the transfered.
	 * @return long
	 */
	public long getTransfered() {
		return transfered;
	}
}
