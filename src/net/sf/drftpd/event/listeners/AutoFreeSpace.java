/*
 * Created on Jan 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveStatus;

import org.apache.log4j.Logger;

/**
 * @author zubov
 * @version $Id: AutoFreeSpace.java,v 1.3 2004/01/13 20:30:53 mog Exp $
 */
public class AutoFreeSpace implements FtpListener {

	private static final Logger logger = Logger.getLogger(AutoFreeSpace.class);
	private long _archiveAfter;

	private ConnectionManager _cm;
	private long _cycleTime;
	private int _keepCopies;
	private long _keepFree;
	private long _lastchecked = System.currentTimeMillis();

	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD"))
			reload();
		if (!(event instanceof TransferEvent))
			return;
		TransferEvent transevent = (TransferEvent) event;
		if (!transevent.getCommand().equals("STOR"))
			return;
		if (System.currentTimeMillis() - _lastchecked <= _cycleTime) {
			return;
		}
		_lastchecked = System.currentTimeMillis();
		Collection slaveList = null;
		try {
			slaveList = _cm.getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e) {
			// done, can't remove space from nonexistant slaves
		}
		while (true) {
			RemoteSlave rslave = _cm.getSlaveManager().findSmallestFreeSlave();
			SlaveStatus status = null;
			try {
				status = rslave.getStatus();
				if (_keepFree > status.getDiskSpaceAvailable()) {
					deleteOldReleases(rslave);
				} else {
					break;
				}
			} catch (RemoteException e1) {
				rslave.handleRemoteException(e1);
			} catch (NoAvailableSlaveException e1) {
			}
		}
	}
	private void deleteOldReleases(RemoteSlave slave) {

	}

	/**
	 * Deletes the oldest directories until _keepFree space is available.
	 * @param dirs
	 */
	private void deleteOldReleases(Collection dirs) {
		long spaceavailable =
			getConnectionManager()
				.getSlaveManager()
				.getAllStatus()
				.getDiskSpaceAvailable();
		if (spaceavailable < _keepFree) {
			for (Iterator iter = dirs.iterator(); iter.hasNext();) {
				LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
				file.delete();
			}
		}
	}

	private ConnectionManager getConnectionManager() {
		return _cm;
	}
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_cycleTime =
			60000 * Long.parseLong(FtpConfig.getProperty(props, "cycleTime"));
		_keepFree =
			1024
				* 1024
				* Long.parseLong(FtpConfig.getProperty(props, "keepFree"));
		_archiveAfter =
			60000
				* Long.parseLong(FtpConfig.getProperty(props, "archiveAfter"));
		_lastchecked = System.currentTimeMillis();
	}

}
