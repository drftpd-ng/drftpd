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

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * @author zubov
 *
 * @version $Id: Mirror.java,v 1.10 2004/01/20 04:33:10 zubov Exp $
 */
public class Mirror implements FtpListener {

	private static final Logger logger = Logger.getLogger(Mirror.class);

	private ConnectionManager _cm;
	private ArrayList _exemptList;
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
		if (_numberOfMirrors < 2)
			return;
		LinkedRemoteFile dir;
		dir = transevent.getDirectory();
		if (checkExclude(dir)) {
			logger.debug(dir.getPath() + " is exempt");
			return;
		}
		ArrayList slaveToMirror = new ArrayList();
		for (int x = 1; x < _numberOfMirrors; x++) { // already have one copy
			slaveToMirror.add(null);
		}
		//logger.info("Adding " + dir.getPath() + " to the JobList");
		_cm.getJobManager().addJob(new Job(dir, slaveToMirror, this, null, 5));
		logger.debug("Done adding " + dir.getPath() + " to the JobList");
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

	public void init(ConnectionManager connectionManager) {
		_cm = connectionManager;
	}
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("mirror.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		_numberOfMirrors =
			Integer.parseInt(FtpConfig.getProperty(props, "numberOfMirrors"));
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
