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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.mirroring.Job;

import org.apache.log4j.Logger;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.Archive;
import org.drftpd.sections.SectionInterface;


/*
 * @author iamn
 * @author zubov
 * @version $Id$
 */
public class ConstantMirroringAndArchive extends ArchiveType {
    private static final Logger logger = Logger.getLogger(ConstantMirroringAndArchive.class);
    private long _slowAfter;
    private ArrayList<RemoteSlave> _fastHosts;

    public ConstantMirroringAndArchive(Archive archive,
        SectionInterface section, Properties p) {
        super(archive, section, p);

        if (_numOfSlaves < 2) {
            throw new IllegalArgumentException(
                "numOfSlaves has to be > 1 for section " + section.getName());
        }

        try {
            _slowAfter = 60000 * Long.parseLong(PropertyHelper.getProperty(p,
                        getSection().getName() + ".slowAfter"));
        } catch (NullPointerException e) {
            _slowAfter = 0;
            logger.error("Unable to get slowafter!!");
        }

        _fastHosts = new ArrayList<RemoteSlave>();

        for (int i = 1;; i++) {
            String slavename = null;

            try {
                slavename = PropertyHelper.getProperty(p,
                        getSection().getName() + ".fastHost." + i);
            } catch (NullPointerException e) {
                break; // done
            }

            try {
                _fastHosts.add(_parent.getGlobalContext().getSlaveManager()
						.getRemoteSlave(slavename));
            } catch (ObjectNotFoundException e) {
                logger.error("Unable to get slave " + slavename +
                    " from the SlaveManager");
            }
        }

        if (_fastHosts.isEmpty()) {
            throw new NullPointerException(
                "Cannot continue, 0 slaves found for fastHosts ConstantMirroringAndArchiveAndArchive for for section " +
                getSection().getName());
        }
    }

    public HashSet<RemoteSlave> findDestinationSlaves() {
        HashSet<RemoteSlave> allHosts = new HashSet<RemoteSlave>(_parent
				.getGlobalContext().getSlaveManager().getSlaves());

        HashSet<RemoteSlave> returnMe = new HashSet<RemoteSlave>();

        /*
		 * if ((System.currentTimeMillis() - getDirectory().lastModified()) >
		 * _slowAfter) { logger.debug("Returning list of slowhosts"); } else {
		 * logger.debug("Returning list of fasthosts"); }
		 */
        for (Iterator iter2 = allHosts.iterator(); iter2.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter2.next();

            if (rslave.isAvailable()) {
                if ((System.currentTimeMillis() -
                        getDirectory().lastModified()) > _slowAfter) {
                    if (!_fastHosts.contains(rslave)) {
                        returnMe.add(rslave);
                    }
                } else {
                    if (_fastHosts.contains(rslave)) {
                        returnMe.add(rslave);
                    }
                }
            }
        }

        return returnMe;

        /*return new HashSet(_parent.getConnectionManager().getGlobalContext()
                                  .getSlaveManager().getSlaves());*/
    }

    protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
        throws IncompleteDirectoryException, OfflineSlaveException {
        boolean shouldBeFast;

        if ((System.currentTimeMillis() - lrf.lastModified()) > _slowAfter) {
            shouldBeFast = false;

            //logger.debug("DBG File " + lrf.getPath() + " should be on slowhost");
        } else {
            shouldBeFast = true;

            //logger.debug("DBG File " + lrf.getPath() + " should be on fasthost");
        }

        for (Iterator iter = lrf.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface src = (LinkedRemoteFileInterface) iter.next();

            if (src.isFile()) {
                /* Custom stuff: */
                for (Iterator iter2 = _fastHosts.iterator(); iter2.hasNext();) {
                    RemoteSlave fasthost = (RemoteSlave) iter2.next();

                    if (fasthost == null) {
                        continue;
                    }

                    if (!src.getSlaves().contains(fasthost) && shouldBeFast) {
                        //logger.debug("DBG File " + src.getName() + " is on slowhost, moving to fasthost");
                        return false;
                    }

                    if (src.getSlaves().contains(fasthost) && !shouldBeFast) {
                        //logger.debug("DBG File " + src.getName() + " is on fasthost, moving to slowhost");
                        return false;
                    }
                }

                /*if (!shouldBeFast) {
                            if (!src.getSlaves().contains(_fastHosts)) {
                                    //logger.debug("DBG File is on fasthost, moving to slowhost");
                                    return false;
                            }
                    }
                    else {
                            if (src.getSlaves().contains(_fastHosts)) {
                                    //logger.debug("DBG File is on slowhost, moving to fasthost");
                                    return false;
                            }
                    }*/
                try {
                    if (src.getAvailableSlaves().size() != _numOfSlaves) {
                        logger.debug(src.getPath() +
                            " is on too few hosts, mirroring.");

                        return false;
                    }
                } catch (NoAvailableSlaveException e) {
                    throw new OfflineSlaveException(src.getName() +
                        " is not online");
                }

                if (src.getSlaves().size() > _numOfSlaves) {
                    logger.debug(src.getPath() +
                        " is on too many hosts, cleaning up.");

                    return false;
                }
            } else { // src.isDirectory()
            	if (!isArchivedDir(src)) {
                	return false;
                }
            }
        }

        return true;
    }

    /**
     * Adds relevant Jobs to the JobManager and returns an ArrayList of those Job's
     */
    public ArrayList<Job> send() {
        return recursiveSend(getDirectory());
    }

    public String toString() {
        return "ConstantMirroringAndArchive=[directory=[" +
        getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) +
        "]numOfSlaves=[" + _numOfSlaves + "]slowAfter=[" + _slowAfter + "]]";
    }
}
