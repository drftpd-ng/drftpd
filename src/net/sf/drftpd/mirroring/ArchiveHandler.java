/*
 * Created on Dec 5, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author zubov
 * @version $Id: ArchiveHandler.java,v 1.1 2003/12/11 18:19:26 zubov Exp $
 */
public class ArchiveHandler extends Thread {
	private boolean _archiving;
	private DirectoryFtpEvent _dirEvent;
	private Archive _parent;

	private Logger logger = Logger.getLogger(ArchiveHandler.class);

	public ArchiveHandler(DirectoryFtpEvent dirEvent, Archive archive) {
		reload();
		_dirEvent = dirEvent;
		_parent = archive;
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
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		// archive slave list
	}
	/* (non-Javadoc)
	 * @see java.lang.Runnable#run()
	 */
	public void run() {
		synchronized (_parent) {
			if (!_archiving)
				_archiving = true;
			else
				return;
		}
		LinkedRemoteFile oldDir = _dirEvent.getDirectory();
		LinkedRemoteFile root = oldDir.getRoot();
		oldDir = getOldestNonArchivedDir(root);
		if (oldDir == null)
			return; //everything is archived
		logger.info("The oldest Directory is " + oldDir.getPath());
		RemoteSlave slave = findDestinationSlave(oldDir);
		logger.info("The slave to archive to is " + slave.getName());
		for (Iterator iter = oldDir.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile src = (LinkedRemoteFile) iter.next();
			AbstractJob job = null;
			if (!src.getSlaves().contains(slave)) {
				ArrayList tempList = new ArrayList();
				tempList.add(slave);
				job = new AbstractJob(src, tempList,this,null,3);
				_parent.getConnectionManager().getJobManager().addJob(job);
			} else {
				src.deleteOthers(slave);
				src.getSlaves().clear();
				src.addSlave(slave);
				continue;
			}
			while (!_parent.getConnectionManager().getJobManager().isDone(job))
				Thread.yield();
			src.deleteOthers(slave);
			src.getSlaves().clear();
			src.addSlave(slave);
		}
		logger.info("Done archiving " + oldDir.getPath());
		_archiving = false;
	}

}
