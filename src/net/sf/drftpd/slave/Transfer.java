package net.sf.drftpd.slave;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @author mog
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class Transfer {
	public static final char TRANSFER_RECEIVING='R';// TRANSFER_UPLOAD='R';
	public static final char TRANSFER_SENDING='S';// TRANSFER_DOWNLOAD='S';
	
	private int started=(int)System.currentTimeMillis()/1000;
	private long transfered=0;
	private InputStream in;
	private OutputStream out;
	private char direction;

	public Transfer(InputStream in, OutputStream out) {
		this.in = in;
		this.out = out;
	}
	
	public void transfer() throws IOException {
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
}
