/*
 * Created on Dec 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.mirroring.ArchiveHandler;

/**
 * @author zubov
 * @version $Id: Archive.java,v 1.8 2004/01/04 18:53:08 zubov Exp $
 */

public class Archive implements FtpListener {
	private long _archiveAfter;
	private boolean _archiveToFreeSlave;
	private ConnectionManager _cm;
	private long _cycleTime;
	private long _lastchecked;
	private long _moveFullSlaves;
	private ArrayList archivingList = new ArrayList();

	private Logger logger = Logger.getLogger(Archive.class);

	/**
	 * 
	 */
	public Archive() {
		reload();
		logger.info("Archive plugin loaded successfully");
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.Event)
	 */
	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD"))
			reload();
		if (!(event instanceof TransferEvent))
			return;
		if (System.currentTimeMillis() - _lastchecked > _cycleTime) {
			_lastchecked = System.currentTimeMillis();
			new ArchiveHandler((DirectoryFtpEvent) event, this).start();
			System.out.println("Launched the ArchiveHandler");
		}
	}

	/**
	 * Adds directories to the list
	 */
	public void addToArchivingList(String dir) {
		archivingList.add(dir);
	}
	/**
	 * @return
	 */
	public long getArchiveAfter() {
		return _archiveAfter;
	}

	/**
	 * This list represents path names of directories currently being handled by ArchiveHandlers
	 */
	public ArrayList getArchivingList() {
		return archivingList;
	}
	/**
	 * Returns the ConnectionManager
	 */
	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	/**
	 * @return
	 */
	public long getCycleTime() {
		return _cycleTime;
	}

	/**
	 * @return
	 */
	public long getMoveFullSlaves() {
		return _moveFullSlaves;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}

	/**
	 * @return
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
		_cycleTime = 60000 * Long.parseLong(props.getProperty("cycleTime"));
		_archiveAfter = 60000 * Long.parseLong(props.getProperty("archiveAfter"));
		_archiveToFreeSlave = (props.getProperty("archiveTo") == "true");
		_moveFullSlaves = 1048576*Long.parseLong(props.getProperty("moveFullSlaves"));
		_lastchecked = System.currentTimeMillis();
	}
	/**
	 * Removes directories from the list
	 */
	public void removeFromArchivingList(String dir) {
		archivingList.remove(dir);
	}
}
