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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.Archive;
import org.drftpd.sections.SectionInterface;


/**
 * Moves a "release" so that it resides only on the .numOfSlaves slaves with most files.
 * 
 * @author zubov
 * @version $Id$
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
        HashMap<RemoteSlave, SlaveCount> slaveMap) {
        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = null;
            file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                findDestinationSlavesRecursive(file, slaveMap);
                continue;
            }

            for (Iterator iter2 = file.getSlaves().iterator(); iter2.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iter2.next();

                if(rslave.isMemberOf("incoming")) continue;
                if(lrf.getSlaves().contains(rslave)) continue;
                SlaveCount i = (SlaveCount) slaveMap.get(rslave);
                if (i == null) {
                	slaveMap.put(rslave, new SlaveCount());
                } else {
                	i.addOne();
        		}
            }
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        HashMap<RemoteSlave, SlaveCount> slaveMap = new HashMap<RemoteSlave, SlaveCount>();
        findDestinationSlavesRecursive(getDirectory(), slaveMap);

        HashSet<RemoteSlave> returnMe = new HashSet<RemoteSlave>();

        RemoteSlave minslave = null;
        // the value of the lowest count in the ret HashSet
        int mincount = 0;
    	Map.Entry<RemoteSlave,SlaveCount> entry;
    	Iterator<Map.Entry<RemoteSlave,SlaveCount>> iter = slaveMap.entrySet().iterator();

        for(int i=0; i < _numOfSlaves; i++) {
        	entry = iter.next();
    		returnMe.add(entry.getKey());
        	// overwrite mincount if count of added is lower.
        	if(minslave == null || mincount > entry.getValue().getValue()) {
        		minslave = entry.getKey();
        		mincount = entry.getValue().getValue();
        	}
        }

        while(iter.hasNext()) {
        	entry = iter.next();
        	// has higher value, replace
        	if(mincount < entry.getValue().getValue()) {
        		if(!returnMe.remove(minslave)) throw new RuntimeException();
        		minslave = entry.getKey();
        		mincount = Integer.MAX_VALUE;

        		//calculated new minCount
        		//could have the returnMe sorted so that lowest value is always at bottom.
        		for (RemoteSlave rslave : returnMe) {
        			int count = slaveMap.get(rslave).getValue();
        			//if count is less than mincount, we have a new minslave
					if(count < mincount) {
						mincount = count;
						minslave = rslave;
					}
				}
        		returnMe.add(entry.getKey());
        	}
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
        return "FinishReleaseOnSlaves[directory=" + getDirectory().getPath() +
        ",dest=" + outputSlaves(getRSlaves()) + "]";
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
