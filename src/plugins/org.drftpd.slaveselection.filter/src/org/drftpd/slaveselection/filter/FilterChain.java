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

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.FatalException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * @author mog
 * @version $Id$
 */
public class FilterChain {
	private static Class<?>[] SIG = new Class<?>[] { int.class, Properties.class };
	
	private String _cfgfileName;

	private ArrayList<Filter> _filters;
	
	private CaseInsensitiveHashMap<String, Class<Filter>> _filtersMap;

	protected FilterChain() {
	}
	
	public Collection<Filter> getFilters() {
		return new ArrayList<>(_filters);
	}

	public FilterChain(String cfgFileName, CaseInsensitiveHashMap<String, Class<Filter>> filtersMap) throws FileNotFoundException, IOException {
		_cfgfileName = cfgFileName;
		_filtersMap = filtersMap;
		reload();
	}

	public void filter(ScoreChart sc, BaseFtpConnection conn,
			char direction, InodeHandleInterface file, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		
		User u = null;
		InetAddress peer = null;
		
		if (conn != null) {
			u = conn.getUserNull();
			peer = conn.getClientAddress();
		}
		
		for (Filter filter : _filters) {
			filter.process(sc, u, peer, direction, file, sourceSlave);
		}
	}

	public RemoteSlave getBestSlave(ScoreChart sc, BaseFtpConnection conn, char direction, InodeHandleInterface file, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		filter(sc, conn, direction, file, sourceSlave);
		RemoteSlave rslave = sc.getBestSlave();
		rslave.setLastDirection(direction, System.currentTimeMillis());
		return rslave;
	}

	public void reload() throws FileNotFoundException, IOException {
		Properties p = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(_cfgfileName);
			p.load(fis);
		} finally {
			if (fis != null) {
				fis.close();
				fis = null;
			}
		}
		reload(p);
	}

	public void reload(Properties p) {
		ArrayList<Filter> filters = new ArrayList<>();
		int i = 1;

		for (;; i++) {
			String filterName = p.getProperty(i + ".filter");

			if (filterName == null) {
				break;
			}

			if (!_filtersMap.containsKey(filterName)) {
				// if we can't find one filter that will be enought to brake the whole chain.
				throw new FatalException(filterName + " wasn't loaded.");
			}

			try {
				Class<Filter> clazz = _filtersMap.get(filterName);
				Filter filter = clazz.getConstructor(SIG).newInstance(i, p);
				filters.add(filter);
			} catch (Exception e) {
				throw new FatalException(i + ".filter = " + filterName, e);
			}
		}

		filters.trimToSize();
		_filters = filters;
	}

	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}
}
