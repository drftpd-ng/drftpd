package net.sf.drftpd.master;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.ConnectException;
import java.rmi.Naming;
import java.rmi.RemoteException;

import java.util.Vector;
import java.util.Iterator;
import java.util.Random;

import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.LinkedRemoteFile;

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
		} catch (ConnectException ex) {
			System.out.println("Naming.rebind(): " + ex.getMessage());
			System.out.println("Is rmiregistry running?");
			System.out.println("This is a critical task, exiting.");
			System.exit(-1);
		} catch (java.net.MalformedURLException ex) {
			ex.printStackTrace();
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

	public RemoteSlave getASlave(char direction) throws NoAvailableSlaveException {
		RemoteSlave bestslave;
		SlaveStatus beststatus;
		{
			Iterator i = slaves.iterator();
			
			while(true) {
				if(!i.hasNext()) throw new NoAvailableSlaveException();
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
				if (direction == Transfer.TRANSFER_RECEIVING) {
					throughput = status.getThroughputReceiving();
					bestthroughput = beststatus.getThroughputReceiving();
				} else if(direction == Transfer.TRANSFER_SENDING) {
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
		System.out.println("This slave should be removed");
		ex.printStackTrace();
	}
}
