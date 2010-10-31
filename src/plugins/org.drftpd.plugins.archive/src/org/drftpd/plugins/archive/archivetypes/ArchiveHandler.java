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
package org.drftpd.plugins.archive.archivetypes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.DuplicateArchiveException;
import org.drftpd.plugins.archive.event.ArchiveFailedEvent;
import org.drftpd.plugins.archive.event.ArchiveFinishEvent;
import org.drftpd.plugins.archive.event.ArchiveStartEvent;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.sections.SectionInterface;

/**
 * @author CyBeR
 * @version $Id$
 */
public class ArchiveHandler extends Thread {
	protected final static Logger logger = Logger.getLogger(ArchiveHandler.class);

	private ArchiveType _archiveType;
	
	private ArrayList<Job> _jobs = null;

	public ArchiveHandler(ArchiveType archiveType) {
		super(archiveType.getClass().getName() + " archiving " + archiveType.getSection().getName());
		_archiveType = archiveType;
		AnnotationProcessor.process(this);
	}

	public ArchiveType getArchiveType() {
		return _archiveType;
	}

	public SectionInterface getSection() {
		return _archiveType.getSection();
	}
	
	public ArrayList<Job> getJobs() {
		if (_jobs == null) {
			return (ArrayList<Job>)Collections.<Job>emptyList();
		}
		return new ArrayList<Job>(_jobs);
	}

	/*
	 * Thread for ArchiveHandler
	 * This will go through and find the oldest non archived dir, then try and archive it
	 * It will also loop X amount of times defined in .repeat from .conf file.
	 * 
	 * This also throws events so they can be caught for sitebot announcing.
	 */
	public void run() {
		for (int i=0; i<_archiveType.getRepeat(); i++) {
			try {
				synchronized (_archiveType._parent) {
					if (_archiveType.getDirectory() == null) {
						_archiveType.setDirectory(_archiveType.getOldestNonArchivedDir());
					}
	
					if (_archiveType.getDirectory() == null) {
						return; // all done
					}
					try {
						_archiveType._parent.addArchiveHandler(this);
					} catch (DuplicateArchiveException e) {
						logger.warn("Directory -- " + _archiveType.getDirectory() + " -- is already being archived ");
						return;
					}
				}
				if (!_archiveType.moveReleaseOnly()) {
					if (_archiveType.getRSlaves() == null) {
						Set<RemoteSlave> destSlaves = _archiveType.findDestinationSlaves();
		
						if (destSlaves == null) {
							_archiveType.setDirectory(null);
							return; // no available slaves to use
						}
						_archiveType.setRSlaves(Collections.unmodifiableSet(destSlaves));
					}
					
					_jobs = _archiveType.send();
				}
	
				GlobalContext.getEventService().publishAsync(new ArchiveStartEvent(_archiveType,_jobs));
				long starttime = System.currentTimeMillis();
				if (_jobs != null) {
					_archiveType.waitForSendOfFiles(_jobs);
				}
				
				if (!_archiveType.moveRelease(getArchiveType().getDirectory())) {
					GlobalContext.getEventService().publishAsync(new ArchiveFailedEvent(_archiveType,starttime,"Failed To Move Directory"));	
				} else {
					logger.info("Done archiving " + getArchiveType().getDirectory().getPath());
					GlobalContext.getEventService().publishAsync(new ArchiveFinishEvent(_archiveType,starttime));
				}
			} catch (Exception e) {
				logger.warn("", e);
			} finally {
				_archiveType._parent.removeArchiveHandler(this);
				_archiveType.setDirectory(null);				
			}
		}
	}
}
