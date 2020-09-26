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

import org.drftpd.master.GlobalContext;

import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.exceptions.NoAvailableSlaveException;

import org.drftpd.common.misc.CaseInsensitiveHashMap;

import org.drftpd.master.network.BaseFtpConnection;

import org.drftpd.master.slavemanagement.RemoteSlave;

import org.drftpd.master.usermanager.User;

import org.drftpd.common.util.ConfigLoader;

import org.drftpd.common.vfs.InodeHandleInterface;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;

/**
 * @author mog
 * @version $Id$
 */
public class FilterChain {

    private static final Logger logger = LogManager.getLogger(FilterChain.class);

    private static final Class<?>[] SIG = new Class<?>[]{int.class, Properties.class};

    private String _cfgFileName;

    private ArrayList<Filter> _filters;

    private CaseInsensitiveHashMap<String, Class<? extends Filter>> _filtersMap;

    /*
     * Guard that we do not initialize an empty FilterChain
     */
    protected FilterChain() {}

    public FilterChain(String cfgFileName, CaseInsensitiveHashMap<String, Class<? extends Filter>> filtersMap) {
        _cfgFileName = cfgFileName;
        _filtersMap = filtersMap;
        reload();
    }

    public static GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }

    public Collection<Filter> getFilters() {
        return new ArrayList<>(_filters);
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

    public void reload() {
        Properties p = ConfigLoader.loadConfig(_cfgFileName);
        reload(p);
    }

    public void reload(Properties p) {

        ArrayList<Filter> filters = new ArrayList<>();

        for (int i = 1; ; i++) {
            String filterName = p.getProperty(i + ".filter");

            if (filterName == null) {
                break;
            }

            if (!_filtersMap.containsKey(filterName)) {
                // if we can't find one filter that will be enought to brake the whole chain.
                throw new FatalException(filterName + " wasn't loaded.");
            }

            try {
                Class<? extends Filter> clazz = _filtersMap.get(filterName);
                Filter filter = clazz.getConstructor(SIG).newInstance(i, p);
                filters.add(filter);
            } catch (Exception e) {
                throw new FatalException(i + ".filter = " + filterName, e);
            }
        }

        filters.trimToSize();
        logger.debug("Loaded {} filters from {}", filters.size(), _cfgFileName);
        _filters = filters;
    }
}
