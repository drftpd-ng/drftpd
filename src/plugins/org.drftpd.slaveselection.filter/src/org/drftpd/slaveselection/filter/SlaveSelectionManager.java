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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.event.ReloadEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
	
	private CaseInsensitiveHashMap<String, Class<Filter>> _filtersMap;

	public SlaveSelectionManager() throws IOException {
		initFilters();
		reload();
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
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
		CaseInsensitiveHashMap<String, Class<Filter>> filtersMap = new CaseInsensitiveHashMap<>();

		try {
			List<PluginObjectContainer<Filter>> loadedFilters =
				CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.slaveselection.filter", "Filter", "ClassName", false);
			for (PluginObjectContainer<Filter> container : loadedFilters) {
				String filterName = container.getPluginExtension().getParameter("FilterName").valueAsString();
				filtersMap.put(filterName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.slaveselection.filter extension point 'Filter'"
					+", possibly the org.drftpd.slaveselection.filter"
					+" extension point definition has changed in the plugin.xml",e);
		}
		
		_filtersMap = filtersMap;
	}
	
	public CaseInsensitiveHashMap<String, Class<Filter>> getFiltersMap() {
		// we dont want to pass this object around allowing it to be modified, make a copy of it.
		return new CaseInsensitiveHashMap<>(_filtersMap);
	}

	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public RemoteSlave getASlave(BaseFtpConnection conn, char direction, InodeHandle file)
			throws NoAvailableSlaveException {
		String status;
		Collection<RemoteSlave> availableSlaves = null;
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

	public void reload() throws FileNotFoundException, IOException {
		_downChain = new FilterChain("conf/slaveselection-down.conf", getFiltersMap());
		_upChain = new FilterChain("conf/slaveselection-up.conf", getFiltersMap());
		_jobUpChain = new FilterChain("conf/slaveselection-jobup.conf", getFiltersMap());
		_jobDownChain = new FilterChain("conf/slaveselection-jobdown.conf", getFiltersMap());
	}
	
	public FilterChain getFilterChain(String type) {
		type = type.toLowerCase();
        switch (type) {
            case "down":
                return _downChain;
            case "up":
                return _upChain;
            case "jobup":
                return _jobUpChain;
            case "jobdown":
                return _jobDownChain;
            default:
                throw new IllegalArgumentException();
        }
	}
}
