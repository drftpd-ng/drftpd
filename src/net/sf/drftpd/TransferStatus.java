package net.sf.drftpd;

import java.io.Serializable;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change this generated comment edit the template variable "typecomment":
 * Window>Preferences>Java>Templates.
 * To enable and disable the creation of type comments go to
 * Window>Preferences>Java>Code Generation.
 */
public class TransferStatus implements Serializable {
	private long transfered;
	private int elapsedTime;
	private String filename;
	
	public TransferStatus(String filename, int elapsedTime, long transfered) {
		this.filename = filename;
		this.elapsedTime = elapsedTime;
		this.transfered = transfered;
	}
	
	public int getElapsedTime() {
		return elapsedTime;
	}
	
	public float getTransferSpeed() {
		return transfered/elapsedTime;
	}
}
