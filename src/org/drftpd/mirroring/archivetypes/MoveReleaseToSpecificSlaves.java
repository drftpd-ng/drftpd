/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.mirroring.archivetypes;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.sections.SectionInterface;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author zubov
 * @version $Id: MoveReleaseToSpecificSlaves.java,v 1.1 2004/05/16 05:44:55 zubov Exp $
 */
public class MoveReleaseToSpecificSlaves extends ArchiveType {

	private HashSet _destSlaves;
	private static final Logger logger = Logger.getLogger(MoveReleaseToSpecificSlaves.class);
	private int _numOfSlaves;
	
	public MoveReleaseToSpecificSlaves(Archive archive, SectionInterface section) {
		super(archive,section);
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_destSlaves = new HashSet();
		for (int i = 1;; i++) {
			String slavename = null;
			try {
				slavename = FtpConfig.getProperty(props, getSection().getName() + ".slavename." + i);
			} catch (NullPointerException e) {
				_numOfSlaves = i-1;
				break; // done
			}
			try {
				_destSlaves.add(_parent.getConnectionManager().getSlaveManager().getSlave(slavename));
			} catch (ObjectNotFoundException e) {
				logger.debug("Unable to get slave " + slavename + " from the SlaveManager");
			}
		}
		if (_destSlaves.isEmpty()) {
			throw new NullPointerException("Cannot continue, 0 destination slaves found for MoveReleaseToSpecificSlave for section " + getSection().getName());
		}
	}

	public HashSet findDestinationSlaves() {
		return _destSlaves;
	}

	public void cleanup(ArrayList jobList) {
		for (Iterator iter = jobList.iterator(); iter.hasNext();) {
			Job job = (Job) iter.next();
			job.getFile().deleteOthers(getRSlaves());
		}
	}

	protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
	throws IncompleteDirectoryException, OfflineSlaveException {
		return isArchivedToXSlaves(lrf,_numOfSlaves);
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
}
