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
package org.drftpd.master.slaveselection.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.slave.network.Transfer;
import org.reflections.Reflections;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Set;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveSelectionManager extends SlaveSelectionManagerInterface {

    protected static final Logger logger = LogManager.getLogger(SlaveSelectionManager.class);

    private FilterChain _downChain;
    private FilterChain _upChain;
    private FilterChain _jobDownChain;
    private FilterChain _jobUpChain;

    private CaseInsensitiveHashMap<String, Class<? extends Filter>> _filtersMap;

    public SlaveSelectionManager() throws IOException {
        initFilters();
        reload();
    }

    @EventSubscriber
    public void onReloadEvent() {
        logger.info("Received reload event, reloading");
        initFilters();
        try {
            reload();
        } catch (FileNotFoundException e) {
            logger.error("Error Re-loading SlaveSelection Configuration Files (Files not found)");
        } catch (IOException e) {
            logger.error("Error Re-loading SlaveSelection Configuration Files");
        }
    }

    private void initFilters() {
        CaseInsensitiveHashMap<String, Class<? extends Filter>> filtersMap = new CaseInsensitiveHashMap<>();
        // TODO [DONE] @k2r Add filters
        Set<Class<? extends Filter>> filters = new Reflections("org.drftpd").getSubTypesOf(Filter.class);
        for (Class<? extends Filter> filter : filters) {
            String simpleName = filter.getSimpleName().replace("Filter", "");
            logger.debug("Registering {} filter", simpleName);
            filtersMap.put(simpleName, filter);
        }
        logger.debug("Registered {} filters", filtersMap.size());
        _filtersMap = filtersMap;
    }

    public CaseInsensitiveHashMap<String, Class<? extends Filter>> getFiltersMap() {
        // we do not want to pass this object around allowing it to be modified, make a copy of it.
        return new CaseInsensitiveHashMap<>(_filtersMap);
    }

    /**
     * Checksums call us with null BaseFtpConnection.
     */
    public RemoteSlave getASlave(BaseFtpConnection conn, char direction, InodeHandle file)
            throws NoAvailableSlaveException {
        String status;
        Collection<RemoteSlave> availableSlaves;
        if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
            status = "up";
            availableSlaves = getAvailableSlaves();
        } else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
            status = "down";
            try {
                availableSlaves = ((FileHandle) file).getAvailableSlaves();
            } catch (FileNotFoundException e) {
                throw new NoAvailableSlaveException("FileNotFound");
            }
        } else {
            throw new IllegalArgumentException();
        }

        return process(status, new ScoreChart(availableSlaves), conn, direction, file, null);
    }

    public RemoteSlave getASlaveForJobDownload(FileHandle file, Collection<RemoteSlave> destinationSlaves)
            throws NoAvailableSlaveException, FileNotFoundException {
        ArrayList<RemoteSlave> slaves = new ArrayList<>(file.getAvailableSlaves());

        slaves.removeAll(destinationSlaves); //remove all target slaves.

        if (slaves.isEmpty()) {
            throw new NoAvailableSlaveException();
        }

        return process("jobdown", new ScoreChart(slaves), null, Transfer.TRANSFER_SENDING_DOWNLOAD, file, null);
    }

    public RemoteSlave getASlaveForJobUpload(FileHandle file, Collection<RemoteSlave> destinationSlaves, RemoteSlave sourceSlave)
            throws NoAvailableSlaveException, FileNotFoundException {

        ArrayList<RemoteSlave> slaves = new ArrayList<>(destinationSlaves);
        slaves.removeAll(file.getAvailableSlaves()); // a slave cannot have the same file twice ;P

        // slave is not online, cannot send a file to it.
        slaves.removeIf(remoteSlave -> !remoteSlave.isAvailable());

        if (slaves.isEmpty()) {
            throw new NoAvailableSlaveException();
        }

        return process("jobup", new ScoreChart(slaves), null, Transfer.TRANSFER_SENDING_DOWNLOAD, file, sourceSlave);
    }


    private RemoteSlave process(String filterchain, ScoreChart sc, BaseFtpConnection conn, char direction, InodeHandle file,
                                RemoteSlave sourceSlave) throws NoAvailableSlaveException {
        return getFilterChain(filterchain).getBestSlave(sc, conn, direction, file, sourceSlave);
    }

    public void reload() throws IOException {
        _downChain = new FilterChain("slaveselection-down.conf", getFiltersMap());
        _upChain = new FilterChain("slaveselection-up.conf", getFiltersMap());
        _jobUpChain = new FilterChain("slaveselection-jobup.conf", getFiltersMap());
        _jobDownChain = new FilterChain("slaveselection-jobdown.conf", getFiltersMap());
    }

    public FilterChain getFilterChain(String type) {
        type = type.toLowerCase();
        return switch (type) {
            case "down" -> _downChain;
            case "up" -> _upChain;
            case "jobup" -> _jobUpChain;
            case "jobdown" -> _jobDownChain;
            default -> throw new IllegalArgumentException();
        };
    }
}
