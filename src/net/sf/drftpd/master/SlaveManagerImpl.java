package net.sf.drftpd.master;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.rmi.AlreadyBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.remotefile.JDOMRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.XMLSerialize;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.TransferImpl;

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
	static {
		logger.setLevel(Level.FINEST);
	}
	public static void setRSlavesManager(
		Collection rslaves,
		SlaveManagerImpl manager) {
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			rslave.setManager(manager);
		}
	}
	public static List loadRSlaves() {
		List rslaves = new ArrayList();
		try {
			Document doc = new SAXBuilder().build(new FileReader("slaves.xml"));
			List children = doc.getRootElement().getChildren("slave");
			for (Iterator i = children.iterator(); i.hasNext();) {
				List masks = new ArrayList();
				Element slaveElement = (Element) i.next();
				List maskElements = slaveElement.getChildren("mask");
				for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
					masks.add(((Element) i2.next()).getText());
				}
				RemoteSlave rslave = new RemoteSlave(slaveElement.getChildText("name"), masks);
				rslaves.add(rslave);
			}
		} catch (Exception ex) {
			//logger.log(Level.INFO, "Error reading masks from slaves.xml", ex);
			throw new FatalException(ex);
		}
		return rslaves;
	}

	public static void printRSlaves(Collection rslaves) {
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			System.out.println("rslave: "+rslave);
		}
	}
	public void reloadRSlaves() throws FileNotFoundException, JDOMException, IOException {
			Document doc;
			doc = new SAXBuilder().build(new FileReader("slaves.xml"));

			List slaveElements = doc.getRootElement().getChildren("slave");
			
			for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
				RemoteSlave rslave = (RemoteSlave) iter.next();
				
				for (Iterator iterator = slaveElements.iterator();
					iterator.hasNext();
					) {
					Element slaveElement = (Element) iterator.next();
					if(slaveElement.getChildText("name").equals(rslave.getName())) {
						List masks = new ArrayList();
						List maskElements = slaveElement.getChildren("mask");
						for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
							masks.add(((Element) i2.next()).getText());
						}
						rslave.setMasks(masks);
					}
					if(!iterator.hasNext()) {
						logger.log(Level.INFO, rslave+" no longer in slaves.xml, unmerging");
						root.unmerge(rslave);
					} 
				}
			}
	}

	public static LinkedRemoteFile loadXmlFileDatabase(Collection rslaves) {
		LinkedRemoteFile root;
		/** load XML file database **/
		try {
			Document doc = new SAXBuilder().build(new FileReader("files.xml"));
			root =
				new LinkedRemoteFile(
					new JDOMRemoteFile(doc.getRootElement(), rslaves));
		} catch (FileNotFoundException ex) {
			logger.info("files.xml not found, new file will be created.");
			root = new LinkedRemoteFile();
		} catch (Exception ex) {
			logger.info("Error loading \"files.xml\"");
			ex.printStackTrace();
			root = new LinkedRemoteFile();
		}
		return root;
	}

	protected LinkedRemoteFile root;
	protected Collection rslaves;

	public static Collection rslavesToMasks(Collection rslaves) {
		ArrayList masks = new ArrayList();
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			masks.addAll(rslave2.getMasks());
		}
		return masks;
	}

	public SlaveManagerImpl(Properties cfg, Collection rslaves, RMIServerSocketFactory ssf)
		throws RemoteException, AlreadyBoundException {
		super(
			0,
			(RMIClientSocketFactory) RMISocketFactory.getSocketFactory(),
			ssf);

		// sure would be nice if we could do this in or before the super() call,
		// but we can't reference ''this´´ from there.
		setRSlavesManager(rslaves, this);

		this.rslaves = rslaves;
		this.root = loadXmlFileDatabase(this.rslaves);
		Registry registry = LocateRegistry.createRegistry(1099, RMISocketFactory.getSocketFactory(), ssf);
		// throws RemoteException
		registry.bind("slavemanager", this);
		//		try {
		//			Naming.rebind(cfg.getProperty("slavemanager.url"), this);
		//		} catch (RemoteException e) {
		//			throw new FatalException(e);
		//		} catch (MalformedURLException e) {
		//			throw new FatalException(e);
		//		}
	}

	public void addSlave(
		String slaveName,
		Slave slave,
		LinkedRemoteFile slaveroot)
		throws RemoteException {

		RemoteSlave rslave = null;
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			if (rslave2.getName().equals(slaveName)) {
				rslave = rslave2;
			}
		}
		if (rslave.isAvailable()) {
			throw new IllegalArgumentException(
				rslave.getName() + " is already online");
		}
		if (rslave == null) {
			throw new IllegalArgumentException("rejected");
		}

		rslave.setSlave(slave);

		System.out.println(
			"SlaveManager.addSlave(): "
				+ slaveName +"/"+rslave.getName()
				+ " remoteroot: "
				+ slaveroot);

		long millis = System.currentTimeMillis();
		//todo remerge
		root.unmerge(rslave);
		root.merge(slaveroot, rslave);
		System.out.println(
			"unmerge()+merge() took "
				+ (System.currentTimeMillis() - millis)
				+ "ms");
		try {
			System.out.println(
				"SlaveStatus: " + rslave.getSlave().getSlaveStatus());
			// throws RemoteException
		} catch (NoAvailableSlaveException e) {
			e.printStackTrace();
		}
		saveFilesXML();
	}

	public void saveFilesXML() {
		saveFilesXML(XMLSerialize.serialize(this.getRoot()));
	}

	public static void saveFilesXML(Element root) {
		File filesDotXml = new File("files.xml");
		//		if (filesDotXml.exists()
		//			|| !filesDotXml.renameTo(new File("files.xml.bak"))) {
		//			logger.log(
		//				Level.WARNING,
		//				"Error renaming "
		//					+ filesDotXml.getPath()
		//					+ " to files.xml.bak");
		//		}
		try {
			new XMLOutputter("  ", true).output(
				root,
				new FileWriter(filesDotXml));
		} catch (IOException ex) {
			logger.log(
				Level.WARNING,
				"Error saving to " + filesDotXml.getPath(),
				ex);
		}
	}

	public Collection getSlaves() {
		return rslaves;
	}

	private Random rand = new Random();
	public RemoteSlave getASlave() {
		ArrayList retSlaves = new ArrayList();
		for (Iterator iter = this.rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			if (!rslave.isAvailable())
				continue;
			retSlaves.add(rslave);
		}

		int num = rand.nextInt(retSlaves.size());
		logger.fine(
			"Slave "
				+ num
				+ " selected out of "
				+ retSlaves.size()
				+ " available slaves");
		return (RemoteSlave) retSlaves.get(num);
	}

	public RemoteSlave getASlave(char direction)
		throws NoAvailableSlaveException {
		return getASlave(getSlaves(), direction);
	}
	public static RemoteSlave getASlave(Collection slaves, char direction)
		throws NoAvailableSlaveException {
		RemoteSlave bestslave;
		SlaveStatus beststatus;
		{
			Iterator i = slaves.iterator();

			while (true) {
				if (!i.hasNext())
					throw new NoAvailableSlaveException();
				bestslave = (RemoteSlave) i.next();
				try {
					try {
						beststatus = bestslave.getStatus(); // throws NoAvailableSlaveException
					} catch(NoAvailableSlaveException ex) {
						continue;
					}
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
					bestslave.handleRemoteException(ex);
					continue;
				} catch (NoAvailableSlaveException ex) { // throws NoAvailableSlaveException
					continue;
				}
				float throughput, bestthroughput;
				if (direction == TransferImpl.TRANSFER_RECEIVING_UPLOAD) {
					throughput = status.getThroughputReceiving();
					bestthroughput = beststatus.getThroughputReceiving();
				} else if (direction == TransferImpl.TRANSFER_SENDING_DOWNLOAD) {
					throughput = status.getThroughputSending();
					bestthroughput = beststatus.getThroughputSending();
				} else if (direction == TransferImpl.TRANSFER_THROUGHPUT) {
					throughput = status.getThroughput();
					bestthroughput = beststatus.getThroughput();
				} else {
					throw new IllegalArgumentException("Invalid direction");
				}

				if (throughput < bestthroughput) {
					bestslave = slave;
				}
			}
		}
		return bestslave;
	}

	public SlaveStatus getAllStatus() {
		SlaveStatus ret = new SlaveStatus();
		for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			try {
				ret = ret.append(rslave.getStatus());
			} catch (RemoteException e) {
				rslave.handleRemoteException(e);
			} catch (NoAvailableSlaveException e) {
				//slave is offline, continue
			}
		}
		return ret;
	}
	public LinkedRemoteFile getRoot() {
		return root;
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

	public Collection getAvailableSlaves() throws NoAvailableSlaveException {
		return getAvailableSlaves(getSlaves());
	}
	/**
	 * @deprecated Use RemoteSlave.handleRemoteException instead
	 */
	public boolean handleRemoteException(
		RemoteException ex,
		RemoteSlave rslave) {
		return rslave.handleRemoteException(ex);
	}

	/** ping's all slaves, returns number of slaves removed */
	public int verifySlaves() {
		int removed = 0;
		synchronized (rslaves) {
			for (Iterator i = rslaves.iterator(); i.hasNext();) {
				RemoteSlave slave = (RemoteSlave) i.next();
				try {
					slave.getSlave().ping();
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
