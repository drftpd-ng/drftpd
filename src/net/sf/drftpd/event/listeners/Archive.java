/*
 * Created on Dec 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.DirectoryFtpEvent;
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
	private boolean _archiving;
	private ConnectionManager _cm;
	private long _cycleTime;
	private SlaveManagerImpl _sm;
	private long lastchecked = 0;

	private Logger logger = Logger.getLogger(Archive.class);

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
		System.out.println("We are now about to try and archive");
		_archiving = false;
		synchronized (this) {
			if (!_archiving)
				_archiving = true;
			else
				return;
		} // two instances of this running would not be a good thing
		System.out.println("We are now archiving");
		if (event.getTime() - lastchecked > _cycleTime) {
			// find the oldest release possible
			System.out.println("We are now looking for the oldest directory");
			LinkedRemoteFile oldDir =
				((DirectoryFtpEvent) event).getDirectory();
			LinkedRemoteFile root = oldDir.getRoot();
			oldDir = this.getOldestNonArchivedDir(root);
			System.out.println("The oldest Directory is " + oldDir.getPath());
			RemoteSlave slave = findDestinationSlave(oldDir);
			System.out.println("The slave to archive to is " + slave.getName());
			//			try {
			//				slave = _sm.getASlave(Transfer.TRANSFER_RECEIVING_UPLOAD);
			//			} catch (NoAvailableSlaveException e) {
			//				_archiving = false;
			//				return;
			//				//can't archive if there are no slaves online
			//			}
		}
		_archiving = false;
	}
	private RemoteSlave findDestinationSlave(LinkedRemoteFile lrf) {
		ArrayList slaveList = new ArrayList();
		for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
			Collection tempSlaveList =
				((LinkedRemoteFile) iter.next()).getSlaves();
			slaveList.addAll(tempSlaveList);
		}
		Collections.sort(slaveList);
		RemoteSlave highSlave = null;
		int highSlaveCount = 0;
		RemoteSlave slave = null;
		int slaveCount = 0;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			System.out.println("rslave = " + rslave.getName());
			if (highSlave == null) {
				highSlave = rslave;
				slave = rslave;
			}
			if (slave == rslave)
				slaveCount += 1;
			else {
				if (highSlaveCount < slaveCount) {
					highSlaveCount = slaveCount;
					highSlave = slave;
				}
				slaveCount = 1;
				slave = rslave;
			}
		}

		return highSlave;
	}

	private LinkedRemoteFile getOldestNonArchivedDir(LinkedRemoteFile lrf) {
		if (lrf.getDirectories().size() == 0) {
			Collection files = lrf.getFiles();
			ArrayList slaveList = new ArrayList();
			for (Iterator iter = files.iterator(); iter.hasNext();) {
				LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
				for (Iterator iter2 = temp.getSlaves().iterator();
					iter2.hasNext();
					) {
					RemoteSlave tempSlave = (RemoteSlave) iter2.next();
					if (!slaveList.contains(tempSlave))
						slaveList.add(tempSlave);
				}
			}
			if (slaveList.size() == 1)
				return null;
			return lrf;
		}
		ArrayList oldDirs = new ArrayList();
		for (Iterator iter = lrf.getDirectories().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFile temp =
				getOldestNonArchivedDir((LinkedRemoteFile) iter.next());
			// if temp == null all directories are archived
			if (temp != null)
				oldDirs.add(temp);
		}
		LinkedRemoteFile oldestDir = null;
		for (Iterator iter = oldDirs.iterator(); iter.hasNext();) {
			LinkedRemoteFile temp = (LinkedRemoteFile) iter.next();
			if (oldestDir == null) {
				oldestDir = temp;
				continue;
			}
			if (oldestDir.lastModified() > temp.lastModified()) {
				oldestDir = temp;
			}
		}
		return oldestDir;
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
		_sm = _cm.getSlaveManager();
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

}
