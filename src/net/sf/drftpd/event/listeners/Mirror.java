/*
 * Created on Dec 2, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.CooperativeSlaveTransfer;

import org.apache.log4j.Logger;

/**
 * @author zubov
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
		RemoteSlave destrslave = null;
		ArrayList mirrorSlaves = new ArrayList();
		int maxSize;
		try {
			maxSize = _cm.getSlaveManager().getAvailableSlaves().size();
		} catch (NoAvailableSlaveException e) {
			maxSize = 0;
		}
		mirrorSlaves.addAll(dir.getSlaves()); // already mirrored files
		for (int x = 1;
			x < Math.min(_numberOfMirrors, maxSize);
			x++) { // already have one copy sent
			try {
				while (true) {
					destrslave =
						_cm.getSlaveManager().getASlave(
							Transfer.TRANSFER_RECEIVING_UPLOAD);
					if (mirrorSlaves.contains(destrslave)) {
						try {
							Thread.sleep(5);
						} catch (InterruptedException e2) {
							// just sleeping till the slaves are in a new state
						}
						continue;
					}
					mirrorSlaves.add(destrslave);
					break;
				}
			} catch (NoAvailableSlaveException e1) {
				logger.error(
					"Failed on getting a slave to mirror " + dir.getPath());
				// what should i do if there's no slave to transfer to?
				e1.printStackTrace();
				return;
			}
			logger.info(
				"Sending file "
					+ dir.getPath()
					+ " to "
					+ destrslave.getName());
			new CooperativeSlaveTransfer(dir, destrslave, _numberOfTries)
				.start();
		}
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
