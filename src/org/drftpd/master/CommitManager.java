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
import org.drftpd.usermanager.javabeans.BeanUser;
import org.drftpd.vfs.VirtualFileSystemInode;

/**
 * @version $Id$
 */
public class CommitManager {

	private static final Logger logger = Logger.getLogger(CommitManager.class);
	
	private static Map<BeanUser, Date> _commitUser = null;
	private static Map<VirtualFileSystemInode, Date> _commitInode = null;
	private static Map<RemoteSlave, Date> _commitSlave = null;
	
	private CommitManager() {
		
	}
	
	public static void start() {
		_commitUser = new HashMap<BeanUser, Date>();
		_commitInode = new HashMap<VirtualFileSystemInode, Date>();
		_commitSlave = new HashMap<RemoteSlave, Date>();
		new Thread(new CommitHandler()).start();
	}
	
	public static void add(BeanUser user) {
		synchronized(_commitUser) {
			// overwrites previous value if it exists
			_commitUser.put(user, new Date());
		}
	}
	
	public static void add(RemoteSlave slave) {
		synchronized(_commitSlave) {
			// overwrites previous value if it exists
			_commitSlave.put(slave, new Date());
		}
	}
	
	public static void add(VirtualFileSystemInode inode) {
		synchronized(_commitInode) {
			// overwrites previous value if it exists
			_commitInode.put(inode, new Date());
		}
	}
	
	private static void processAll(Map map) {
		synchronized (map) {
			long time = System.currentTimeMillis();
			for (Iterator<Entry<Commitable, Date>> iter = map.entrySet()
					.iterator(); iter.hasNext();) {
				Entry<Commitable, Date> entry = iter.next();
				if (time - entry.getValue().getTime() > 5000) {
					try {
						entry.getKey().writeToDisk();
					} catch (IOException e) {
						logger.error("Error writing object to disk - " + entry.getKey().descriptiveName(), e);
					}
					iter.remove();
				}
			}
		}

	}
	
	static class CommitHandler implements Runnable {

		public void run() {
			while(true) {
				processAll(_commitInode);
				processAll(_commitSlave);
				processAll(_commitUser);
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	
}
