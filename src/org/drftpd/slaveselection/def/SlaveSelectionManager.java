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
package org.drftpd.slaveselection.def;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java,v 1.11 2004/08/03 20:14:09 zubov Exp $
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {
    private long _minfreespace;
    private int _maxTransfers;
    private long _maxBandwidth;
    private SlaveManagerImpl _sm;

    public SlaveSelectionManager(SlaveManagerImpl sm)
        throws FileNotFoundException, IOException {
        super();
        _sm = sm;
    }

    public void reload() throws FileNotFoundException, IOException {
        Properties p = new Properties();
        p.load(new FileInputStream("conf/slaveselection-old.conf"));
        _minfreespace = Bytes.parseBytes(FtpConfig.getProperty(p, "minfreespace"));

        try {
            if (_sm.getGlobalContext().getConnectionManager().getJobManager() != null) {
                _maxTransfers = Integer.parseInt(FtpConfig.getProperty(p,
                            "maxTransfers"));
                _maxBandwidth = Bytes.parseBytes(FtpConfig.getProperty(p,
                            "maxBandwidth"));
            }
        } catch (IllegalStateException e) {
            // jobmanager isn't loaded, don't load it's settings
        }
    }

    public RemoteSlave getASlave(Collection rslaves, char direction,
        BaseFtpConnection conn, LinkedRemoteFileInterface file)
        throws NoAvailableSlaveException {
        return getASlaveInternal(rslaves, direction);
    }

    public RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file,
        FtpConfig cfg) throws NoAvailableSlaveException {
        return getASlaveInternal(file.getAvailableSlaves(),
            Transfer.TRANSFER_SENDING_DOWNLOAD);
    }

    public SlaveManagerImpl getSlaveManager() {
        if (_sm == null) {
            throw new NullPointerException();
        }

        return _sm;
    }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        return getASlaveInternal(job.getFile().getAvailableSlaves(),
            Transfer.TRANSFER_SENDING_DOWNLOAD);
    }

    private RemoteSlave getASlaveInternal(Collection slaves, char direction)
        throws NoAvailableSlaveException {
        RemoteSlave bestslave;
        SlaveStatus beststatus;

        {
            Iterator i = slaves.iterator();
            int bestthroughput;

            while (true) {
                if (!i.hasNext()) {
                    throw new NoAvailableSlaveException();
                }

                bestslave = (RemoteSlave) i.next();

                try {
                    beststatus = bestslave.getStatusAvailable();

                    // throws SlaveUnavailableException
                } catch (SlaveUnavailableException ex) {
                    continue;
                }

                bestthroughput = beststatus.getThroughputDirection(direction);

                break;
            }

            while (i.hasNext()) {
                RemoteSlave slave = (RemoteSlave) i.next();
                SlaveStatus status;

                try {
                    status = slave.getStatusAvailable();
                } catch (SlaveUnavailableException ex) {
                    continue;
                }

                int throughput = status.getThroughputDirection(direction);

                if ((beststatus.getDiskSpaceAvailable() < _minfreespace) &&
                        (beststatus.getDiskSpaceAvailable() < status.getDiskSpaceAvailable())) {
                    // best slave has less space than "freespace.min" &&
                    // best slave has less space available than current slave 
                    bestslave = slave;
                    bestthroughput = throughput;
                    beststatus = status;

                    continue;
                }

                if (status.getDiskSpaceAvailable() < _minfreespace) {
                    // current slave has less space available than "freespace.min"
                    // above check made sure bestslave has more space than us
                    continue;
                }

                if (throughput == bestthroughput) {
                    if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
                        if (bestslave.getLastUploadReceiving() > slave.getLastUploadReceiving()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
                        if (bestslave.getLastDownloadSending() > slave.getLastDownloadSending()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    } else if (direction == Transfer.TRANSFER_THROUGHPUT) {
                        if (bestslave.getLastTransfer() > slave.getLastTransfer()) {
                            bestslave = slave;
                            bestthroughput = throughput;
                            beststatus = status;
                        }
                    }
                }

                if (throughput < bestthroughput) {
                    bestslave = slave;
                    bestthroughput = throughput;
                    beststatus = status;
                }
            }
        }

        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            bestslave.setLastUploadReceiving(System.currentTimeMillis());
        } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            bestslave.setLastDownloadSending(System.currentTimeMillis());
        } else {
            bestslave.setLastUploadReceiving(System.currentTimeMillis());
            bestslave.setLastDownloadSending(System.currentTimeMillis());
        }

        return bestslave;
    }

    public RemoteSlave getASlaveForJobUpload(Job job)
        throws NoAvailableSlaveException {
        Collection slaves = _sm.getAvailableSlaves();
        slaves.removeAll(job.getFile().getAvailableSlaves());

        return getASlaveForJob(slaves, Transfer.TRANSFER_RECEIVING_UPLOAD);
    }

    public RemoteSlave getASlaveForJob(Collection slaves, char direction)
        throws NoAvailableSlaveException {
        RemoteSlave rslave = this.getASlaveInternal(slaves, direction);
        SlaveStatus status = null;

        try {
            status = rslave.getStatusAvailable();
        } catch (SlaveUnavailableException e) {
            throw new NoAvailableSlaveException();
        }

        if (status.getThroughputDirection(direction) > _maxBandwidth) {
            throw new NoAvailableSlaveException();
        }

        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            if (status.getTransfersReceiving() > _maxTransfers) {
                throw new NoAvailableSlaveException();
            }
        }

        if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            if (status.getTransfersSending() > _maxTransfers) {
                throw new NoAvailableSlaveException();
            }
        }

        return rslave;
    }
}
