/*
 * Created on Dec 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
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
import net.sf.drftpd.util.CooperativeSlaveTransfer;

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
		//System.out.println("We are now about to try and archive");
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
			//System.out.println("We are now looking for the oldest directory");
			LinkedRemoteFile oldDir =
				((DirectoryFtpEvent) event).getDirectory();
			LinkedRemoteFile root = oldDir.getRoot();
			oldDir = getOldestNonArchivedDir(root);
			if (oldDir == null)
				return; //everything is archived
			System.out.println("The oldest Directory is " + oldDir.getPath());
			RemoteSlave slave = findDestinationSlave(oldDir);
			System.out.println("The slave to archive to is " + slave.getName());
			for (Iterator iter = oldDir.getFiles().iterator();
				iter.hasNext();
				) {
				LinkedRemoteFile src = (LinkedRemoteFile) iter.next();
				CooperativeSlaveTransfer temp = null;
				if (!src.getSlaves().contains(slave)) {
					temp = new CooperativeSlaveTransfer(src, slave, 3);
				} else {
					src.deleteOthers(slave);
					src.getSlaves().clear();
					src.addSlave(slave);
					continue;
				}
				temp.start();
				while (temp.isAlive())
					Thread.yield();
				src.deleteOthers(slave);
				src.getSlaves().clear();
				src.addSlave(slave);
			}
		}
		_archiving = false;
		System.out.println("at the end of archive");
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
		int x = 0;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
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
			x++;
			//System.out.println(x + ": slave = " + rslave.getName() + ", " + slaveCount);
		}

		return highSlave;
	}

	private LinkedRemoteFile getOldestNonArchivedDir(LinkedRemoteFile lrf) {
		if (lrf.getDirectories().size() == 0) {
			Collection files = lrf.getFiles();
			if (files.size() == 0)
				return null;
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
				// for testing			if (oldestDir.lastModified() < temp.lastModified()) {
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
