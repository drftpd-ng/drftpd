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
package net.sf.drftpd.master;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.net.InetAddress;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.util.SafeFileWriter;

import net.sf.drftpd.tcpslave.SocketSlaveImpl;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

/**
 * @author mog
 * @version $Id: SlaveManagerImpl.java,v 1.81 2004/04/27 22:06:28 zombiewoof64 Exp $
 */
public class SlaveManagerImpl
	extends UnicastRemoteObject
	implements SlaveManager {
	private static final Logger logger =
		Logger.getLogger(SlaveManagerImpl.class.getName());

	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public static RemoteSlave getASlave(
		Collection slaves,
		char direction,
		FtpConfig config,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		return config.getSlaveManager().getSlaveSelectionManager().getASlave(
			slaves,
			direction,
			conn,
			file);
	}

	public static Collection getAvailableSlaves(Collection slaves)
		throws NoAvailableSlaveException {
		ArrayList availableSlaves = new ArrayList();
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!rslave.isAvailable())
				continue;
			availableSlaves.add(rslave);
		}
		if (availableSlaves.isEmpty()) {
			throw new NoAvailableSlaveException("No slaves online");
		}
		return availableSlaves;
	}

	public static RemoteSlave loadRSlave(Element slaveElement) {
		List masks = new ArrayList();
		List maskElements = slaveElement.getChildren("mask");
		for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
			masks.add(((Element) i2.next()).getText());
		}
		return new RemoteSlave(
			slaveElement.getChildText("name").toString(),
			masks, slaveElement);
	}

	public static List loadRSlaves() {
		ArrayList rslaves;
		try {
			Document doc =
				new SAXBuilder().build(new FileReader("conf/slaves.xml"));
			List children = doc.getRootElement().getChildren("slave");
			rslaves = new ArrayList(children.size());
			for (Iterator i = children.iterator(); i.hasNext();) {
				//slavemanager is set in the slavemanager constructor
				rslaves.add(loadRSlave((Element) i.next()));
			}
			rslaves.trimToSize();
		} catch (Exception ex) {
			//logger.log(Level.INFO, "Error reading masks from slaves.xml", ex);
			throw new FatalException(ex);
		}
		Collections.sort(rslaves);
		return rslaves;
	}

	public static Collection rslavesToMasks(Collection rslaves) {
		ArrayList masks = new ArrayList();
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			masks.addAll(rslave2.getMasks());
		}
		return masks;
	}

	/**
	 * @deprecated
	 */
	public static void saveFilesXML(Element root) {
		File filesDotXml = new File("files.xml");
		File filesxmlbak = new File("files.xml.bak");
		filesxmlbak.delete();
		filesDotXml.renameTo(filesxmlbak);
		try {
			FileWriter out = new FileWriter(filesDotXml);
			new XMLOutputter("  ", true).output(root, out);
			out.flush();
		} catch (IOException ex) {
			logger.log(
				Level.WARN,
				"Error saving to " + filesDotXml.getPath(),
				ex);
		}
	}
	public static void setRSlavesManager(
		Collection rslaves,
		SlaveManagerImpl manager) {
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			rslave.setManager(manager);
		}
	}

	private ConnectionManager _cm;

	protected List _rslaves;

	private SlaveSelectionManagerInterface _slaveSelectionManager;
	protected SlaveManagerImpl() throws RemoteException {
	}
	public SlaveManagerImpl(
		Properties cfg,
		List rslaves,
		RMIServerSocketFactory ssf,
		ConnectionManager cm)
		throws RemoteException {
		super(0, RMISocketFactory.getSocketFactory(), ssf);

		_cm = cm;

		// sure would be nice if we could do this in or before the super() call,
		// but we can't reference ''this?? from there.
		setRSlavesManager(rslaves, this);

		_rslaves = rslaves;

		Registry registry =
			LocateRegistry.createRegistry(
				Integer.parseInt(cfg.getProperty("master.bindport", "1099")),
				RMISocketFactory.getSocketFactory(),
				ssf);
		// throws RemoteException
		try {
			registry.bind(
				cfg.getProperty("master.bindname", "slavemanager"),
				this);
		} catch (Exception t) {
			throw new FatalException(t);
		}
		try {
			Constructor c =
				Class
					.forName(
						cfg.getProperty(
							"slaveselection",
							"org.drftpd.slaveselection.def.SlaveSelectionManager"))
					.getConstructor(new Class[] { SlaveManagerImpl.class });
			_slaveSelectionManager =
				(SlaveSelectionManagerInterface) c.newInstance(
					new Object[] { this });
		} catch (Exception e) {
			if (e instanceof RuntimeException)
				throw (RuntimeException) e;
			throw new FatalException(e);
		}
		logger.debug("starting slavestatus updater thread");
		//new Thread(this, "SlaveStatusUpdater").start();
		new SlaveStatusUpdater().start();
	}

	protected void addShutdownHook() {
		//add shutdown hook last
		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				logger.info("Running shutdown hook");
				saveFilelist();
				try {
					getConnectionManager().getUserManager().saveAll();
				} catch (UserFileException e) {
					logger.warn("", e);
				}
			}
		});
	}

	public void addSlave(String slaveName, Slave slave, SlaveStatus status)
		throws RemoteException {

		slave.ping();

		RemoteSlave rslave = null;
		for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			if (rslave2.getName().equals(slaveName)) {
				rslave = rslave2;
				break;
			}
		}
		if (rslave == null)
			throw new IllegalArgumentException("Slave not found in slaves.xml");

		if (rslave.isAvailablePing()) {
			throw new IllegalArgumentException(
				rslave.getName() + " is already online");
		}

		try {
                    InetAddress addr = null;
                    if (slave instanceof net.sf.drftpd.slave.SlaveImpl) {
                        addr = InetAddress.getByName(RemoteServer.getClientHost());
                    }
                    if (slave instanceof net.sf.drftpd.tcpslave.SocketSlaveImpl) {
                        addr = ((net.sf.drftpd.tcpslave.SocketSlaveImpl)slave).getAddress();
                    }
                    if (addr == null) {
                        throw new IllegalArgumentException(rslave.getName() + " has no slave address");
                    }
                    rslave.setSlave(slave,addr,slave.getSlaveStatus());
		} catch (Throwable e1) {
			throw new FatalException(e1);
		}		logger.debug("About to remerge(), slave is " + rslave);
		try {
			remerge(rslave);
		} catch (IOException e) {
			rslave.setOffline("IOException during remerge()");
			return;
		} catch (SlaveUnavailableException e) {
			rslave.setOffline("Slave Unavailable during remerge()");
			return;
		}

		logger.info("Slave added: '" + rslave.getName()+"'");
		logger.info("SlaveStatus: " + status);
		
		getConnectionManager().dispatchFtpEvent(
			new SlaveEvent("ADDSLAVE", rslave));
	}

	public void delSlave(String slaveName, String reason)
		throws RemoteException {

		RemoteSlave rslave = null;
		for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			if (rslave2.getName().equals(slaveName)) {
				rslave = rslave2;
				break;
			}
		}
		if (rslave == null)
			throw new IllegalArgumentException("Slave not found in slaves.xml (" + slaveName + ")");
                
                rslave.setOffline(reason);
        }


	public RemoteSlave findLargestFreeSlave() {
		Collection slaveList =
			getConnectionManager().getSlaveManager().getSlaveList();
		long bigSize = 0;
		RemoteSlave bigSlave = null;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			long size = 0;
			try {
				size = rslave.getStatus().getDiskSpaceAvailable();
			} catch (SlaveUnavailableException e) {
				continue;
			}
			if (size > bigSize) {
				bigSize = size;
				bigSlave = rslave;
			}
		}
		return bigSlave;
	}

	public RemoteSlave findSmallestFreeSlave() {
		Collection slaveList =
			getConnectionManager().getSlaveManager().getSlaveList();
		long smallSize = Integer.MAX_VALUE;
		RemoteSlave smallSlave = null;
		for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			long size = Integer.MAX_VALUE;
			try {
				size = rslave.getStatus().getDiskSpaceAvailable();
			} catch (SlaveUnavailableException e) {
				continue;
			}
			if (size < smallSize) {
				smallSize = size;
				smallSlave = rslave;
			}
		}
		return smallSlave;
	}

	/**
	 * Not cached at all since RemoteSlave objects cache their SlaveStatus
	 */
	public SlaveStatus getAllStatus() {
		SlaveStatus allStatus = new SlaveStatus();
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			try {
				allStatus = allStatus.append(rslave.getStatus());
			} catch (SlaveUnavailableException e) {
				//slave is offline, continue
			}
		}
		return allStatus;
	}

	//	private Random rand = new Random();
	//	public RemoteSlave getASlave() {
	//		ArrayList retSlaves = new ArrayList();
	//		for (Iterator iter = this.rslaves.iterator(); iter.hasNext();) {
	//			RemoteSlave rslave = (RemoteSlave) iter.next();
	//			if (!rslave.isAvailable())
	//				continue;
	//			retSlaves.add(rslave);
	//		}
	//
	//		int num = rand.nextInt(retSlaves.size());
	//		logger.fine(
	//			"Slave "
	//				+ num
	//				+ " selected out of "
	//				+ retSlaves.size()
	//				+ " available slaves");
	//		return (RemoteSlave) retSlaves.get(num);
	//	}

	public RemoteSlave getASlave(
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		return getASlave(
			getSlaves(),
			direction,
			getConnectionManager().getConfig(),
			conn,
			file);
	}

	public Collection getAvailableSlaves() throws NoAvailableSlaveException {
		return getAvailableSlaves(getSlaves());
	}
	public ConnectionManager getConnectionManager() {
		return _cm;
	}
	public RemoteSlave getSlave(String s) throws ObjectNotFoundException {
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (rslave.getName().equals(s))
				return rslave;
		}
		throw new ObjectNotFoundException(s + ": No such slave");
	}
	public List getSlaveList() {
		return _rslaves;
	}

	public Collection getSlaves() {
		return _rslaves;
	}

	public SlaveSelectionManagerInterface getSlaveSelectionManager() {
		return _slaveSelectionManager;
	}
	/**
	 * @deprecated Use RemoteSlave.handleRemoteException instead
	 */
	public void handleRemoteException(RemoteException ex, RemoteSlave rslave) {
		rslave.handleRemoteException(ex);
	}

	/**
	 * Returns true if one or more slaves are online, false otherwise.
	 * @return true if one or more slaves are online, false otherwise.
	 */
	public boolean hasAvailableSlaves() {
		for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (rslave.isAvailable())
				return true;
		}
		return false;
	}

	public void reload() throws FileNotFoundException, IOException {
		_slaveSelectionManager.reload();
		reloadRSlaves();
	}
	public void reloadRSlaves() throws FileNotFoundException, IOException {
		Document doc;
		try {
			doc = new SAXBuilder().build(new FileReader("conf/slaves.xml"));
		} catch (JDOMException e) {
			throw (IOException) new IOException().initCause(e);
		}

		List slaveElements = doc.getRootElement().getChildren("slave");

		// first, unmerge non-existing slaves
		synchronized (_rslaves) {
			nextslave : for (
				Iterator iter = _rslaves.iterator(); iter.hasNext();) {
				RemoteSlave rslave = (RemoteSlave) iter.next();

				for (Iterator iterator = slaveElements.iterator();
					iterator.hasNext();
					) {
					Element slaveElement = (Element) iterator.next();
					if (rslave
						.getName()
						.equals(slaveElement.getChildText("name"))) {
						logger.log(
							Level.DEBUG,
							rslave.getName() + " still in slaves.xml");
						continue nextslave;
					}
				}
				logger.log(
					Level.WARN,
					rslave.getName() + " no longer in slaves.xml, unmerging");
				rslave.setOffline("Slave removed from slaves.xml");
				getConnectionManager().getRoot().unmergeDir(rslave);
				//rslaves.remove(rslave);
				iter.remove();
			}
		}

		nextelement : for (
			Iterator iterator = slaveElements.iterator();
				iterator.hasNext();
				) {
			Element slaveElement = (Element) iterator.next();

			for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
				RemoteSlave rslave = (RemoteSlave) iter.next();

				if (slaveElement
					.getChildText("name")
					.equals(rslave.getName())) {
					List masks = new ArrayList();
					List maskElements = slaveElement.getChildren("mask");
					for (Iterator i2 = maskElements.iterator();
						i2.hasNext();
						) {
						masks.add(((Element) i2.next()).getText());
					}
					rslave.setMasks(masks);
					continue nextelement;
				}
			} // rslaves.iterator()
			RemoteSlave rslave = loadRSlave(slaveElement);
			rslave.setManager(this);
			_rslaves.add(rslave);
			logger.log(Level.INFO, "Added " + rslave.getName() + " to slaves");
		}
		Collections.sort(_rslaves);
	}

	public void remerge(RemoteSlave rslave)
		throws IOException, SlaveUnavailableException {
		LinkedRemoteFile slaveroot;
		slaveroot = rslave.getSlaveRoot();
		try {
			getConnectionManager().getRoot().remerge(slaveroot, rslave);
		} catch (RuntimeException t) {
			logger.log(Level.FATAL, "", t);
			rslave.setOffline(t.getMessage());
			throw t;
		}
	}

	public class SlaveStatusUpdater extends Thread {
		public SlaveStatusUpdater() {
			super("SlaveStatusUpdater");
		}
		public void run() {
			logger.debug("started slavestatus updater thread");
			long low = Integer.MAX_VALUE;
			long high = 0;
			while (true) {
			try {
				for (Iterator iter = getAvailableSlaves().iterator();
					iter.hasNext();
					) {
					RemoteSlave slave = (RemoteSlave) iter.next();
					try {
						long time = System.currentTimeMillis();
						slave.updateStatus();
						long difference = System.currentTimeMillis() - time;
						if (difference < low) {
							low = difference;
							logger.debug(
								low
									+ " low milliseconds were used to run updateStatus on "
									+ slave.getName());
						}
						if (difference > high) {
							high = difference;
							logger.debug(
								high
									+ " high milliseconds were used to run updateStatus on "
									+ slave.getName());
						}
					} catch (SlaveUnavailableException e1) {
						continue;
					}
				}
			} catch (NoAvailableSlaveException e) {
			}
			try {
				Thread.sleep(_cm.getConfig().getSlaveStatusUpdateTime());
			} catch (InterruptedException e1) {
			}
		}
	}
	}

	public void saveFilelist() {
		try {
			SafeFileWriter out = new SafeFileWriter("files.mlst");
			try {
				MLSTSerialize.serialize(getConnectionManager().getRoot(), out);
			} finally {
				out.close();
			}
		} catch (IOException e) {
			logger.warn("Error saving files.mlst", e);
		}
	}

	/** ping's all slaves, returns number of slaves removed */
	public int verifySlaves() {
		int removed = 0;
		synchronized (_rslaves) {
			for (Iterator i = _rslaves.iterator(); i.hasNext();) {
				RemoteSlave slave = (RemoteSlave) i.next();
				if (!slave.isAvailablePing())
					removed++;
			}
		}
		return removed;
	}
}
