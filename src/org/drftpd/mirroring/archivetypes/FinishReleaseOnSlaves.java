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

import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;

import org.drftpd.PropertyHelper;
import org.drftpd.mirroring.ArchiveType;

import org.drftpd.sections.SectionInterface;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id: FinishReleaseOnSlaves.java,v 1.6 2004/11/09 18:59:55 mog Exp $
 */
public class FinishReleaseOnSlaves extends ArchiveType {
    private static final Logger logger = Logger.getLogger(FinishReleaseOnSlaves.class);
    private int _numOfSlaves = 1;

    public FinishReleaseOnSlaves(Archive archive, SectionInterface section,
        Properties props) {
        super(archive, section, props);
        _numOfSlaves = Integer.parseInt(PropertyHelper.getProperty(props,
                    getSection().getName() + ".numOfSlaves"));

        if (_numOfSlaves < 1) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 0 for section " + section.getName());
        }
    }

    public void findDestinationSlavesRecursive(LinkedRemoteFileInterface lrf,
        HashMap slaveMap) {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = null;
            file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                findDestinationSlavesRecursive(file, slaveMap);

                continue;
            }

            Collection tempSlaveList = file.getSlaves();

            for (Iterator iter2 = tempSlaveList.iterator(); iter2.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iter2.next();

                if (rslave.isAvailable()) {
                    SlaveCount i = (SlaveCount) slaveMap.get(rslave);

                    if (i == null) {
                        slaveMap.put(new SlaveCount(), rslave);
                    } else {
                        i.addOne();
                    }
                }
            }
        }
    }

    public HashSet findDestinationSlaves() {
        HashMap slaveMap = new HashMap();
        findDestinationSlavesRecursive(getDirectory(), slaveMap);

        ArrayList sorted = new ArrayList(slaveMap.keySet());
        Collections.sort(sorted);

        HashSet returnMe = new HashSet();

        for (ListIterator iter = sorted.listIterator(); iter.hasNext();) {
            if (iter.nextIndex() == _numOfSlaves) {
                break;
            }

            RemoteSlave rslave = (RemoteSlave) slaveMap.get(iter.next());
            returnMe.add(rslave);
        }

        return returnMe;
    }

    public void cleanup(ArrayList jobList) {
        for (Iterator iter = jobList.iterator(); iter.hasNext();) {
            Job job = (Job) iter.next();
            job.getFile().deleteOthers(getRSlaves());
        }
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        return isArchivedToXSlaves(lrf, _numOfSlaves);
    }

    public String toString() {
        return "FinishReleaseOnSlaves=[directory=[" + getDirectory().getPath() +
        "]dest=[" + outputSlaves(getRSlaves()) + "]]";
    }

    public class SlaveCount implements Comparable {
        private int _value = 1;

        public SlaveCount() {
        }

        public int compareTo(Object o) {
            SlaveCount count = (SlaveCount) o;

            return getValue() - count.getValue();
        }

        public void addOne() {
            _value++;
        }

        public int getValue() {
            return _value;
        }
    }
}
