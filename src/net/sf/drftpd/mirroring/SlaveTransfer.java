/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.mirroring;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.slave.Transfer;

import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.net.InetSocketAddress;

import java.rmi.RemoteException;


/**
 * @author mog
 * @author zubov
 * @version $Id: SlaveTransfer.java,v 1.22 2004/08/03 20:14:01 zubov Exp $
 */
public class SlaveTransfer {
    private static final Logger logger = Logger.getLogger(SlaveTransfer.class);
    private RemoteSlave _destSlave;
    private LinkedRemoteFileInterface _file;
    private RemoteSlave _sourceSlave;
    private DstXfer dstxfer;
    private boolean finished = false;
    private SrcXfer srcxfer;
    private Throwable stackTrace;

    /**
     * Slave to Slave Transfers
     */
    public SlaveTransfer(LinkedRemoteFileInterface file,
        RemoteSlave sourceSlave, RemoteSlave destSlave) {
        _file = file;
        _sourceSlave = sourceSlave;
        _destSlave = destSlave;
    }

    int getXferSpeed() {
        if ((srcxfer == null) || (srcxfer.srcxfer == null) ||
                (dstxfer == null) || (dstxfer.dstxfer == null)) {
            return 0;
        }

        int srcspeed;

        try {
            srcspeed = srcxfer.srcxfer.getXferSpeed();
        } catch (RemoteException e) {
            _sourceSlave.handleRemoteException(e);
            srcspeed = 0;
        }

        int dstspeed;

        try {
            dstspeed = dstxfer.dstxfer.getXferSpeed();
        } catch (RemoteException e1) {
            _destSlave.handleRemoteException(e1);
            dstspeed = 0;
        }

        return (srcspeed + dstspeed) / 2;
    }

    long getTransfered() {
        if ((srcxfer == null) || (srcxfer.srcxfer == null) ||
                (dstxfer == null) || (dstxfer.dstxfer == null)) {
            return 0;
        }

        long srctransfered;

        try {
            srctransfered = srcxfer.srcxfer.getTransfered();
        } catch (RemoteException e) {
            _sourceSlave.handleRemoteException(e);
            srctransfered = 0;
        }

        long dsttransfered;

        try {
            dsttransfered = dstxfer.dstxfer.getTransfered();
        } catch (RemoteException e1) {
            _destSlave.handleRemoteException(e1);
            dsttransfered = 0;
        }

        return (srctransfered + dsttransfered) / 2;
    }

    private void interruptibleSleepUntilFinished() throws Throwable {
        while (!finished) {
            try {
                Thread.sleep(1000); // 1 sec

                //System.err.println("slept 1 secs");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        if (stackTrace != null) {
            throw stackTrace;
        }
    }

    private void abort() throws DestinationSlaveException, SourceSlaveException {
        logger.debug("running abort()");

        if ((dstxfer != null) && (dstxfer.dstxfer != null)) {
            try {
                logger.debug("running dstxfer abort()");
                dstxfer.dstxfer.abort();
                logger.debug("ran dstxfer abort()");
            } catch (RemoteException e) {
                throw new DestinationSlaveException(e);
            }
        }

        if ((srcxfer != null) && (srcxfer.srcxfer != null)) {
            try {
                logger.debug("running srcxfer abort()");
                srcxfer.srcxfer.abort();
                logger.debug("ran srcxfer abort()");
            } catch (RemoteException e1) {
                throw new SourceSlaveException(e1);
            }
        }
    }

    /**
     * Returns true if the crc passed, false otherwise
     *
     * @return @throws
     *         IOException
     */
    protected boolean transfer(boolean checkCRC) throws SlaveException {
        try {
            dstxfer = new DstXfer(_destSlave.getSlave().listen(false));
        } catch (SlaveUnavailableException e) {
            abort();
            throw new DestinationSlaveException(e);
        } catch (RemoteException e1) {
            _destSlave.handleRemoteException(e1);
            abort();
            throw new DestinationSlaveException(
                "Slave was unavailable to tell to listen for slave2slave transfer");
        } catch (IOException e) {
            abort();
            throw new DestinationSlaveException(_destSlave.getName() +
                " had an error listening for slave2slave transfer");
        }

        try {
            srcxfer = new SrcXfer(_sourceSlave.getSlave().connect(new InetSocketAddress(
                            _destSlave.getInetAddress(), dstxfer.getLocalPort()),
                        false));
        } catch (SlaveUnavailableException e) {
            abort();
            throw new SourceSlaveException(e);
        } catch (RemoteException e2) {
            _sourceSlave.handleRemoteException(e2);
            abort();
            throw new SourceSlaveException(
                "Slave could not connect for slave2slave transfer");
        }

        dstxfer.start();
        srcxfer.start();

        while ((srcxfer.isAlive() || dstxfer.isAlive()) &&
                ((dstxfer.e == null) && (srcxfer.e == null))) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
            }
        }

        if (srcxfer.e != null) {
            logger.info("Problem with " + _sourceSlave.getName(), srcxfer.e);
            abort();
            throw new SourceSlaveException(srcxfer.e);
        }

        if (dstxfer.e != null) {
            logger.info("Problem with " + _destSlave.getName(), dstxfer.e);
            abort();
            throw new DestinationSlaveException(dstxfer.e);
        }

        if (!checkCRC) {
            // crc passes if we're not using it
            _file.addSlave(_destSlave);

            return true;
        }

        long dstxferCheckSum;

        try {
            dstxferCheckSum = dstxfer.getChecksum();
        } catch (RemoteException e3) {
            _destSlave.handleRemoteException(e3);
            throw new DestinationSlaveException(
                "Caught RemoteException getting the checksum from transfer");
        }

        try {
            if ((dstxferCheckSum == 0) ||
                    (_file.getCheckSumCached() == dstxferCheckSum) ||
                    (_file.getCheckSumFromSlave() == dstxferCheckSum)) {
                _file.addSlave(_destSlave);

                return true;
            }
        } catch (NoAvailableSlaveException e) {
            logger.info("NoAvailableSlaveException caught getting checksum from slave",
                e);

            return false;
        } catch (IOException e) {
            logger.info("Exception caught getting checksum from slave", e);

            return false;
        }

        return false;
    }

    class DstXfer extends Thread {
        private Transfer dstxfer;
        private Throwable e;

        public DstXfer(Transfer dstxfer) {
            this.dstxfer = dstxfer;
        }

        public long getChecksum() throws RemoteException {
            return dstxfer.getChecksum();
        }

        /**
         * @return
         */
        public int getLocalPort() throws RemoteException {
            return dstxfer.getLocalPort();
        }

        public void run() {
            try {
                _file.receiveFile(dstxfer, 'I', 0L);
            } catch (Throwable e) {
                this.e = e;
            }
        }
    }

    class SrcXfer extends Thread {
        private Throwable e;
        private Transfer srcxfer;

        public SrcXfer(Transfer srcxfer) {
            this.srcxfer = srcxfer;
        }

        public long getChecksum() throws RemoteException {
            return srcxfer.getChecksum();
        }

        public void run() {
            try {
                _file.sendFile(srcxfer, 'I', 0L);
            } catch (Throwable e) {
                this.e = e;
            }
        }
    }
}
