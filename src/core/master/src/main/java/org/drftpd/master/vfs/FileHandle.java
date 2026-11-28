/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.vfs;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * @author zubov
 * @version $Id$
 */
public class FileHandle extends InodeHandle implements FileHandleInterface {
    protected static final Logger logger = LogManager.getLogger(FileHandle.class.getName());

    public FileHandle(String path) {
        super(path);
    }

    @Override
    /**
     * @see org.drftpd.master.vfs.InodleHandle#getInode()
     */
    protected VirtualFileSystemFile getInode() throws FileNotFoundException {
        VirtualFileSystemInode inode = super.getInode();
        if (inode instanceof VirtualFileSystemFile) {
            return (VirtualFileSystemFile) inode;
        }
        throw new ClassCastException("FileHandle object pointing to Inode:"
                + inode);
    }

    /**
     * @return a Set containing the names of the slaves that contain the File.
     * @throws FileNotFoundException
     */
    public Set<String> getSlaveNames() throws FileNotFoundException {
        return new HashSet<>(getInode().getSlaves());
    }

    /**
     * @return a Set containing the slaves that contain the File.
     * @throws FileNotFoundException
     */
    public Set<RemoteSlave> getSlaves() throws FileNotFoundException {
        HashSet<RemoteSlave> slaves = new HashSet<>();
        for (String slave : getInode().getSlaves()) {
            try {
                slaves.add(getGlobalContext().getSlaveManager().getRemoteSlave(
                        slave));
            } catch (ObjectNotFoundException e) {
                getInode().removeSlave(slave);
            }
        }
        return slaves;
    }

    /**
     * @return a filtered Collection containing only the avaiable slaves of
     * the Inode.
     * @throws NoAvailableSlaveException
     * @throws FileNotFoundException
     */
    public Collection<RemoteSlave> getAvailableSlaves()
            throws NoAvailableSlaveException, FileNotFoundException {
        HashSet<RemoteSlave> rslaves = new HashSet<>();
        for (RemoteSlave rslave : getSlaves()) {
            if (rslave.isAvailable()) {
                rslaves.add(rslave);
            }
        }
        if (rslaves.isEmpty()) {
            throw new NoAvailableSlaveException();
        }
        return rslaves;
    }

    /**
     * Removes a slave from the slavelist.
     */
    public void removeSlave(RemoteSlave sourceSlave)
            throws FileNotFoundException {
        getInode().removeSlave(sourceSlave.getName());
    }

    /**
     * @return the first slave of the slave list.
     * @throws FileNotFoundException     if there's no such file.
     * @throws NoAvailableSlaveException if there's no avaiable slave.
     */
    public RemoteSlave getASlaveForFunction() throws FileNotFoundException,
            NoAvailableSlaveException {
        for (RemoteSlave rslave : getAvailableSlaves()) {
            return rslave;
        }
        throw new NoAvailableSlaveException("No slaves are online for file "
                + this);
    }

    /**
     * Like {@link #getASlaveForFunction()}, but skips the given slaves.
     *
     * @param exclude slaves that should not be returned
     * @return an available slave for the file that is not in <tt>exclude</tt>
     * @throws FileNotFoundException     if there's no such file, or every available slave was
     *                                   excluded (they all reported the file as missing)
     * @throws NoAvailableSlaveException if there's no available slave.
     */
    protected RemoteSlave getASlaveForFunction(Set<RemoteSlave> exclude) throws FileNotFoundException,
            NoAvailableSlaveException {
        for (RemoteSlave rslave : getAvailableSlaves()) {
            if (!exclude.contains(rslave)) {
                return rslave;
            }
        }
        if (!exclude.isEmpty()) {
            throw new FileNotFoundException(getPath() + " was not found on any available slave");
        }
        throw new NoAvailableSlaveException("No slaves are online for file "
                + this);
    }

    /**
     * @return the CRC32 of the file.
     * @throws FileNotFoundException     if there's no such file.
     * @throws NoAvailableSlaveException if there's no available slave.
     */
    public long getCheckSum() throws NoAvailableSlaveException,
            FileNotFoundException {
        long checksum = getInode().getChecksum();
        if (checksum == 0L) {
            return getCheckSumFromSlave();
        }
        return checksum;
    }

    /**
     * Changes the CRC32 of the File.
     *
     * @param checksum
     * @throws FileNotFoundException
     */
    public void setCheckSum(long checksum) throws FileNotFoundException {
        VirtualFileSystemFile file = getInode();
        file.setChecksum(checksum);
    }

    /**
     * @return the cached CRC32 of the file.
     * @throws FileNotFoundException if there's no such file.
     */
    public long getCheckSumCached() throws FileNotFoundException {
        return getInode().getChecksum();
    }

    /**
     * @return the CRC32 of the file ignoring the cached value.
     * @throws FileNotFoundException     if there's no such file.
     * @throws NoAvailableSlaveException if there's no available slave.
     */
    public long getCheckSumFromSlave() throws NoAvailableSlaveException,
            FileNotFoundException {
        long checksum = 0L;
        if (getSize() != 0L) {
            // Slaves that reported the file as missing, so we don't ask them again
            Set<RemoteSlave> missingOn = new HashSet<>();
            while (true) {
                synchronized (getInode()) {
                    RemoteSlave rslave = getASlaveForFunction(missingOn);
                    try {
                        checksum = rslave.getCheckSumForPath(getPath());
                    } catch (FileNotFoundException e) {
                        // The slave does not have the file at this path. This is usually a race with a
                        // delete, nuke, move or wipe of the file, not a fault of the slave, so we must
                        // not set the slave offline for it (see #328). Try the next slave instead; if the
                        // file itself is gone from the VFS, getInode() throws FileNotFoundException.
                        logger.warn("Slave {} reported {} as missing while getting its checksum: {}",
                                rslave.getName(), getPath(), e.getMessage());
                        logger.debug("Slave {} reported {} as missing", rslave.getName(), getPath(), e);
                        missingOn.add(rslave);
                        continue;
                    } catch (IOException e) {
                        rslave.setOffline(e);
                        continue;
                    } catch (SlaveUnavailableException e) {
                        continue;
                    }
                    setCheckSum(checksum);
                    return checksum;
                }
            }
        }
        return checksum;
    }

    /**
     * Add a slave to the slave list.
     *
     * @param destinationSlave
     * @throws FileNotFoundException if there's no such file.
     */
    public void addSlave(RemoteSlave destinationSlave)
            throws FileNotFoundException {
        getInode().addSlave(destinationSlave.getName());
    }

    /**
     * @return the xfertime.
     * @throws FileNotFoundException if there's no such file.
     */
    public long getXfertime() throws FileNotFoundException {
        return getInode().getXfertime();
    }

    /**
     * Sets the xfertime of this file
     *
     * @throws FileNotFoundException if there's no such file.
     */
    public void setXfertime(long x) throws FileNotFoundException {
        getInode().setXfertime(x);
    }

    public boolean isUploading() throws FileNotFoundException {
        return getInode().isUploading();
    }

    public boolean isDownloading() throws FileNotFoundException {
        return getInode().isDownloading();
    }

    public boolean isTransferring() throws FileNotFoundException {
        return getInode().isTransferring();
    }

    public void abortTransfers(String reason) throws FileNotFoundException {
        getInode().abortTransfers(reason);
    }

    public void abortUploads(String reason) throws FileNotFoundException {
        getInode().abortUploads(reason);
    }

    public void abortDownloads(String reason) throws FileNotFoundException {
        getInode().abortDownloads(reason);
    }

    /**
     * @return true if there's an avaiable slave for the file or
     * false if there isn't.
     */
    public boolean isAvailable() throws FileNotFoundException {
        try {
            return !getAvailableSlaves().isEmpty();
        } catch (NoAvailableSlaveException e) {
            return false;
        }
    }

    /**
     * Returns the size of the file in bytes
     */
    public long getSize() throws FileNotFoundException {
        return getInode().getSize();
    }

    /**
     * Changes the size of the file.
     *
     * @param size
     * @throws FileNotFoundException
     */
    public void setSize(long size) throws FileNotFoundException {
        getInode().setSize(size);
        getInode().commit();
    }

    @Override
    public boolean isDirectory() {
        return false;
    }

    @Override
    public boolean isFile() {
        return true;
    }

    @Override
    public boolean isLink() {
        return false;
    }

    @Override
    public void deleteUnchecked() throws FileNotFoundException {
        synchronized (getInode()) {
            abortTransfers("File " + getPath() + " is being deleted");
            for (RemoteSlave rslave : getSlaves()) {
                rslave.simpleDelete(getPath());
            }
            super.deleteUnchecked();
        }
    }
}
