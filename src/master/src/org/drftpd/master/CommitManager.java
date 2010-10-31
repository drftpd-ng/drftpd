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

package org.drftpd.master;

import java.io.IOException;
import java.util.Date;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.log4j.Logger;
import org.drftpd.util.CommonPluginUtils;

/**
 * This classes handle all XML commits.
 * The main purpose of having this is to avoiding serializing the same object tons of times,
 * even if it data was not changed. 
 * @author zubov
 * @version $Id$
 */
public class CommitManager {

	private static final Logger logger = Logger.getLogger(CommitManager.class);

	private static CommitManager _instance;

	private ConcurrentHashMap<Commitable, Date> _commitMap;
	private boolean _isStarted;
	private AtomicInteger _queueSize;
	private volatile boolean _drainQueue;
	private Thread _commitThread;

	/**
	 * Private constructor in order to make this class a Singleton.
	 */
	private CommitManager() {
		_commitMap = new ConcurrentHashMap<Commitable, Date>();
		_queueSize = new AtomicInteger();
	}

	/**
	 * @return the unique CommitManager instance, creating the instance if it does not exist yet.
	 */
	public static CommitManager getCommitManager() {
		if (_instance == null) {
			_instance = new CommitManager();
		}
		return _instance;
	}

	/**
	 * Starts the {@link CommitHandler}.
	 * @throws IllegalStateException if the thread has already started.
	 */
	public void start() {
		if (_isStarted) {
			throw new IllegalStateException("The CommitManager is already started");
		}
		
		_isStarted = true;
		_commitThread = new Thread(new CommitHandler());
		_commitThread.start();
	}

	/**
	 * Adds a {@link Commitable} object to the commit queue.
	 * If the object is already present on the queue, this call is just ignored.
	 * @param object
	 */
	public void add(Commitable object) {
		if (_commitMap.containsKey(object)) {
			return;
			// object already queued to write
		}
		_commitMap.put(object, new Date());
		_queueSize.incrementAndGet();
	}
	

	/**
	 * @param object
	 * @return true if the object was removed from the CommitQueue, false otherwise.
	 */
	public boolean remove(Commitable object) {
		boolean removed = _commitMap.remove(object) != null;
		if (removed) {
			_queueSize.decrementAndGet();
		}
		return removed;
	}
	
	/**
	 * @param object
	 * @return true if the object is present on the CommitQueue, false otherwise.
	 */
	public boolean contains(Commitable object) {
		return _commitMap.containsKey(object);
	}

	/**
	 * 
	 * @return the number of outstanding objects to commit.
	 */
	public int getQueueSize() {
		return _queueSize.get();
	}

	/**
	 * Forces the immediate write of a (@link Commitable) if present in the commit queue.
	 * @param object
	 */
	public void flushImmediate(Commitable object) {
		if (_commitMap.containsKey(object)) {
			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			writeCommitable(object);
			Thread.currentThread().setContextClassLoader(prevCL);
		}
	}

	/**
	 * Instructs the commitmanager to write all queued items regardless of age, this cannot
	 * be undone and is used only when the process enters shutdown mode.
	 */
	public void enableQueueDrain() {
		_drainQueue = true;
		// Wakeup the commit thread incase it is sleeping
		if (_commitThread != null) {
			_commitThread.interrupt();
		}
		return;
	}

	private void processAllLoop() {
		while (true) {
			long time = System.currentTimeMillis() - 10000;
			for (Iterator<Entry<Commitable, Date>> iter = _commitMap.entrySet()
					.iterator(); iter.hasNext();) {
				Entry<Commitable, Date> entry = iter.next();
				if (entry.getValue().getTime() < time || _drainQueue) {
					writeCommitable(entry.getKey());
				}
			}
			
			try {
				Thread.sleep(10000);
			} catch (InterruptedException e) {
			}
		}
	}

	private void writeCommitable(Commitable item) {
		try {
			item.writeToDisk();
			if (_commitMap.remove(item) != null) {
				_queueSize.decrementAndGet();
			}
		} catch (IOException e) {
			logger.error("Error writing object to disk - "
					+ item.descriptiveName(), e);
		} catch (Exception e) {
			logger.error("Error writing object to disk - "
					+ item.descriptiveName(), e);
		}
	}

	private class CommitHandler implements Runnable {

		private CommitHandler() {
		}

		public void run() {
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			Thread.currentThread().setName("CommitHandler");
			processAllLoop();
		}
	}
}
