/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.mirroring.archivetypes;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import org.apache.log4j.Logger;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.sections.SectionInterface;
/**
 * @author zubov
 * @version $Id: MoveReleaseOffSlavesToMostFreeSlaves.java,v 1.1 2004/05/22 15:08:43 zubov Exp $
 */
public class MoveReleaseOffSlavesToMostFreeSlaves extends ArchiveType {
	private static final Logger logger = Logger
			.getLogger(MoveReleaseOffSlavesToMostFreeSlaves.class);
	private HashSet _offOfSlaves;
	private int _numOfSlaves;
	public MoveReleaseOffSlavesToMostFreeSlaves(Archive archive,
			SectionInterface section) {
		super(archive, section);
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_offOfSlaves = new HashSet();
		for (int i = 1;; i++) {
			String slavename = null;
			try {
				slavename = FtpConfig.getProperty(props, getSection().getName()
						+ ".offOfSlave." + i);
			} catch (NullPointerException e) {
				break; // done
			}
			try {
				_offOfSlaves.add(_parent.getConnectionManager()
						.getSlaveManager().getSlave(slavename));
			} catch (ObjectNotFoundException e) {
				logger.debug("Unable to get slave " + slavename
						+ " from the SlaveManager");
			}
		}
		if (_offOfSlaves.isEmpty()) {
			throw new NullPointerException(
					"Cannot continue, 0 slaves found to move off MoveReleaseOffSlavesToMostFreeSlaves for for section "
							+ getSection().getName());
		}
		try {
			_numOfSlaves = Integer.parseInt(FtpConfig.getProperty(props,
					getSection().getName() + ".numOfSlaves"));
		} catch (NullPointerException e) {
			_numOfSlaves = 1;
		}
	}
	public void cleanup(ArrayList jobList) {
		for (Iterator iter = jobList.iterator(); iter.hasNext();) {
			Job job = (Job) iter.next();
			job.getFile().deleteOthers(getRSlaves());
		}
	}
	public HashSet findDestinationSlaves() {
		HashSet set = _parent.getConnectionManager().getSlaveManager().findSlavesBySpace(_numOfSlaves,_offOfSlaves,false); 
		if (set.isEmpty())
			return null;
		return set;
		
	}
	protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
			throws IncompleteDirectoryException, OfflineSlaveException {
		for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter
					.next();
			if (file.isDirectory()) {
				if (!isArchivedDir(file)) {
					return false;
				}
			} else {
				try {
					for (Iterator iter2 = file.getAvailableSlaves().iterator(); iter2
							.hasNext();) {
						RemoteSlave rslave = (RemoteSlave) iter2.next();
						if (_offOfSlaves.contains(rslave))
							return false;
					}
				} catch (NoAvailableSlaveException e) {
					throw new OfflineSlaveException(
							"There were no available slaves for "
									+ file.getPath());
				}
			}
		}
		return true;
	}
	public void waitForSendOfFiles(ArrayList jobQueue) {
		while (true) {
			for (Iterator iter = jobQueue.iterator(); iter.hasNext();) {
				Job job = (Job) iter.next();
				if (job.isDone()) {
					logger.debug(
						"File "
							+ job.getFile().getPath()
							+ " is done being sent");
					iter.remove();
				}
			}
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}
			if (jobQueue.isEmpty()) {
				break;
			}
		}
	}
	public String toString() {
		return "MoveReleaseOffSlavesToMostFreeSlaves=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}
}