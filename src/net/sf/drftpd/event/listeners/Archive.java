/*
 * Created on Dec 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;

/**
 * @author zubov
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Archive implements FtpListener {

	private Logger logger = Logger.getLogger(Archive.class);
	private boolean _archiving;
	private ConnectionManager _cm;
	private SlaveManagerImpl _sm;
	private long _cycleTime;
	private long lastchecked = 0;
	
	/**
	 * 
	 */
	public Archive() {
		reload();
		logger.info("Archive plugin loaded successfully");
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.Event)
	 */
	public void actionPerformed(Event event) {
		if (!(event instanceof TransferEvent))
			return;
		if ( !(_cm.getSlaveManager() instanceof SlaveManagerImpl)) {
			if (_sm == null)
				_sm = _cm.getSlaveManager();
		}
		synchronized(this) {
			if (!_archiving)
				_archiving = true;
			else return; 
		}// two instances of this running would not be a good thing
		if ( event.getTime() - lastchecked > _cycleTime) {
			// find the oldest release possible
			LinkedRemoteFile oldDirectory;
			RemoteSlave slave;
			try {
				slave = _sm.getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
			} catch (NoAvailableSlaveException e) {
				_archiving = false;
				return;
				//can't archive if there are no slaves online
			}
			
		}
		_archiving = false;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_cycleTime = Long.parseLong(props.getProperty("cycleTime"));
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}

}
