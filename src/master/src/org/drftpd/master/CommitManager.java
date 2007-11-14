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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.java.plugin.PluginClassLoader;
import org.java.plugin.PluginManager;

/**
 * @author zubov
 * @version $Id$
 */
public class CommitManager {

	private static final Logger logger = Logger.getLogger(CommitManager.class);

	private Map<Commitable, Date> _commitMap = null;

	private CommitManager() {

	}

	private static CommitManager manager = new CommitManager();

	public static CommitManager getCommitManager() {
		return manager;
	}

	public synchronized void start() {
		_commitMap = new HashMap<Commitable, Date>();
		new Thread(new CommitHandler()).start();
	}

	public synchronized void add(Commitable object) {
		if (_commitMap.containsKey(object)) {
			return;
			// object already queued to write
		}
		_commitMap.put(object, new Date());
		notifyAll();
	}

	private synchronized void processAllLoop() {
		while (true) {
		long time = System.currentTimeMillis() - 10000;
			for (Iterator<Entry<Commitable, Date>> iter = _commitMap.entrySet()
					.iterator(); iter.hasNext();) {
				Entry<Commitable, Date> entry = iter.next();
				if (entry.getValue().getTime() < time) {
					try {
						entry.getKey().writeToDisk();
					} catch (IOException e) {
						logger.error("Error writing object to disk - "
								+ entry.getKey().descriptiveName(), e);
					}
					iter.remove();
				}
			}
			try {
				wait(10050);
			} catch (InterruptedException e) {
			}
		}
	}

	private class CommitHandler implements Runnable {

		private CommitHandler() {
		}

		public void run() {
			PluginManager manager = PluginManager.lookup(this);
			PluginClassLoader loader = manager.getPluginClassLoader((manager
					.getPluginFor(this)).getDescriptor());
			Thread.currentThread().setContextClassLoader(loader);
			Thread.currentThread().setName("CommitHandler");
			processAllLoop();
		}
	}

	/**
	 * Returns true if the object was removed from the CommitQueue
	 * @param object
	 * @return
	 */
	public synchronized boolean remove(Commitable object) {
		return _commitMap.remove(object) != null;
	}

}
