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
package org.drftpd.mirroring;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.drftpd.mirroring.archivetypes.IncompleteDirectoryException;
import org.drftpd.mirroring.archivetypes.OfflineSlaveException;
import org.drftpd.sections.SectionInterface;


import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author zubov
 * @version $Id: ArchiveType.java,v 1.3 2004/04/26 21:41:53 zubov Exp $
 */
public abstract class ArchiveType {
	private long _archiveAfter;
	private static final Logger logger = Logger.getLogger(ArchiveType.class);
	private LinkedRemoteFileInterface _lrf;
	protected Archive _parent;
	protected SectionInterface _section;
	private ArrayList _slaveList;

	public ArchiveType() {
	}
	/**
	 * Once the Jobs in the jobList have been sent, this method is called
	 * This is where files are possibly deleted from slaves
	 */
	
	public abstract void cleanup(ArrayList jobList);
	public abstract ArrayList findDestinationSlaves();

	public final LinkedRemoteFileInterface getDirectory() {
		return _lrf;
	}
	/**
	 * Returns the oldest LinkedRemoteFile(directory) that needs to be archived by this type's definition
	 * If no such directory exists, it returns null
	 */
	public final LinkedRemoteFileInterface getOldestNonArchivedDir() {
		if (_parent.checkExclude(getSection())) {
			return null;
		}
		ArrayList oldDirs = new ArrayList();
		for (Iterator iter = getSection().getFile().getFiles().iterator();
			iter.hasNext();
			) {
			LinkedRemoteFileInterface lrf =
				(LinkedRemoteFileInterface) iter.next();
			try {
				if(!isArchivedDir(lrf)) {
					if (System.currentTimeMillis() - lrf.lastModified()
						> getArchiveAfter()) {
						oldDirs.add(lrf);
					}
				}
			} catch (IncompleteDirectoryException e) {
				continue;
			} catch (OfflineSlaveException e) {
				continue;
			}
		}
		LinkedRemoteFileInterface oldestDir = null;
		for (Iterator iter = oldDirs.iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface temp =
				(LinkedRemoteFileInterface) iter.next();
			if (oldestDir == null) {
				oldestDir = temp;
				continue;
			}
			if (oldestDir.lastModified() > temp.lastModified()) {
				oldestDir = temp;
			}
		}
		if (oldestDir != null)
			logger.debug("Returning the oldest directory " + oldestDir);
		else logger.debug("Returning a null directory");
		return oldestDir;
	}
	/**
	 * if the file needs to be archived by this type's definition, this method returns true
	 */
	protected abstract boolean isArchivedDir(LinkedRemoteFileInterface lrf) throws IncompleteDirectoryException, OfflineSlaveException;

	public final ArrayList getRSlaves() {
		return _slaveList;
	}
	
	protected final long getArchiveAfter() {
		return _archiveAfter;
	}

	public final SectionInterface getSection() {
		if (_section == null)
			throw new IllegalStateException("setSection() needs to be called before getSection()");
		return _section;
	}

	public final void init(Archive archive, SectionInterface section) {
		_parent = archive;
		setSection(section);
		setProperties(_parent.getProperties());
	}
	
	private void setProperties(Properties properties) {
		try {
			_archiveAfter =
				60000 * Long.parseLong(FtpConfig.getProperty(properties, getSection().getName() + ".archiveAfter"));
		} catch (NullPointerException e) {
			_archiveAfter =
							60000 * Long.parseLong(FtpConfig.getProperty(properties, "default.archiveAfter"));
		}
	}
	/**
	 * Adds relevant Jobs to the JobManager and returns an ArrayList of those Job's
	 */
	public abstract ArrayList send();
	public final void setDirectory(LinkedRemoteFileInterface lrf) {
		_lrf = lrf;
	}

	public final void setRSlaves(ArrayList slaveList) {
		_slaveList = slaveList;
	}

	public final void setSection(SectionInterface section) {
		_section = section;
	}

	public abstract void waitForSendOfFiles(ArrayList jobQueue);

}