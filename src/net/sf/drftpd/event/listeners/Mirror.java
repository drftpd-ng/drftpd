package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author zubov
 *
 * @version $Id: Mirror.java,v 1.9 2004/01/13 20:30:53 mog Exp $
 */
public class Mirror implements FtpListener {

	private static final Logger logger = Logger.getLogger(Mirror.class);

	private ConnectionManager _cm;
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
		ArrayList slaveToMirror = new ArrayList();
		for (int x = 1; x < _numberOfMirrors; x++) { // already have one copy
			slaveToMirror.add(null);
		}
		//logger.info("Adding " + dir.getPath() + " to the JobList");
		_cm.getJobManager().addJob(
			new Job(dir, slaveToMirror, this, null, 5));
		logger.debug("Done adding " + dir.getPath() + " to the JobList");
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
			Integer.parseInt(FtpConfig.getProperty(props,"numberOfMirrors"));
	}
}
