/*
 * Created on Jan 3, 2004
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.ExcludePath;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFileLastModifiedComparator;

import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * @author zubov
 * @version $Id: AutoFreeSpace.java,v 1.6 2004/01/20 04:18:41 zubov Exp $
 */
public class AutoFreeSpace implements FtpListener {
	private static final Logger logger = Logger.getLogger(AutoFreeSpace.class);

	public static void main(String args[]) throws IOException {
		BasicConfigurator.configure();
		List rslaves = SlaveManagerImpl.loadRSlaves();
		LinkedRemoteFile root =
			SlaveManagerImpl.loadMLSTFileDatabase(rslaves, null);

		AutoFreeSpace space = new AutoFreeSpace();
		space.deleteOldReleases(root.getDirectories(), 0);
	}
	private long _archiveAfter;

	private ConnectionManager _cm;
	private long _cycleTime;
	private ArrayList _exemptList;
	private int _keepCopies;
	private long _keepFree;
	private long _lastchecked = System.currentTimeMillis();
	public AutoFreeSpace() {
		reload();
	}

	public void actionPerformed(Event event) {
		if (event.getCommand().equals("RELOAD"))
			reload();
		if (!(event instanceof TransferEvent))
			return;
		TransferEvent transevent = (TransferEvent) event;
		if (!transevent.getCommand().equals("STOR"))
			return;
		if (System.currentTimeMillis() - _lastchecked <= _cycleTime) {
			return;
		}
		_lastchecked = System.currentTimeMillis();

		//		Collection slaveList = null;
		//		try {
		//			slaveList = _cm.getSlaveManager().getAvailableSlaves();
		//		} catch (NoAvailableSlaveException e) {
		//			// done, can't remove space from nonexistant slaves
		//		}
		//		while (true) {
		//			RemoteSlave rslave = _cm.getSlaveManager().findSmallestFreeSlave();
		//			SlaveStatus status = null;
		//			try {
		//				status = rslave.getStatus();
		//				if (_keepFree > status.getDiskSpaceAvailable()) {
		//					deleteOldReleases(rslave);
		//				} else {
		//					break;
		//				}
		//			} catch (RemoteException e1) {
		//				rslave.handleRemoteException(e1);
		//			} catch (NoAvailableSlaveException e1) {
		//			}
		//		}
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
	 * Deletes the oldest directories until _keepFree space is available.
	 * @param dirs
	 */
	private void deleteOldReleases(Collection rootDirs, long spaceAvailable) {
		//Collection rootDirs = getConnectionManager().getSlaveManager().getRoot().getDirectories();
		ArrayList dirs = new ArrayList();
		for (Iterator iter = rootDirs.iterator(); iter.hasNext();) {
			LinkedRemoteFile lrf = (LinkedRemoteFile) iter.next();
			if (checkExclude(lrf)) {
				logger.debug(lrf.getPath() + " is excluded");
				continue;
			}
			dirs.addAll(lrf.getDirectories());
		}
		Collections.sort(dirs, new RemoteFileLastModifiedComparator(true));

		//		long spaceAvailable =
		//			getConnectionManager()
		//				.getSlaveManager()
		//				.getAllStatus()
		//				.getDiskSpaceAvailable();
		if (spaceAvailable < _keepFree) {
			for (Iterator iter = dirs.iterator(); iter.hasNext();) {
				logger.debug(
					Bytes.formatBytes(spaceAvailable)
						+ " < "
						+ Bytes.formatBytes(_keepFree));
				if (spaceAvailable >= _keepFree)
					break;
				LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
				spaceAvailable += file.length();
				logger.debug("delete " + file.getPath());
				//file.delete();
			}
		}
	}

	private void deleteOldReleases(RemoteSlave slave) {
	}

	private ConnectionManager getConnectionManager() {
		return _cm;
	}
	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/autofreespace.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		reload(props);
	}
	private void reload(Properties props) {
		_cycleTime =
			60000 * Long.parseLong(FtpConfig.getProperty(props, "cycleTime"));
		_keepFree = Bytes.parseBytes(FtpConfig.getProperty(props, "keepFree"));
		_archiveAfter =
			60000
				* Long.parseLong(FtpConfig.getProperty(props, "archiveAfter"));
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
}
