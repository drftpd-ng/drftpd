/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 * 
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.event.listeners;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.mirroring.ArchiveHandler;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.sections.SectionInterface;
/**
 * @author zubov
 * @version $Id: Archive.java,v 1.27 2004/05/20 14:08:59 zubov Exp $
 */
public class Archive implements FtpListener, Runnable {
	private Properties _props;
	private static final Logger logger = Logger.getLogger(Archive.class);
	public static Logger getLogger() {
		return logger;
	}
	private HashMap _archiveTypes;
	private ConnectionManager _cm;
	private long _cycleTime;
	private ArrayList _exemptList = new ArrayList();
	private boolean _isStopped = false;
	private Thread thread = null;
	private ArrayList _archiveHandlers;
	public Archive() {
		logger.info("Archive plugin loaded successfully");
		_archiveHandlers = new ArrayList();
	}
	public Properties getProperties() {
		return _props;
	}
	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD")) {
			reload();
			return;
		}
	}
	/**
	 * @param lrf
	 *            Returns true if lrf.getPath() is excluded
	 */
	public boolean checkExclude(SectionInterface section) {
		return _exemptList.contains(section.getName());
	}
	/**
	 * @return the correct ArchiveType for the @section - if the ArchiveType is still being used, it will return null
	 */
	public ArchiveType getArchiveType(SectionInterface section) {
		ArchiveType archiveType = (ArchiveType) _archiveTypes.get(section);
		if (archiveType == null)
			throw new IllegalStateException(
					"Could not find an archive type for "
							+ section.getName()
							+ ", check you make sure default.archiveType is defined in archive.conf and the section is not excluded");
		return archiveType;
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
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
		_cm.loadJobManager();
		reload();
		startArchive();
	}
	private boolean isStopped() {
		return _isStopped;
	}
	private void reload() {
		_props = new Properties();
		try {
			_props.load(new FileInputStream("conf/archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_cycleTime = 60000 * Long.parseLong(FtpConfig.getProperty(_props,
				"cycleTime"));
		_exemptList = new ArrayList();
		for (int i = 1;; i++) {
			String path = _props.getProperty("exclude." + i);
			if (path == null)
				break;
			_exemptList.add(path);
		}
		_archiveTypes = new HashMap();
		Class[] classParams = {Archive.class, SectionInterface.class};
		for (Iterator iter = getConnectionManager().getSectionManager()
				.getSections().iterator(); iter.hasNext();) {
			SectionInterface section = (SectionInterface) iter.next();
			if (checkExclude(section))
				// don't have to build an archiveType for sections that won't be
				// archived
				continue;
			ArchiveType archiveType = null;
			String name = null;
			try {
				name = FtpConfig.getProperty(_props, section.getName()
						+ ".archiveType");
			} catch (NullPointerException e) {
				name = FtpConfig.getProperty(_props, "default.archiveType");
			}
			Constructor constructor = null;
			try {
				constructor = Class.forName(
						"org.drftpd.mirroring.archivetypes."
								+ name).getConstructor(classParams);
			} catch (Exception e1) {
				throw new RuntimeException("Unable to load ArchiveType for section " + section.getName(), e1);
			}
			Object[] objectParams = { this, section };
			try {
				archiveType = (ArchiveType) constructor.newInstance(objectParams);
			} catch (Exception e2) {
				throw new RuntimeException("Unable to load ArchiveType for section " + section.getName(), e2);
			}
			_archiveTypes.put(section, archiveType);
			logger.debug("added archiveType for section " + section.getName());
		}
	}
	public void run() {
		while (true) {
			if (isStopped()) {
				logger.debug("Stopping ArchiveStarter thread");
				return;
			}
			for (Iterator iter = _archiveHandlers.iterator(); iter.hasNext();) {
				ArchiveHandler archiveHandler = (ArchiveHandler) iter.next();
				if (!archiveHandler.isAlive()) {
					iter.remove();
				}
			}
			Collection sectionsToCheck = getConnectionManager()
					.getSectionManager().getSections();
			for (Iterator iter = sectionsToCheck.iterator(); iter.hasNext();) {
				SectionInterface section = (SectionInterface) iter.next();
				if (checkExclude(section))
					continue;
				ArchiveType archiveType = getArchiveType(section);
				if (archiveType.isBusy()) // archiveType was not done with it's
					continue;			 // current send, cannot process another
				ArchiveHandler archiveHandler = new ArchiveHandler(archiveType);
				archiveHandler.start();
			}
			try {
				Thread.sleep(_cycleTime);
			} catch (InterruptedException e) {
			}
		}
	}
	public void startArchive() {
		if (thread != null) {
			stopArchive();
			thread.interrupt();
			while (thread.isAlive()) {
				Thread.yield();
			}
		}
		_isStopped = false;
		thread = new Thread(this, "ArchiveStarter");
		thread.start();
	}
	public void stopArchive() {
		_isStopped = true;
	}
	public void unload() {
		stopArchive();
	}
}