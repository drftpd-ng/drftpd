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
import org.apache.log4j.Logger;
import org.drftpd.sections.SectionInterface;

import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/*
 * @author zubov
 * @version $Id
 */
/*
 * @author zubov
 * @version $Id
 */
/**
 * @author zubov
 * @version $Id: ArchiveType.java,v 1.1 2004/04/18 05:57:35 zubov Exp $
 */
public abstract class ArchiveType {
	protected static Logger logger = Logger.getLogger(ArchiveType.class);
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
	 * If no such directory exists, it returns null, should use Archive.isExempt(String)
	 */
	public abstract LinkedRemoteFileInterface getOldestNonArchivedDir();

	public final ArrayList getRSlaves() {
		return _slaveList;
	}

	public final SectionInterface getSection() {
		if (_section == null)
			throw new IllegalStateException("setSection() needs to be called before getSection()");
		return _section;
	}

	public final void init(Archive archive, SectionInterface section) {
		_parent = archive;
		setSection(section);
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