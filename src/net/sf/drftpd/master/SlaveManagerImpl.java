package net.sf.drftpd.master;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.ConnectException;
import java.rmi.ConnectIOException;
import java.rmi.Naming;
import java.rmi.RemoteException;

import java.util.Vector;
import java.util.Iterator;
import java.util.Random;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.TransferImpl;

public class SlaveManagerImpl
	extends UnicastRemoteObject
	implements SlaveManager {

	//	protected Hashtable slaves = new Hashtable();
	protected LinkedRemoteFile root = new LinkedRemoteFile();
	//	protected Vector slaves = new Vector();
	protected Vector slaves = (Vector) root.getSlaves();

	public LinkedRemoteFile getRoot() {
		return root;
	}

	public SlaveManagerImpl(String url) throws RemoteException {
		super();
		try {
			Naming.rebind(url, this);
			
		} catch (java.rmi.ConnectException ex) {
			System.out.println("Naming.rebind(): " + ex.getMessage());
			System.out.println("Is rmiregistry running?");
			System.out.println("This is a critical task, exiting.");
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
	}

	/**
	 * TODO: support hot swapping slavess
	 */
	public void addSlave(RemoteSlave slave, LinkedRemoteFile remoteroot)
		throws RemoteException {
		System.out.println("SlaveManager.addSlave(): " + slave);
		slave.setManager(this);
		slaves.add(slave);
		long millis = System.currentTimeMillis();
		root.merge(remoteroot);
		System.out.println(
			"merge() took " + (System.currentTimeMillis() - millis) + "ms");
		System.out.println("SlaveStatus: " + slave.getSlave().getSlaveStatus());
		//TODO: write XML representation of "LinkedRemoteFile root"
	}

	private Random rand = new Random();
	public RemoteSlave getASlave() {
		int num = rand.nextInt(slaves.size());
		System.out.println(
			"Slave "
				+ num
				+ " selected out of "
				+ slaves.size()
				+ " available slaves");
		return (RemoteSlave) slaves.get(num);
	}

	public RemoteSlave getASlave(char direction)
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
					handleRemoteException(ex, bestslave);
					continue;
				}
			}

			while (i.hasNext()) {
				RemoteSlave slave = (RemoteSlave) i.next();
				SlaveStatus status;

				try {
					status = slave.getStatus();
				} catch (RemoteException ex) {
					handleRemoteException(ex, slave);
					continue;
				}
				float throughput, bestthroughput;
				if (direction == TransferImpl.TRANSFER_RECEIVING) {
					throughput = status.getThroughputReceiving();
					bestthroughput = beststatus.getThroughputReceiving();
				} else if (direction == TransferImpl.TRANSFER_SENDING) {
					throughput = status.getThroughputSending();
					bestthroughput = beststatus.getThroughputSending();
				} else {
					throughput = status.getThroughput();
					bestthroughput = beststatus.getThroughput();
				}

				if (throughput < bestthroughput) {
					bestslave = slave;
				}
			}
		}
		return bestslave;
	}

	public void handleRemoteException(RemoteException ex, RemoteSlave slave) {
		System.out.println(
			"Caught exception when trying to communicate with " + slave);
		if(!isFatalRemoteException(ex)) {
			System.out.println("Non-fatal exception, not removing");
			return;
		}
		System.out.println("This slave should be removed");
		ex.printStackTrace();
		System.out.println("Attempting to unmerge()");
		root.unmerge(slave);
	}

	public boolean isFatalRemoteException(RemoteException ex) {
		return (ex instanceof java.rmi.ConnectException);
	}	
	
	public int verifySlaves() {
		int removed = 0;
		synchronized(slaves) {
			for (Iterator i = slaves.iterator(); i.hasNext();) {
				RemoteSlave slave = (RemoteSlave) i.next();
				try {
					slave.getSlave().ping();
				} catch (RemoteException ex) {
					if(isFatalRemoteException(ex)) i.remove();
					handleRemoteException(ex, slave);
					removed++;
				}
			}
		}
		return removed;
	}
}
