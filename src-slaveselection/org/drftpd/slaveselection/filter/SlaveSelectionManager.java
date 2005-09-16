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
package org.drftpd.slaveselection.filter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.mirroring.Job;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.SlaveManager;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.User;


/**
 * @author mog
 * @version $Id: SlaveSelectionManager.java 930 2005-01-30 15:16:42Z zubov $
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {
    private GlobalContext _gctx;
    private FilterChain _ssmiDown;
    private FilterChain _ssmiJobDown;
    private FilterChain _ssmiJobUp;
    private FilterChain _ssmiUp;

    public SlaveSelectionManager(GlobalContext gctx)
        throws FileNotFoundException, IOException {
        _gctx = gctx;
        reload();
    }

    /**
     * Checksums call us with null BaseFtpConnection.
     */
    public RemoteSlave getASlave(Collection<RemoteSlave> rslaves, char direction,
        BaseFtpConnection conn, LinkedRemoteFileInterface file)
        throws NoAvailableSlaveException {
        InetAddress source = ((conn != null) ? conn.getClientAddress() : null);
        String status;

        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            status = "up";
        } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            status = "down";
        } else {
            throw new IllegalArgumentException();
        }

        return process(status, new ScoreChart(rslaves),
            (conn != null) ? conn.getUserNull() : null, source, direction, file, null);
    }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>(job.getFile().getAvailableSlaves());
        slaves.removeAll(job.getDestinationSlaves());

        if (slaves.isEmpty()) {
            throw new NoAvailableSlaveException();
        }

        return process("jobdown", new ScoreChart(slaves), null, null,
            Transfer.TRANSFER_SENDING_DOWNLOAD, job.getFile(), null);
    }

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>(job.getDestinationSlaves());

        for (Iterator iter = slaves.iterator(); iter.hasNext();) {
            if (!((RemoteSlave) iter.next()).isAvailable()) {
                iter.remove();
            }
        }

        if (slaves.isEmpty()) {
            throw new NoAvailableSlaveException();
        }

        return process("jobup", new ScoreChart(slaves), null, null,
            Transfer.TRANSFER_SENDING_DOWNLOAD, job.getFile(), sourceSlave);
    }

    public SlaveManager getSlaveManager() {
        return getGlobalContext().getSlaveManager();
    }

    private RemoteSlave process(String filterchain, ScoreChart sc, User user,
        InetAddress peer, char direction, LinkedRemoteFileInterface file, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        FilterChain ssmi;

        if (filterchain.equals("down")) {
            ssmi = _ssmiDown;
        } else if (filterchain.equals("up")) {
            ssmi = _ssmiUp;
        } else if (filterchain.equals("jobup")) {
            ssmi = _ssmiJobUp;
        } else if (filterchain.equals("jobdown")) {
            ssmi = _ssmiJobDown;
        } else {
            throw new IllegalArgumentException();
        }

        return ssmi.getBestSlave(sc, user, peer, direction, file, sourceSlave);
    }

    public void reload() throws FileNotFoundException, IOException {
        _ssmiDown = new FilterChain(this, "conf/slaveselection-down.conf");
        _ssmiUp = new FilterChain(this, "conf/slaveselection-up.conf");

        if (getGlobalContext().getConnectionManager().getGlobalContext()
                    .isJobManagerLoaded()) {
            _ssmiJobUp = new FilterChain(this, "conf/slaveselection-jobup.conf");
            _ssmiJobDown = new FilterChain(this,
                    "conf/slaveselection-jobdown.conf");
        } else {
            _ssmiJobUp = null;
            _ssmiJobDown = null;
        }
    }

    public GlobalContext getGlobalContext() {
        return _gctx;
    }
}
