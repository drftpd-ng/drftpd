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
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.ExcludePath;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.ArchiveHandler;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * @author zubov
 * @version $Id: Archive.java,v 1.20 2004/03/15 14:06:23 zubov Exp $
 */

public class Archive implements FtpListener, Runnable {

	private static final Logger logger = Logger.getLogger(Archive.class);
	private long _archiveAfter;
	private boolean _archiveToFreeSlave;
	private ArrayList _archivingList = new ArrayList();
	private ConnectionManager _cm;
	private long _cycleTime;
	private ArrayList _exemptList = new ArrayList();
	private long _moveFullSlaves;
	private boolean _isStopped = false;
	private Thread thread = null;
	private int _maxArchive;

	/**
	 * 
	 */
	public Archive() {
		reload();
		logger.info("Archive plugin loaded successfully");
	}

	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD")) {
			reload();
			return;
		}
//		if (!(event instanceof TransferEvent))
//			return;
//		if (System.currentTimeMillis() - _lastchecked > _cycleTime) {
//			_lastchecked = System.currentTimeMillis();
//			new ArchiveHandler((DirectoryFtpEvent) event, this).start();
//			logger.debug("Launched the ArchiveHandler");
//		}
	}

	/**
	 * Adds directories to the list
	 */
	public synchronized void addToArchivingList(String dir) {
		_archivingList.add(dir);
	}

	/**
	 * @param lrf
	 * Returns true if lrf.getPath() is excluded
	 */
	public boolean checkExclude(LinkedRemoteFileInterface lrf) {
		for (Iterator iter = _exemptList.iterator(); iter.hasNext();) {
			ExcludePath ep = (ExcludePath) iter.next();
			if (ep.checkPath(lrf))
				return true;
		}
		return false;
	}
	/**
	 * Returns the archiveAfter setting
	 */
	public long getArchiveAfter() {
		return _archiveAfter;
	}

	/**
	 * This list represents path names of directories currently being handled by ArchiveHandlers
	 */
	public ArrayList getArchivingList() {
		return _archivingList;
	}
	/**
	 * Returns the ConnectionManager
	 */
	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	/**
	 * Returns the getCycleTime setting
	 */
	public long getCycleTime() {
		return _cycleTime;
	}

	//	/**
	//	 * Returns the moveFullSlaves setting
	//	*/
	//	public long getMoveFullSlaves() {
	//		return _moveFullSlaves;
	//	}

	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
		_cm.loadJobManager();
		startArchive();
	}

	/**
	 * Returns the archiveToFreeSlave setting
	 */
	public boolean isArchiveToFreeSlave() {
		return _archiveToFreeSlave;
	}
	
	public void startArchive() {
		if (thread != null) {
			stopArchive();
			thread.interrupt();
			while(thread.isAlive()) {
				logger.debug("thread is still alive");
				Thread.yield();
			}
		}
		_isStopped = false;
		thread = new Thread(this,"ArchiveStarter");
		thread.start();
	}
	
	public void stopArchive() {
		_isStopped = true;
	}
	
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_maxArchive = Integer.parseInt(FtpConfig.getProperty(props,"maxArchive"));
		_cycleTime =
			60000 * Long.parseLong(FtpConfig.getProperty(props, "cycleTime"));
		_archiveAfter =
			60000
				* Long.parseLong(FtpConfig.getProperty(props, "archiveAfter"));
		_archiveToFreeSlave =
			(FtpConfig.getProperty(props, "archiveToFreeSlave").equals("true"));
		_exemptList = new ArrayList();
		for (int i = 1;; i++) {
			String path = props.getProperty("exclude." + i);
			if (path == null)
				break;
			try {
				ExcludePath.makePermission(_exemptList, path);
			} catch (MalformedPatternException e1) {
				throw new RuntimeException(e1);
			}
		}
	}

	/**
	 * Removes directories from the list
	 */
	public synchronized void removeFromArchivingList(String dir) {
		_archivingList.remove(dir);
	}

	public void unload() {
		stopArchive();
	}

	private boolean isStopped() {
		return _isStopped;
	}

	public void run() {
		while(true) {
			if (isStopped()) {
				logger.debug("Stopping ArchiveStarter thread");
				return;
			}
			if (_archivingList.size() < _maxArchive)
				new ArchiveHandler(this).start();
			try {
				Thread.sleep(_cycleTime);
			} catch (InterruptedException e) {
			}
		}
	}

}
