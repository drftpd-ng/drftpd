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

package org.drftpd.slave.diskselection;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.drftpd.slave.Root;
import org.drftpd.slave.RootCollection;
import org.drftpd.slave.Slave;

/**
 * DiskSelection core.<br>
 * This class takes care of processing each ScoreChart,<br>
 * loading filters and also contains the getBestRoot() method.
 * 
 * @author fr0w
 */
public class DiskSelection {

	private static final Logger logger = Logger.getLogger(Slave.class);

	private ArrayList _filters;

	private RootCollection _rootCollection;

	private static DiskSelection _diskSelection;

	private DiskSelection(RootCollection rootCollection) throws IOException {
		_rootCollection = rootCollection;
	}

	public static void init(RootCollection rootCollection) throws IOException {
		if (_diskSelection != null) {
			throw new RuntimeException(
					"DiskSelection has already been initialized.");
		}
		_diskSelection = new DiskSelection(rootCollection);
                _diskSelection.readConf();
	}

	public static DiskSelection getDiskSelection() {
		if (_diskSelection == null) {
			throw new NullPointerException(
					"DiskSelection should already have been initialized.");
		}
		return _diskSelection;
	}

	public RootCollection getRootCollection() {
		return _rootCollection;
	}

	/**
	 * Load conf/diskselection.conf
	 * 
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
		loadFilters(p);
	}

	/**
	 * Parses conf/diskselection.conf and load the filters.<br>
	 * Filters classes MUST follow this naming scheme:<br>
	 * First letter uppercase, and add the "Filter" in the end.<br>
	 * For example: 'minfreespace' filter, class = MinfreespaceFilter.class<br>
	 */
	private void loadFilters(Properties p) {
		ArrayList filters = new ArrayList();
		int i = 1;

		logger.info("Loading DiskSelection filters...");

		Class[] constructor = new Class[] { Properties.class, Integer.class };

		for (;; i++) {
			String type = p.getProperty(i + ".filter");

			if (type == null) {
				break;
			}

			if (type.indexOf('.') == -1) {
				type = "org.drftpd.slave.diskselection."
						+ type.substring(0, 1).toUpperCase()
						+ type.substring(1) + "Filter";
			}

			try {
				DiskFilter filter = (DiskFilter) Class.forName(type)
						.getConstructor(constructor).newInstance(
								new Object[] { p, new Integer(i) });
				filters.add(filter);
				logger.info("Filter loaded: " + filter.getClass().getName());
			} catch (Exception e) {
				throw new RuntimeException(i + ".filter = " + type, e);
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
	public Root getBestRoot(String path) throws IOException {

		ScoreChart sc = new ScoreChart(getRootCollection());
		process(sc, path);

		long bestScore = 0L;
		Root bestRoot = null;

		for (Iterator iter = getRootCollection().iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			if (sc.getRootScore(root) > bestScore)
				bestRoot = root;
		}

		return bestRoot;
	}

	/**
	 * Runs the process() on all filters.
	 */
	public void process(ScoreChart sc, String path) {
		for (Iterator iter = getFilters().iterator(); iter.hasNext();) {
			DiskFilter filter = (DiskFilter) iter.next();
			filter.process(sc, path);
		}
	}

	public ArrayList getFilters() {
		return _filters;
	}
}
