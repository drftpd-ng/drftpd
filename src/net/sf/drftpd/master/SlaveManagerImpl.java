package net.sf.drftpd.master;

import java.io.File;
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
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.permission.GlobRMISocketFactory;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.TransferImpl;

import org.jdom.Element;
import org.jdom.output.XMLOutputter;

public class SlaveManagerImpl
	extends UnicastRemoteObject
	implements SlaveManager {

	private static Logger logger =
		Logger.getLogger(SlaveManagerImpl.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	protected LinkedRemoteFile root;
	protected Collection slaves;

	/**
	 * @deprecated
	 */
	public SlaveManagerImpl(String url, List rslaves)
		throws RemoteException, AlreadyBoundException {
		this(url, new LinkedRemoteFile(), rslaves);
	}

	private static Collection rslavesToMasks(Collection rslaves) {
		ArrayList masks = new ArrayList();
		for (Iterator iter = rslaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			masks.addAll(rslave2.getMasks());
		}
		return masks;
	}

	public SlaveManagerImpl(
		String url,
		LinkedRemoteFile root,
		Collection rslaves)
		throws RemoteException, AlreadyBoundException {
		super(
			0,
			(RMIClientSocketFactory) RMISocketFactory.getSocketFactory(),
			(RMIServerSocketFactory) new GlobRMISocketFactory(
				rslavesToMasks(rslaves)));
		//		 (RMIServerSocketFactory) RMISocketFactory.getSocketFactory());

		//		super();
		//		super(0, new GlobRMISocketFactory(masks), new GlobRMISocketFactory(masks));
		this.root = root;
		//slaves = new ArrayList(root.getSlaves());
		this.slaves = rslaves;
		/*
				RMIClientSocketFactory csf = RMISocketFactory.getSocketFactory();
				RMIServerSocketFactory ssf = new GlobRMISocketFactory(masks);
		*/
		//RMIServerSocketFactory ssf = RMISocketFactory.getSocketFactory();

		//SlaveManager stub = this;
		/*
				SlaveManager stub =
					(SlaveManager) exportObject(
						this,
						0,
						csf,
						ssf);
		*/
		//SlaveManager stub = (SlaveManager)
		//		UnicastRemoteObject.exportObject((Remote)this, 6666);

		Registry registry = LocateRegistry.createRegistry(1099);
		registry.bind("slavemanager", this);

		/*		} catch (java.rmi.ConnectException ex) {
					ex.printStackTrace();
					System.exit(-1);
					return;
				//java.rmi.ConnectIOException: Exception creating connection to: 213.114.146.61; nested exception is: 
				//	java.net.SocketException: errno: 101, error: Network is unreachable for fd: 7
				} catch(java.rmi.ConnectIOException ex) {
					System.out.println(ex.getMessage());
					System.out.println("Error binding slave, check the slavemanager.url property");
					System.exit(-1);
					return;			
				} catch (java.net.MalformedURLException ex) {
					ex.printStackTrace();
					System.out.println("The property slavemanager.url has an invalid URL.");
					System.exit(-1);
					return;
					//} catch(ClassNotFoundException ex) {
					//	System.out.println("ClassNotFoundException: "+ex.getMessage());
					//	System.out.println("rmiregistry was problably started with wrong parameters.");
				}
				*/
	}

	public void addSlave(RemoteSlave rslave, LinkedRemoteFile remoteroot)
		throws RemoteException {

		rslave.setManager(this);
		RemoteSlave realRSlave = null;
		for (Iterator iter = slaves.iterator(); iter.hasNext();) {
			RemoteSlave rslave2 = (RemoteSlave) iter.next();
			if (rslave2.equals(rslave)) {
				realRSlave = rslave2;
			}
			logger.log(Level.FINE, rslave2+" not equals() "+rslave);
		}
		logger.log(Level.SEVERE, "Rejected slave "+rslave+", not a valid slave");
		if (realRSlave == null) {
			throw new IllegalArgumentException("rejected");
		}

		realRSlave.setManager(this);
		try {
			realRSlave.setSlave(rslave.getSlave());
		} catch (NoAvailableSlaveException e1) {
			logger.log(
				Level.SEVERE,
				"NoAvailableSlaveException in addSlave()",
				e1);
			throw new IllegalArgumentException("rejected");
		}

		System.out.println(
			"SlaveManager.addSlave(): "
				+ rslave
				+ " remoteroot: "
				+ remoteroot);

		//this.slaves.add(rslave);
		long millis = System.currentTimeMillis();
		//remerge
		root.unmerge(realRSlave);
		root.merge(remoteroot, realRSlave);
		System.out.println(
			"unmerge()+merge() took " + (System.currentTimeMillis() - millis) + "ms");
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
		saveFilesXML(this.getRoot().toXML());
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
		return slaves;
	}

	private Random rand = new Random();
	public RemoteSlave getASlave() {
		ArrayList retSlaves = new ArrayList();
		for (Iterator iter = this.slaves.iterator(); iter.hasNext();) {
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

	public RemoteSlave getASlave(char direction) throws NoAvailableSlaveException {
		return getASlave(this.getSlaves(), direction);
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
					beststatus = bestslave.getStatus();
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
				}
				float throughput, bestthroughput;
				if (direction == TransferImpl.TRANSFER_RECEIVING) {
					throughput = status.getThroughputReceiving();
					bestthroughput = beststatus.getThroughputReceiving();
				} else if (direction == TransferImpl.TRANSFER_SENDING) {
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
		synchronized (slaves) {
			for (Iterator i = slaves.iterator(); i.hasNext();) {
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
