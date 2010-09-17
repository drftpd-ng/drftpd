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
package org.drftpd.vfs.index.lucene;

import java.io.IOException;

import org.apache.log4j.Logger;
import org.apache.lucene.index.CorruptIndexException;
import org.drftpd.GlobalContext;

/**
 * Optimizes the index and update the search engine.
 * @author fr0w
 * @version $Id$
 */
public class LuceneMaintenanceThread extends Thread {
	private static final Logger logger = Logger.getLogger(LuceneMaintenanceThread.class);

	private long _currentTime;
	private int _minDelay;

	private int _optimizeInterval;
	private int _updateSearcherInterval;
	private long _lastOptimization;
	private long _lastSearcherCreation;
	
	private boolean _stop;
	
	private LuceneEngine _engine;
	
	public LuceneMaintenanceThread() {
		setName("IndexMaintenanceThread");
		_engine = (LuceneEngine) GlobalContext.getGlobalContext().getIndexEngine();
		
		_lastOptimization = System.currentTimeMillis();
		_lastSearcherCreation = System.currentTimeMillis();
	}

	public void run() {
		while (true) {
			_currentTime = System.currentTimeMillis();

			try {
				if (_stop) {
					break;
				}

				if (_currentTime >= _lastOptimization + _optimizeInterval) {
					_engine.getWriter().optimize();
					_engine.getWriter().commit();
					updateLastOptimizationTime();

					logger.debug("Index was optimized successfully.");
				}

				if (_currentTime >= _lastSearcherCreation + _updateSearcherInterval) {
					logger.debug("Creating a new IndexSearcher.");

					_engine.refreshSearcher();

					updateSearcherCreationTime();

					logger.debug("Search engine updated successfully.");
				}

				// obtaining the object monitor's.
				synchronized (this) {
					wait(_minDelay);
				}
			} catch (InterruptedException e) {
			} catch (CorruptIndexException e) {
				throw new IllegalStateException("Corrupt index, couldn't run periodical maintenance, that's bad!", e);
			} catch (IOException e) {
				throw new IllegalStateException("Corrupt index, couldn't run periodical maintenance, that's bad!", e);
			}
		}
	}
	
	/**
	 * Stops the maintenance thread.<br>
	 * If there's an operation already running when this method is called,
	 * the operation is firstly finalized and then the query is stopped.
	 */
	protected void stopMaintenance() {
		_stop = true;
	}
	
	/**
	 * Updates the searcher creation time.
	 */
	protected void updateSearcherCreationTime() {
		_lastSearcherCreation = System.currentTimeMillis();
	}
	
	/**
	 * @return the last time the search engine was updated.
	 */
	protected long getSearcherCreationTime() {
		return _lastSearcherCreation;
	}
	
	/**
	 * Updates the last optimization time.
	 */
	protected void updateLastOptimizationTime() {
		_lastOptimization = System.currentTimeMillis();
	}
	
	/**
	 * @return the last time the index was optimized.
	 */
	protected long getLastOptimizationTime() {
		return _lastOptimization;
	}
	
	/**
	 * Sets how frequently the optimization process is executed.
	 * @param interval
	 */
	protected void setOptimizationInterval(int interval) {
		_optimizeInterval = interval;
		updateMinimumDelay();
	}

	/**
	 * Sets how frequently the search engine is updated.
	 * @param interval
	 */
	protected void setSearcherCreationInterval(int interval) {
		_updateSearcherInterval = interval;
		updateMinimumDelay();
	}
	
	private void updateMinimumDelay() {
		_minDelay = Math.min(_optimizeInterval, _updateSearcherInterval);		
	}
}
