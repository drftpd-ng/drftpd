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
import org.drftpd.usermanager.User;
import org.drftpd.vfs.VirtualFileSystemInode;
import org.java.plugin.PluginClassLoader;
import org.java.plugin.PluginManager;

/**
 * @author zubov
 * @version $Id$
 */
public class CommitManager {

	private static final Logger logger = Logger.getLogger(CommitManager.class);

	Map<Commitable, Date> _commitMap = null;

	private CommitManager() {

	}

	private static CommitManager manager = new CommitManager();

	public static CommitManager getCommitManager() {
		return manager;
	}

	public void start() {
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

	@SuppressWarnings("unchecked")
	private synchronized void processAll(Map<Commitable, Date> map) {
		long time = System.currentTimeMillis();
		for (Iterator<Entry<Commitable, Date>> iter = map.entrySet().iterator(); iter
				.hasNext();) {
			Entry<Commitable, Date> entry = iter.next();
			if (time - entry.getValue().getTime() > 10000) {
				try {
					entry.getKey().writeToDisk();
				} catch (IOException e) {
					logger.error("Error writing object to disk - "
							+ entry.getKey().descriptiveName(), e);
				}
				iter.remove();
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
			while (true) {
				synchronized (CommitManager.getCommitManager()) {
					processAll(_commitMap);
					try {
						CommitManager.getCommitManager().wait(10500);
						// this will wake up when add() is called or a maximum
						// of 10500
					} catch (InterruptedException e) {
					}
				}
			}
		}
	}

}
