package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.ExcludePath;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.ArchiveHandler;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author zubov
 * @version $Id: Archive.java,v 1.13 2004/01/20 04:18:41 zubov Exp $
 */

public class Archive implements FtpListener {

	private static final Logger logger = Logger.getLogger(Archive.class);
	private long _archiveAfter;
	private boolean _archiveToFreeSlave;
	private ArrayList _archivingList = new ArrayList();
	private ConnectionManager _cm;
	private long _cycleTime;
	private ArrayList _exemptList = new ArrayList();
	private long _lastchecked;
	private long _moveFullSlaves;

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
		if (!(event instanceof TransferEvent))
			return;
		if (System.currentTimeMillis() - _lastchecked > _cycleTime) {
			_lastchecked = System.currentTimeMillis();
			new ArchiveHandler((DirectoryFtpEvent) event, this).start();
			logger.debug("Launched the ArchiveHandler");
		}
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
	public boolean checkExclude(LinkedRemoteFile lrf) {
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
	}

	/**
	 * Returns the archiveToFreeSlave setting
	 */
	public boolean isArchiveToFreeSlave() {
		return _archiveToFreeSlave;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_cycleTime =
			60000 * Long.parseLong(FtpConfig.getProperty(props, "cycleTime"));
		_archiveAfter =
			60000
				* Long.parseLong(FtpConfig.getProperty(props, "archiveAfter"));
		_archiveToFreeSlave =
			(FtpConfig.getProperty(props, "archiveToFreeSlave").equals("true"));
		//_moveFullSlaves = 1048576*Long.parseLong(FtpConfig.getProperty(props,"moveFullSlaves"));
		_lastchecked = System.currentTimeMillis();
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
}
