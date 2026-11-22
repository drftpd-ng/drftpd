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
import java.util.List;
import java.util.Properties;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author mog
 * @version $Id$
 */
public class FilterChain {

    private static final Logger logger = LogManager.getLogger(FilterChain.class);

    private static final Class<?>[] SIG = new Class<?>[]{int.class, Properties.class};

    private static final Pattern FILTER_KEY = Pattern.compile("^(\\d+)\\.filter$");

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

    /**
     * Builds a chain from already loaded properties, used by the tests.
     */
    FilterChain(String cfgFileName, CaseInsensitiveHashMap<String, Class<? extends Filter>> filtersMap, Properties p) {
        _cfgFileName = cfgFileName;
        _filtersMap = filtersMap;
        reload(p);
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

        List<Integer> numbers = getFilterNumbers(p);
        if (!isConsecutive(numbers)) {
            logger.warn("Filter numbers in {} are not consecutive from 1: {}. All of them are loaded in numeric order, "
                    + "but check that each filter's N.* settings use its own number", _cfgFileName, numbers);
        }

        for (int i : numbers) {
            String filterName = p.getProperty(i + ".filter");

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

    /**
     * Returns the numbers N of every <code>N.filter</code> key, sorted numerically.
     */
    static List<Integer> getFilterNumbers(Properties p) {
        TreeSet<Integer> numbers = new TreeSet<>();
        for (String key : p.stringPropertyNames()) {
            Matcher m = FILTER_KEY.matcher(key);
            if (m.matches()) {
                int i;
                try {
                    i = Integer.parseInt(m.group(1));
                } catch (NumberFormatException e) {
                    throw new FatalException("Invalid filter number in " + key);
                }
                if (!key.equals(i + ".filter")) {
                    // e.g. 01.filter: the filter would read its settings from 1.* instead
                    logger.warn("Ignoring {}, filter numbers must not have leading zeros", key);
                    continue;
                }
                numbers.add(i);
            }
        }
        return new ArrayList<>(numbers);
    }

    /**
     * True if the sorted numbers are exactly 1, 2, ..., n.
     */
    static boolean isConsecutive(List<Integer> numbers) {
        for (int i = 0; i < numbers.size(); i++) {
            if (numbers.get(i) != i + 1) {
                return false;
            }
        }
        return true;
    }
}
