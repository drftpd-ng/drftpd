package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.mirroring.AbstractJob;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author zubov
 *
 * @version $Id: Mirror.java,v 1.6 2003/12/23 13:38:19 mog Exp $
 */
public class Mirror implements FtpListener {

	private ConnectionManager _cm;
	private int _numberOfMirrors;
	private int _numberOfTries;

	private Logger logger = Logger.getLogger(Mirror.class);

	public Mirror() {
		reload();
		logger.info("Mirror plugin loaded successfully");
	}

	public void actionPerformed(Event event) {
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
		for (int x = 1;
			x < _numberOfMirrors;
			x++) { // already have one copy
			slaveToMirror.add(null);
//			logger.info(
//				"Sending file "
//					+ dir.getPath()
//					+ " to "
//					+ destrslave.getName());
		}
		//logger.info("Adding " + dir.getPath() + " to the JobList");
		_cm.getJobManager().addJob(new AbstractJob(dir,slaveToMirror,this,null,5));
		//logger.info("Done adding " + dir.getPath() + " to the JobList");
		
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.Initializeable#init(net.sf.drftpd.master.ConnectionManager)
	 */
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
			Integer.parseInt(props.getProperty("numberOfMirrors"));
		_numberOfTries = Integer.parseInt(props.getProperty("numberOfTries"));
	}
}
