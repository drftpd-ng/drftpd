package net.sf.drftpd.slave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.Socket;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import net.sf.drftpd.AsciiOutputStream;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class TransferImpl extends UnicastRemoteObject implements Transfer {
	public static final char TRANSFER_RECEIVING='R';// TRANSFER_UPLOAD='R';
	public static final char TRANSFER_SENDING='S';// TRANSFER_DOWNLOAD='S';
	
	private int started;
	private long transfered=0;
	private char direction;
	
	private InputStream in;
	private OutputStream out;
	private Connection conn;
	private Socket sock;
	private char mode='I';
	
	/**
	 * Read from 'in' and write to 'conn'.
	 */
	public TransferImpl(InputStream in, Connection conn) throws RemoteException {
		super();
		direction = TRANSFER_SENDING;
		this.in = in;
		this.conn = conn;
	}
	
	/**
	 * Read from 'in' and write to 'conn' using transfer type 'mode'.
	 */
	public TransferImpl(InputStream in, Connection conn, char mode) throws RemoteException {
		this(in, conn);
		this.mode = mode;
	}

	/**
	 * Read from 'conn' and write to 'out'.
	 */
	public TransferImpl(Connection conn, OutputStream out) throws RemoteException {
		super();
		direction = TRANSFER_RECEIVING;
		this.out = out;
		this.conn = conn;
	}
	
	
	public void transfer() throws IOException {
		started=(int)System.currentTimeMillis()/1000;
			sock = conn.connect();
		if(in == null) {
			System.out.println("Receiving binary stream from "+sock.getInetAddress()+":"+sock.getPort());
			in = sock.getInputStream();
		} else if(out == null) {
			System.out.println("Sending "+mode+" stream to "+sock.getInetAddress()+":"+sock.getPort());
			if(mode == 'A') {
				out = new AsciiOutputStream(sock.getOutputStream());
			} else {
				out = sock.getOutputStream();
			}
		} else {
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
	
	public float getTransferSpeed() {
		return transfered/(System.currentTimeMillis()/1000);
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
}
