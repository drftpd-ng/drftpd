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

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.slave.Root;
import org.drftpd.slave.RootCollection;
import org.drftpd.slave.Slave;
import org.drftpd.slave.diskselection.DiskSelectionInterface;
import org.drftpd.slave.diskselection.filter.ScoreChart.RootScore;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * DiskSelection core.<br>
 * This class takes care of processing each ScoreChart,<br>
 * loading filters and also contains the {@link #getBestRoot(String)} method.
 * 
 * @author fr0w
 * @version $Id$
 */
public class DiskSelectionFilter extends DiskSelectionInterface{
	private static final Class[] SIG = new Class[] { DiskSelectionFilter.class, Properties.class, Integer.class };
	private static final Logger logger = Logger.getLogger(Slave.class);

	private ArrayList<DiskFilter> _filters;
	private RootCollection _rootCollection;	
	private CaseInsensitiveHashMap<String, Class<DiskFilter>> _filtersMap;

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
	 * @throws IOException
	 */
	private void readConf() throws IOException {
		Properties p = new Properties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream("conf/diskselection.conf");
			p.load(fis);
		} finally {
			if (fis != null) {
				fis.close();
				fis = null;
			}
		}
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
		ArrayList<DiskFilter> filters = new ArrayList<DiskFilter>();
		int i = 1;

		logger.info("Loading DiskSelection filters...");

		for (;; i++) {
			String filterName = p.getProperty(i + ".filter");

			if (filterName == null) {
				break;
			}

			if (!_filtersMap.containsKey(filterName)) {
				// if we can't find one filter that will be enought to brake the whole chain.
				throw new RuntimeException(filterName + " wasn't loaded.");
			}
			
			try {
				Class<DiskFilter> clazz = _filtersMap.get(filterName);
				DiskFilter filter = clazz.getConstructor(SIG).newInstance(new Object[] { this, p, new Integer(i) });
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

		RootScore bestRoot = null;
		
		for (RootScore rs : sc.getScoreList()) {
			long score = rs.getScore();
			if (bestRoot != null && score > bestRoot.getScore()) {
				bestRoot = rs;
			} else {
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
		CaseInsensitiveHashMap<String, Class<DiskFilter>> filtersMap = new CaseInsensitiveHashMap<String, Class<DiskFilter>>();
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint exp = manager.getRegistry().getExtensionPoint("org.drftpd.slave.diskselection.filter", "DiskFilter");
		
		for (Extension ext : exp.getAvailableExtensions()) {
			ClassLoader classLoader = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());
			String pluginId = ext.getDeclaringPluginDescriptor().getId();
			String filterName = ext.getParameter("FilterName").valueAsString();
			String className = ext.getParameter("ClassName").valueAsString();
			
			try {
				if (!manager.isPluginActivated(ext.getDeclaringPluginDescriptor())) {
					manager.activatePlugin(pluginId);
				}
				
				Class<DiskFilter> clazz = (Class<DiskFilter>) classLoader.loadClass(className);
			
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
}
