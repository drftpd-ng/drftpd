/*
 * Created on Dec 2, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.util;

import java.io.IOException;

import org.apache.log4j.Logger;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.TransferThread;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author matt
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class CooperativeSlaveTransfer extends Thread {

	private LinkedRemoteFile _lrf;
	private RemoteSlave _rs;
	private Logger logger = Logger.getLogger(CooperativeSlaveTransfer.class);
	private int _numoftries;
	
	/**
	 * 
	 */
	public CooperativeSlaveTransfer(LinkedRemoteFile lrf, RemoteSlave rs, int numoftries) {
		_lrf = lrf;
		_rs = rs;
		_numoftries = numoftries;
	}

	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		boolean completed = false;
		for(int x = 0; x<_numoftries; x++) {
			if (completed) {
				logger.info("Successfully mirrored " + _lrf.getName() + " to " + _rs.getName());
				break;
			} 
			//logger.info("About to send " + _lrf.getName() + " to " + _rs.getName());
			try {
				new TransferThread(_lrf,_rs).transfer();
				completed = true;
			} catch (IOException e) {
				if (!e.getMessage().equals("File exists"))
					completed = true;
			} catch (Exception e) {
				logger.error("Error mirroring " + _lrf.getName() + " to " + _rs.getName(), e);
				completed = true;
			}
		}
	}

}