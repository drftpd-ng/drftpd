package net.sf.drftpd.slave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import java.net.ConnectException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.CheckedOutputStream;

import com.jconfig.DiskFile;
import com.jconfig.DiskFileException;
import com.jconfig.DiskObject;
import com.jconfig.DiskVolume;
import com.jconfig.FileRegistry;
import com.sun.corba.se.internal.iiop.ListenerThread;

import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.master.SlaveManager;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class SlaveImpl extends UnicastRemoteObject implements Slave {
	private Vector transfers = new Vector();
	//Properties cfg;

	SlaveManager manager;
	private String root;

	public SlaveImpl(Properties cfg) throws RemoteException {
		super();
		root = cfg.getProperty("slave.root");
		//		this.cfg = cfg;
	}

	public static void main(String args[]) {
		RemoteSlave slave;
		Properties cfg = new Properties();
		SlaveManager manager;

		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (IOException ex) {
			ex.printStackTrace();
			System.err.println("Could not open drftpd.conf, exiting.");
			System.exit(-1);
			return;
		}

		try {
			manager =
				(SlaveManager) Naming.lookup(
					cfg.getProperty("slavemanager.url"));
		} catch (Exception ex) {
			System.out.println(ex.getMessage());
			System.out.println("Could not lookup slavemanager.url, this is a critical task, exiting.");
			System.exit(-1);
			return;
		}

		try {
			slave = new RemoteSlave(new SlaveImpl(cfg));
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(-1);
			return;
			//the compiler doesn't know that execution stops at System.exit()
		}

		LinkedRemoteFile root =
			getDefaultRoot(cfg.getProperty("slave.root"), slave);

		try {
			System.out.println("manager.addSlave() root: " + root);
			manager.addSlave(slave, root);
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(-1);
			return;
		}
	}

	public static void checkInited() {
		if (!FileRegistry.isInited()) {
			System.out.println(
				"Initalizing using " + System.getProperty("user.dir"));

			/* START jconfig */
			FileRegistry.initialize(
				new File(System.getProperty("user.dir")),
				0);
			if (!FileRegistry.isInited()) {
				System.out.println("Please check your configuration.");
				return;
			}
			/* END jconfig */
		}
	}
	public static LinkedRemoteFile getDefaultRoot(
		String root,
		RemoteSlave slave) {

		File rootfile = new File(root);
		if (!rootfile.isDirectory()) {
			throw new RuntimeException(
				"slave.root = " + rootfile.getPath() + " is not a directory!");
			//System.exit(-1);
			//return null;
		}
		return new LinkedRemoteFile(slave, rootfile);
	}

	public SlaveStatus getSlaveStatus() {
		checkInited();
		float throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;

		for (Iterator i = transfers.iterator(); i.hasNext();) {
			TransferImpl transfer = (TransferImpl) i.next();
			if (transfer.getDirection() == transfer.TRANSFER_RECEIVING) {
				throughputDown += transfer.getTransferSpeed();
				transfersDown += 1;
			} else {
				throughputUp += transfer.getTransferSpeed();
				transfersUp += 1;
			}
			//			throughput += transfer.getTransferSpeed();
		}
		try {
			DiskObject dobject =
				FileRegistry.createDiskObject(new File(root), 0);
			if (!(dobject instanceof DiskFile)) {
				throw new RuntimeException("slave.root is not a DiskFile");
			}
			DiskVolume dvolume = ((DiskFile) dobject).getVolume();
			return new SlaveStatus(
				dvolume.getFreeSpace(),
				throughputUp,
				transfersUp,
				throughputDown,
				transfersDown);
		} catch (Exception ex) {
			ex.printStackTrace();
			throw new RuntimeException(ex.toString());
		}
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#mkdir()
	 */
	public void mkdir(String path) throws RemoteException, PermissionDeniedException {
		if(!new File(root+path).mkdir()) {
			throw new PermissionDeniedException("mkdir(): Permission denied");
		}
	}
	
	/**
	 * @see net.sf.drftpd.slave.Slave#ping()
	 */
	public void ping() throws RemoteException {
	}
	
	/////////////////// TRANSFER METHODS ///////////////////////////
	// SEND
	
	/**
	 * Starts sending 'remotefile' starting at 'offset' bytes to a outputstream from the already connected socket 'sock'.
	 */
	private Transfer doSend(RemoteFile remotefile, char mode, long offset, Connection conn) throws IOException {
		File file = new File(root + remotefile.getPath());
		if (!file.exists())
			throw new FileNotFoundException(
				"File " + file + " not found, Remotefile: " + remotefile);
		
		FileInputStream in = new FileInputStream(file);
		in.skip(offset);
		
		return new TransferImpl(in, conn, mode);
	}
	
	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectSend(REmoteFile, long, InetADdress, int)
	 */
	public Transfer doConnectSend(
		RemoteFile rfile,
		char mode,
		long offset,
		InetAddress addr,
		int port)
		throws IOException {
		System.out.println("doConnectSend() called with mode "+mode);
		return doSend(rfile, mode, offset,  new ActiveConnection(addr, port));
	}
	
	/**
	 * @see net.sf.drftpd.slave.Slave#doListenSend(RemoteFile, long, int)
	 */
	public Transfer doListenSend(RemoteFile remotefile, char mode, long offset)
		throws RemoteException, IOException {
		
//		ServerSocket server = new ServerSocket(0, 1);
		//server.setSoTimeout(xxx);
//		Socket sock = server.accept();
//		server.close();
		return doSend(remotefile, mode, offset, new PassiveConnection());
	}

	//RECEIVE
	/**
	 * Generic receive method.
	 */
	private Transfer doReceive(RemoteFile remotefile, long offset, Connection conn) throws IOException {
		
		File file = new File(root + remotefile.getPath());
		FileOutputStream out;
		out = new FileOutputStream(file);

//		Socket sock = conn.connect();
//		InputStream in = sock.getInputStream();
		/*
				CRC32 checksum = new CRC32();
				CheckedOutputStream cos = new CheckedOutputStream(os, checksum);
		*/

		return new TransferImpl(conn, out);
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(RemoteFile, long, InetAddress, int)
	 */
	public Transfer doConnectReceive(
		RemoteFile remotefile,
		long offset,
		InetAddress addr,
		int port)
		throws IOException {

		//Socket sock = new Socket(addr, port);
		return doReceive(remotefile, offset, new ActiveConnection(addr, port));
	}


	/**
	 * @see net.sf.drftpd.slave.Slave#doListenReceive(RemoteFile, long, int)
	 */
	public Transfer doListenReceive(RemoteFile remotefile, long offset)
		throws IOException {
//		ServerSocket server = new ServerSocket(0, 1);
		//server.setSoTimeout(xxx);
//		Socket sock = server.accept();
//		server.close();
		return doReceive(remotefile, offset, new PassiveConnection());
	}

}
