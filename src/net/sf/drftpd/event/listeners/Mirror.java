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
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.ExcludePath;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * @author zubov
 *
 * @version $Id: Mirror.java,v 1.17 2004/03/01 04:21:03 zubov Exp $
 */
public class Mirror implements FtpListener {

	private static final Logger logger = Logger.getLogger(Mirror.class);

	private ConnectionManager _cm;
	private ArrayList _exemptList;
	private boolean _mirrorAllSFV;
	private int _numberOfMirrors;

	public Mirror() {
		reload();
		logger.info("Mirror plugin loaded successfully");
	}

	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD"))
			reload();
		if (!(event instanceof TransferEvent))
			return;
		TransferEvent transevent = (TransferEvent) event;
		if (!transevent.getCommand().equals("STOR"))
			return;
		LinkedRemoteFile dir;
		dir = transevent.getDirectory();
		if (checkExclude(dir)) {
			logger.debug(dir.getPath() + " is exempt");
			return;
		}
		ArrayList slaveToMirror = new ArrayList();
		int numToMirror = _numberOfMirrors;
		if (_mirrorAllSFV && dir.getName().toLowerCase().endsWith(".sfv")) {
			numToMirror = _cm.getSlaveManager().getSlaveList().size();
		}
		for (int x = 1; x < numToMirror; x++) { // already have one copy
			slaveToMirror.add(null);
		}
		if (slaveToMirror.isEmpty()) {
			logger.debug("slaveToMirror was empty, returning");
			return;
		}
		_cm.getJobManager().addJob(new Job(dir, slaveToMirror, this, null, 5));
		logger.debug("Done adding " + dir.getPath() + " to the JobList");
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

	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
		_cm.loadJobManager();
	}
	
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/mirror.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		_numberOfMirrors =
			Integer.parseInt(FtpConfig.getProperty(props, "numberOfMirrors"));
		_mirrorAllSFV =
			FtpConfig.getProperty(props, "mirrorAllSFV").equals("true");
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

	public void unload() {

	}

}
