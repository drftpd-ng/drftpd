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
import java.util.Collections;
import java.util.Set;

import net.sf.drftpd.mirroring.Job;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.Archive;
import org.drftpd.sections.SectionInterface;


/**
 * @author zubov
 * @version $Id$
 */
public class ArchiveHandler extends Thread {
    protected final static Logger logger = Logger.getLogger(ArchiveHandler.class);
    private ArchiveType _archiveType;

    public ArchiveHandler(ArchiveType archiveType) {
        super(archiveType.getClass().getName() + " archiving " +
                archiveType.getSection().getName());
        _archiveType = archiveType;
        
        for(ArchiveHandler h : _archiveType._parent.getArchiveHandlers()) {
        	if(archiveType.getSection().equals(h.getSection()))
        		throw new RuntimeException(
        				"Attempt to add start an already running archivehandler");
        }
    }

    public ArchiveType getArchiveType() {
        return _archiveType;
    }

    public SectionInterface getSection() {
        return _archiveType.getSection();
    }

    public void run() {
        try {
            synchronized (_archiveType._parent) {
                if (_archiveType.getDirectory() == null) {
                    _archiveType.setDirectory(_archiveType.getOldestNonArchivedDir());
                }

                if (_archiveType.getDirectory() == null) {
                    return; // all done
                }

                _archiveType._parent.addArchiveHandler(this);
            }

            if (_archiveType.getRSlaves() == null) {
                Set<RemoteSlave> destSlaves = _archiveType.findDestinationSlaves();

                if (destSlaves == null) {
                    _archiveType.setDirectory(null);
                    return; // no available slaves to use
                }

                _archiveType.setRSlaves(Collections.unmodifiableSet(destSlaves));
            }

            ArrayList<Job> jobs = _archiveType.send();
            _archiveType.waitForSendOfFiles(new ArrayList<Job>(jobs));
            _archiveType.cleanup(jobs);
            logger.info("Done archiving " +
                getArchiveType().getDirectory().getPath());
        } catch (Exception e) {
            logger.warn("", e);
        }

        Archive archive = _archiveType._parent;

        if (!archive.removeArchiveHandler(this)) {
            logger.debug(
                "This is a serious bug, unable to remove the ArchiveHandler!");
        }
    }
}
