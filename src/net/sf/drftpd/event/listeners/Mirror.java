/*
 * Created on Dec 2, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.CooperativeSlaveTransfer;

import org.apache.log4j.Logger;

/**
 * @author zubov
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Mirror implements FtpListener {

	private Logger logger = Logger.getLogger(Mirror.class);
	
	private ConnectionManager _cm;
	private int _numberOfMirrors;
	private int _numberOfTries;

	public Mirror() {
		reload();		
		logger.info("Mirror plugin loaded successfully");
	}
	
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("mirror.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_numberOfMirrors = Integer.parseInt(props.getProperty("numberOfMirrors"));
		_numberOfTries = Integer.parseInt(props.getProperty("numberOfTries"));
	}

	public void actionPerformed(Event event) {
		if ( !(event instanceof TransferEvent))
				return;
		TransferEvent transevent = (TransferEvent) event;
		if ( !transevent.getCommand().equals("STOR"))
			return;
		if ( _numberOfMirrors < 2 ) return;
		LinkedRemoteFile dir;
		System.out.println("Command = " + transevent.getCommand());
		dir = transevent.getDirectory();
		RemoteSlave destrslave = null;
		for (int x = 1; x<_numberOfMirrors; x++){ // already have one copy sent
			try {
				destrslave = _cm.getSlaveManager().getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
			} catch (NoAvailableSlaveException e1) {
				System.out.println("Failed on getting destination slave");
				// what should i do if there's no slave to transfer to?
				e1.printStackTrace();
				return;
			}
			System.out.println("Sending file " + dir.getPath() + " to " + destrslave.getName());
			new CooperativeSlaveTransfer(dir,destrslave,_numberOfTries).start();
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}
}
