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

package org.drftpd.slave.diskselection.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.slave.Slave;
import org.drftpd.slave.diskselection.DiskSelectionInterface;
import org.drftpd.slave.vfs.Root;
import org.drftpd.slave.vfs.RootCollection;
import org.reflections.Reflections;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * DiskSelection core.<br>
 * This class takes care of processing each ScoreChart,<br>
 * loading filters and also contains the {@link #getBestRoot(String)} method.
 *
 * @author fr0w
 * @version $Id$
 */
public class DiskSelectionFilter extends DiskSelectionInterface {
    private static final Class<?>[] SIG = new Class[]{DiskSelectionFilter.class, Properties.class, Integer.class};
    private static final Logger logger = LogManager.getLogger(Slave.class);

    private ArrayList<DiskFilter> _filters;
    private final RootCollection _rootCollection;
    private CaseInsensitiveHashMap<String, Class<? extends DiskFilter>> _filtersMap;

    public DiskSelectionFilter(Slave slave) throws IOException {
        super(slave);
        _rootCollection = slave.getRoots();
        readConf();
    }

    public RootCollection getRootCollection() {
        return _rootCollection;
    }

    /**
     * Load conf/diskselection.conf
     */
    private void readConf() {
        Properties p = ConfigLoader.loadConfig("diskselection.conf");
        initFilters();
        loadFilters(p);
    }

    /**
     * Parses conf/diskselection.conf and load the filters.<br>
     * Filters classes MUST follow this naming scheme:<br>
     * First letter uppercase, and add the "Filter" in the end.<br>
     * For example: 'minfreespace' filter, class = MinfreespaceFilter.class<br>
     */
    private void loadFilters(Properties p) {
        ArrayList<DiskFilter> filters = new ArrayList<>();
        int i = 1;

        logger.info("Loading DiskSelection filters...");

        for (; ; i++) {
            String filterName = p.getProperty(i + ".filter");

            if (filterName == null) {
                break;
            }

            if (!_filtersMap.containsKey(filterName)) {
                // if we can't find one filter that will be enought to brake the whole chain.
                throw new RuntimeException(filterName + " wasn't loaded.");
            }

            try {
                Class<? extends DiskFilter> clazz = _filtersMap.get(filterName);
                DiskFilter filter = clazz.getConstructor(SIG).newInstance(this, p, i);
                filters.add(filter);
            } catch (Exception e) {
                throw new RuntimeException(i + ".filter = " + filterName, e);
            }
        }

        filters.trimToSize();
        _filters = filters;
    }

    /**
     * Creates a new ScoreChart, process it and pick up the root with more
     * positive points.
     *
     * @throws IOException
     */
    public Root getBestRoot(String path) {

        ScoreChart sc = new ScoreChart(getRootCollection());
        process(sc, path);

        ScoreChart.RootScore bestRoot = null;

        for (ScoreChart.RootScore rs : sc.getScoreList()) {
            long score = rs.getScore();

            if (bestRoot == null) {
                bestRoot = rs;
            } else if (score > bestRoot.getScore()) {
                bestRoot = rs;
            }
        }

        return bestRoot.getRoot();
    }

    /**
     * Runs the process() on all filters.
     */
    public void process(ScoreChart sc, String path) {
        for (DiskFilter filter : getFilters()) {
            filter.process(sc, path);
        }
    }

    public ArrayList<DiskFilter> getFilters() {
        return _filters;
    }

    private void initFilters() {
        CaseInsensitiveHashMap<String, Class<? extends DiskFilter>> filtersMap = new CaseInsensitiveHashMap<>();
        // TODO [DONE] @k2r Load Filters
        Set<Class<? extends DiskFilter>> aFilters = new Reflections("org.drftpd")
                .getSubTypesOf(DiskFilter.class);
        List<Class<? extends DiskFilter>> filters = aFilters.stream()
                .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers())).collect(Collectors.toList());
        try {
            for (Class<? extends DiskFilter> filterClass : filters) {
                String filterName = filterClass.getSimpleName().replace("Filter", "");
                filtersMap.put(filterName, filterClass);
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for slave extension point 'Handler', possibly the slave"
                    + " extension point definition has changed in the plugin.xml", e);
        }
        _filtersMap = filtersMap;
    }
}
