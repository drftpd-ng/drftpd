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

import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;

import org.drftpd.PropertyHelper;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.Archive;

import org.drftpd.sections.SectionInterface;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id$
 */
public class MoveReleaseToMostFreeSlaves extends ArchiveType {
    private static final Logger logger = Logger.getLogger(MoveReleaseToMostFreeSlaves.class);
    private int _numOfSlaves;

    public MoveReleaseToMostFreeSlaves(Archive archive,
        SectionInterface section, Properties props) {
        super(archive, section, props);
        _numOfSlaves = Integer.parseInt(PropertyHelper.getProperty(props,
                    getSection().getName() + ".numOfSlaves"));

        if (_numOfSlaves < 1) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 0 for section " + section.getName());
        }
    }

    public void cleanup(ArrayList jobList) {
        for (Iterator iter = jobList.iterator(); iter.hasNext();) {
            Job job = (Job) iter.next();
            job.getFile().deleteOthers(getRSlaves());
        }
    }

    public HashSet findDestinationSlaves() {
        HashSet set = _parent.getConnectionManager().getGlobalContext()
                             .getSlaveManager().findSlavesBySpace(_numOfSlaves,
                new HashSet(), false);

        if (set.isEmpty()) {
            return null;
        }

        return set;
    }

    /**
     * Returns true if this directory is Archived by this ArchiveType's definition
     */
    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        return isArchivedToXSlaves(lrf, _numOfSlaves);
    }

    public String toString() {
        return "MoveReleaseToMostFreeSlaves=[directory=[" +
        getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) +
        "]numOfSlaves=[" + _numOfSlaves + "]]";
    }
}
