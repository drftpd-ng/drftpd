/*
 * Created on Dec 3, 2003
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.IOException;
import java.rmi.RemoteException;
import java.sql.Time;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Logger;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.ArchiveHandler;
import net.sf.drftpd.util.CooperativeSlaveTransfer;

/**
 * @author zubov
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Archive implements FtpListener {
	private boolean _archiving;
	private ConnectionManager _cm;
	private long _cycleTime;
	private long lastchecked;

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
		if (!(event instanceof TransferEvent))
			return;
		//System.out.println("We are now about to try and archive");
		System.out.println("System.currentTimeMillis() - lastchecked = " + (System.currentTimeMillis() - lastchecked));
		System.out.println("System.currentTimeMillis() = " + System.currentTimeMillis());
		System.out.println("lastchecked = " + lastchecked);
		System.out.println("_cycleTime = " + _cycleTime);
		if (System.currentTimeMillis() - lastchecked > _cycleTime) {
			lastchecked = System.currentTimeMillis();
			new ArchiveHandler((DirectoryFtpEvent) event,this).start();
			System.out.println("Launched the ArchiveHandler");
		}
		System.out.println("at the end of archive");
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
			props.load(new FileInputStream("archive.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		_cycleTime = Long.parseLong(props.getProperty("cycleTime"));
		lastchecked = System.currentTimeMillis();
		_archiving = false;
	}
}
