package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
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
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.CorruptFileListException;
import net.sf.drftpd.remotefile.JDOMSerialize;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferImpl;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;
import org.jdom.output.XMLOutputter;

public class SlaveManagerImpl
	extends UnicastRemoteObject
	implements SlaveManager {

	private static Logger logger =
		Logger.getLogger(SlaveManagerImpl.class.getName());

	public static RemoteSlave getASlave(
		Collection slaves,
		char direction,
		FtpConfig config)
		throws NoAvailableSlaveException {
		RemoteSlave bestslave;
		SlaveStatus beststatus;
		{
			Iterator i = slaves.iterator();
			int bestthroughput;

			while (true) {
				if (!i.hasNext())
					throw new NoAvailableSlaveException();
				bestslave = (RemoteSlave) i.next();
				try {
					try {
						beststatus = bestslave.getStatus();
						// throws NoAvailableSlaveException
					} catch (NoAvailableSlaveException ex) {
						continue;
					}
					bestthroughput = getThroughput(direction, beststatus);
					break;
				} catch (RemoteException ex) {
					bestslave.handleRemoteException(ex);
					continue;
				}
			}
			while (i.hasNext()) {
				RemoteSlave slave = (RemoteSlave) i.next();
				SlaveStatus status;

				try {
					status = slave.getStatus();
				} catch (RemoteException ex) {
					slave.handleRemoteException(ex);
					continue;
				} catch (NoAvailableSlaveException ex) { // throws NoAvailableSlaveException
					continue;
				}

				int throughput = getThroughput(direction, status);

				if (beststatus.getDiskSpaceAvailable()
					< config.getFreespaceMin()
					&& beststatus.getDiskSpaceAvailable()
						< status.getDiskSpaceAvailable()) {
					// best slave has less space than "freespace.min" &&
					// best slave has less space available than current slave 
					bestslave = slave;
					bestthroughput = throughput;
					beststatus = status;
					continue;
				}

				if (status.getDiskSpaceAvailable()
					< config.getFreespaceMin()) {
					// current slave has less space available than "freespace.min"
					// above check made sure bestslave has more space than us
					continue;
				}

				if (throughput == bestthroughput) {
					if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
						if (bestslave.getLastUploadReceiving()
							> slave.getLastUploadReceiving()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					} else if (
						direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
						if (bestslave.getLastDownloadSending()
							> slave.getLastDownloadSending()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					} else if (direction == Transfer.TRANSFER_THROUGHPUT) {
						if (bestslave.getLastTransfer()
							> slave.getLastTransfer()) {
							bestslave = slave;
							bestthroughput = throughput;
							beststatus = status;
						}
					}
				}
				if (throughput < bestthroughput) {
					bestslave = slave;
					bestthroughput = throughput;
					beststatus = status;
				}
			}
		}
		if (direction == Transfer.TRANSFER_RECEIVING_UPLOAD) {
			bestslave.setLastUploadReceiving(System.currentTimeMillis());
		} else if (direction == Transfer.TRANSFER_SENDING_DOWNLOAD) {
			bestslave.setLastDownloadSending(System.currentTimeMillis());
		} else {
			bestslave.setLastUploadReceiving(System.currentTimeMillis());
			bestslave.setLastDownloadSending(System.currentTimeMillis());
		}
		if (bestslave == null)
			throw new NoAvailableSlaveException("Object had no slaves!");
		return bestslave;
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

	public static int getThroughput(char direction, SlaveStatus status) {
		if (direction == TransferImpl.TRANSFER_RECEIVING_UPLOAD) {
			return status.getThroughputReceiving();
		} else if (direction == TransferImpl.TRANSFER_SENDING_DOWNLOAD) {
			return status.getThroughputSending();
		} else if (direction == TransferImpl.TRANSFER_THROUGHPUT) {
			return status.getThroughput();
		} else {
			throw new IllegalArgumentException("Invalid direction");
		}
	}
	public static RemoteSlave loadRSlave(Element slaveElement) {
		List masks = new ArrayList();
		List maskElements = slaveElement.getChildren("mask");
		for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
			masks.add(((Element) i2.next()).getText());
		}
		return new RemoteSlave(slaveElement.getChildText("name"), masks);

	}
	public static List loadRSlaves() {
		ArrayList rslaves;
		try {
			Document doc = new SAXBuilder().build(new FileReader("slaves.xml"));
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

	public static LinkedRemoteFile loadFileDatabase(
		List rslaves,
		ConnectionManager cm) throws FileNotFoundException, IOException, CorruptFileListException {
		/** load XML file database **/
		/** load MLST file database **/
		return loadMLSTFileDatabase(rslaves, cm);
	}

	public static LinkedRemoteFile loadJDOMFileDatabase(List rslaves, ConnectionManager cm) throws FileNotFoundException {
		return JDOMSerialize.unserialize(cm, new FileReader("files.xml"), rslaves);
	}
	
	public static LinkedRemoteFile loadMLSTFileDatabase(
	List rslaves, ConnectionManager cm) throws IOException {
		return MLSTSerialize.unserialize(cm.getConfig(), new BufferedReader(new FileReader("files.mlst")), rslaves);
	}
	
	public static void printRSlaves(Collection rslaves) {
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			System.out.println("rslave: " + rslave);
		}
	}

	public static Collection rslavesToMasks(Collection rslaves) {
		ArrayList masks = new ArrayList();
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			masks.addAll(rslave2.getMasks());
		}
		return masks;
	}

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
	private ConnectionManager cm;

	protected LinkedRemoteFile root;
	protected List rslaves;

	public SlaveManagerImpl(
		Properties cfg,
		List rslaves,
		RMIServerSocketFactory ssf,
		ConnectionManager cm)
		throws RemoteException {
		super(0, RMISocketFactory.getSocketFactory(), ssf);

		this.cm = cm;

		// sure would be nice if we could do this in or before the super() call,
		// but we can't reference ''this´´ from there.
		setRSlavesManager(rslaves, this);

		this.rslaves = rslaves;
		try {
			this.root = loadFileDatabase(this.rslaves, cm);
		} catch(FileNotFoundException e) {
			logger.info("files.mlst not found, creating a new filesystem", e);
			root = new LinkedRemoteFile(cm.getConfig());
			saveFilelist();
		} catch (IOException e) {
			throw new FatalException(e);
		}
		Registry registry =
			LocateRegistry.createRegistry(
				Integer.parseInt(cfg.getProperty("master.bindport", "1099")),
				RMISocketFactory.getSocketFactory(),
				ssf);
		// throws RemoteException
		try {
			registry.bind(cfg.getProperty("master.bindname"), this);
		} catch (Exception t) {
			throw new FatalException(t);
		}
	}

	public void addSlave(
		String slaveName,
		Slave slave,
		LinkedRemoteFile slaveroot)
		throws RemoteException {

		slave.ping();

		RemoteSlave rslave = null;
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			if (rslave2.getName().equals(slaveName)) {
				rslave = rslave2;
				break;
			}
		}
		if (rslave == null) throw new IllegalArgumentException("Slave not found in slaves.xml");

		if (rslave.isAvailablePing()) {
			throw new IllegalArgumentException(
				rslave.getName() + " is already online");
		}

		try {
			rslave.setSlave(
				slave,
				InetAddress.getByName(RemoteServer.getClientHost()));
		} catch (Throwable e1) {
			throw new FatalException(e1);
		}

		try {
			root.remerge(slaveroot, rslave);
		} catch (RuntimeException t) {
			logger.log(Level.FATAL, "", t);
			rslave.setSlave(null, null);
			throw t;
		}

		System.out.println(
			"SlaveManager.addSlave(): "
				+ rslave.getName()
				+ " remoteroot: "
				+ slaveroot);

		try {
			System.out.println(
				"SlaveStatus: " + rslave.getSlave().getSlaveStatus());
			// throws RemoteException
		} catch (NoAvailableSlaveException e) {
			logger.log(Level.FATAL, "", e);
		} catch (RemoteException e) {
			logger.log(
				Level.FATAL,
				"RemoteException from getSlaveStatus()",
				e);
		}
		getConnectionManager().dispatchFtpEvent(
			new SlaveEvent("ADDSLAVE", rslave));
	}

	SlaveStatus allStatus = null;
	long allStatusTime = 0;
	/**
	 * Cached for 1 second.
	 */
	public SlaveStatus getAllStatus() {
		if(allStatusTime >= System.currentTimeMillis()-1000) return allStatus;
		allStatus = new SlaveStatus();
		allStatusTime = System.currentTimeMillis();
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			try {
				allStatus = allStatus.append(rslave.getStatus());
			} catch (RemoteException e) {
				rslave.handleRemoteException(e);
			} catch (NoAvailableSlaveException e) {
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

	public RemoteSlave getASlave(char direction)
		throws NoAvailableSlaveException {
		return getASlave(
			getSlaves(),
			direction,
			getConnectionManager().getConfig());
	}

	public Collection getAvailableSlaves() throws NoAvailableSlaveException {
		return getAvailableSlaves(getSlaves());
	}
	public ConnectionManager getConnectionManager() {
		return cm;
	}
	public LinkedRemoteFile getRoot() {
		return root;
	}

	public RemoteSlave getSlave(String s) throws ObjectNotFoundException {
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (rslave.getName().equals(s))
				return rslave;
		}
		throw new ObjectNotFoundException(s + ": No such slave");
	}

	public Collection getSlaves() {
		return rslaves;
	}
	/**
	 * @deprecated Use RemoteSlave.handleRemoteException instead
	 */
	public boolean handleRemoteException(
		RemoteException ex,
		RemoteSlave rslave) {
		return rslave.handleRemoteException(ex);
	}

	public boolean hasAvailableSlaves() {
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (rslave.isAvailable())
				return true;
		}
		return false;
	}

	public void reloadRSlaves() throws FileNotFoundException, IOException {
		Document doc;
		try {
			doc = new SAXBuilder().build(new FileReader("slaves.xml"));
		} catch (JDOMException e) {
			throw (IOException) new IOException().initCause(e);
		}

		List slaveElements = doc.getRootElement().getChildren("slave");

		// first, unmerge non-existing slaves
		synchronized (this.rslaves) {
			nextslave : for (
				Iterator iter = this.rslaves.iterator(); iter.hasNext();) {
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
				root.unmerge(rslave);
				//rslaves.remove(rslave);
				iter.remove();
			}
		}

		nextelement : for (
			Iterator iterator = slaveElements.iterator();
				iterator.hasNext();
				) {
			Element slaveElement = (Element) iterator.next();

			for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
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
			this.rslaves.add(rslave);
			logger.log(Level.INFO, "Added " + rslave.getName() + " to slaves");
		}
		Collections.sort(this.rslaves);
	}

	public void saveFilelist() {
		//saveFilesXML(JDOMSerialize.serialize(this.getRoot()));
		
		File bak = new File("files.mlst.bak");
		bak.delete();
		new File("files.mlst").renameTo(bak);
		try {
			MLSTSerialize.serialize(getRoot(), new PrintStream(new FileOutputStream("files.mlst")));
		} catch (FileNotFoundException e) {
			logger.warn("Error saving files.mlst", e);
		}
	}

	/** ping's all slaves, returns number of slaves removed */
	public int verifySlaves() {
		int removed = 0;
		synchronized (rslaves) {
			for (Iterator i = rslaves.iterator(); i.hasNext();) {
				RemoteSlave slave = (RemoteSlave) i.next();
				try {
					slave.ping();
				} catch (RemoteException ex) {
					if (slave.handleRemoteException(ex))
						removed++;
				} catch (NoAvailableSlaveException ex) {
					continue;
				}
			}
		}
		return removed;
	}
}
