package net.sf.drftpd.master;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.RemoteException;

import java.util.Vector;
import java.util.Iterator;
import java.util.Random;

import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.RemoteSlave;
import net.sf.drftpd.LinkedRemoteFile;

public class SlaveManagerImpl
	extends UnicastRemoteObject
	implements SlaveManager {

//	protected Hashtable slaves = new Hashtable();
	protected Vector slaves = new Vector();
	protected LinkedRemoteFile root = new LinkedRemoteFile();

	public LinkedRemoteFile getRoot() {
		return root;
	}

	public SlaveManagerImpl(String url) throws RemoteException {
		super();
		try {
			Naming.rebind(url, this);
		} catch (java.net.MalformedURLException ex) {
			ex.printStackTrace();
		//} catch(ClassNotFoundException ex) {
		//	System.out.println("ClassNotFoundException: "+ex.getMessage());
		//	System.out.println("rmiregistry was problably started with wrong parameters.");
		}
	}

	public void addSlave(RemoteSlave slave, LinkedRemoteFile remoteroot)
		throws RemoteException {
		System.out.println("SlaveManager.addSlave(): " + slave);
		RemoteSlave rslave;
		slaves.add(slave);
/*
 		rslave = (RemoteSlave) slaves.get(key);
		if (rslave != null) {
			rslave.setSlave(slave);
		} else {
			rslave = new RemoteSlave(slave);
			slaves.put(key, rslave);
		}
*/
		root.merge(remoteroot);
	}

	private Random rand = new Random();
	public RemoteSlave getASlave() {
		int num = rand.nextInt(slaves.size());
		System.out.println("Slave "+num+" selected out of "+slaves.size()+" available slaves");
		return (RemoteSlave)slaves.get(num);
	}
}
