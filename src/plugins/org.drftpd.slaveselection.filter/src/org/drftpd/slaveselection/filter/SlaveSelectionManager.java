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
import java.util.ArrayList;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.jobmanager.Job;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.vfs.InodeHandle;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author mog
 * @version $Id$
 */
public class SlaveSelectionManager extends SlaveSelectionManagerInterface {
	protected static final Logger logger = Logger.getLogger(SlaveSelectionManager.class);
			
	private FilterChain _downChain;
	private FilterChain _upChain;
	private FilterChain _jobDownChain;
	private FilterChain _jobUpChain;
	
	private CaseInsensitiveHashMap<String, Class> _filtersMap;

	public SlaveSelectionManager() throws IOException {
		initFilters();
		reload();
	}
	
	private void initFilters() {
		CaseInsensitiveHashMap<String, Class> filtersMap = new CaseInsensitiveHashMap<String, Class>();
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint exp = manager.getRegistry().getExtensionPoint("org.drftpd.slaveselection.filter", "Filter");
		
		for (Extension ext : exp.getAvailableExtensions()) {
			ClassLoader classLoader = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());
			String pluginId = ext.getDeclaringPluginDescriptor().getId();
			String filterName = ext.getParameter("FilterName").valueAsString();
			String className = ext.getParameter("ClassName").valueAsString();
			
			try {
				if (!manager.isPluginActivated(ext.getDeclaringPluginDescriptor())) {
					manager.activatePlugin(pluginId);
				}
				
				Class clazz = classLoader.loadClass(className);
				if (clazz.getSuperclass() != Filter.class) {
					logger.error(className + " does not extend Filter.class");
					continue;
				}
				
				filtersMap.put(filterName, clazz);				
			} catch (ClassNotFoundException e) {
				logger.error(className + ": was not found", e);
				continue;
			} catch (PluginLifecycleException e) {
				logger.debug("Error while activating plugin: "+pluginId, e);
				continue;
			}
		}
		
		_filtersMap = filtersMap;
	}
	
	public CaseInsensitiveHashMap<String, Class> getFiltersMap() {
		// we dont want to pass this object around allowing it to be modified, make a copy of it.
		return (CaseInsensitiveHashMap<String, Class>) _filtersMap.clone();
	}

	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public RemoteSlave getASlave(BaseFtpConnection conn, char direction, InodeHandle file)
			throws NoAvailableSlaveException {
		String status;

		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			status = "up";
		} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			status = "down";
		} else {
			throw new IllegalArgumentException();
		}

		return process(status, new ScoreChart(getAvailableSlaves()), conn, direction, file, null);
	}

	public RemoteSlave getASlaveForJobDownload(Job job)
			throws NoAvailableSlaveException, FileNotFoundException {		
		ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>(job.getFile().getAvailableSlaves());
		
		slaves.removeAll(job.getDestinationSlaves()); //remove all target slaves.

		if (slaves.isEmpty()) {
			throw new NoAvailableSlaveException();
		}

		return process("jobdown", new ScoreChart(slaves), null, Transfer.TRANSFER_SENDING_DOWNLOAD, job.getFile(), null);
	}

	public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException, FileNotFoundException {
		
		ArrayList<RemoteSlave> slaves = new ArrayList<RemoteSlave>(job.getDestinationSlaves());
		slaves.removeAll(job.getFile().getAvailableSlaves()); // a slave cannot have the same file twice ;P

		for (Iterator<RemoteSlave> iter = slaves.iterator(); iter.hasNext();) {
			if (!iter.next().isAvailable()) {
				iter.remove(); // slave is not online, cannot send a file to it.
			}
		}

		if (slaves.isEmpty()) {
			throw new NoAvailableSlaveException();
		}

		return process("jobup", new ScoreChart(slaves), null, Transfer.TRANSFER_SENDING_DOWNLOAD, job.getFile(), sourceSlave);
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
		if (type.equals("down")) {
			return _downChain;
		} else if (type.equals("up")) {
			return _upChain;
		} else if (type.equals("jobup")) {
			return _jobUpChain;
		} else if (type.equals("jobdown")) {
			return _jobDownChain;
		} else {
			throw new IllegalArgumentException();
		}
	}
}
