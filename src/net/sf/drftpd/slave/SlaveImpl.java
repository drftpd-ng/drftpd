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

import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.master.SlaveManager;

public final class SlaveImpl extends UnicastRemoteObject implements Slave {
	private Vector transfers = new Vector();
	//	Properties cfg;
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
		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		try {
			slave = new RemoteSlave(new SlaveImpl(cfg));
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(0);
			return;
			//the compiler doesn't know that execution stops at System.exit() stops execution
		}

		LinkedRemoteFile root =
			getDefaultRoot(cfg.getProperty("slave.root"), slave);

		SlaveManager manager;
		try {
			manager =
				(SlaveManager) Naming.lookup(
					cfg.getProperty("slavemanager.url"));
		} catch (Exception ex) {
			ex.printStackTrace();
			return;
		}
		try {
			System.out.println("manager.addSlave() root: " + root);
			manager.addSlave(slave, root);
		} catch (RemoteException ex) {
			ex.printStackTrace();
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
	//public void doPassiveTransfer(RemoteFile file) {}

	public void doConnectSend(
		RemoteFile rfile,
		long offset,
		InetAddress address,
		int port)
		throws FileNotFoundException, ConnectException {

		File file = new File(root + rfile.getPath());
		if (!file.exists())
			throw new FileNotFoundException(
				"File " + file + " not found, Remotefile: " + rfile);
		Transfer transfer = null;
		try {
			Socket sock;
			sock = new Socket(address, port);
			FileInputStream is = new FileInputStream(file);
			is.skip(offset);
			OutputStream os = sock.getOutputStream();
			transfer = new Transfer(is, os);
			transfers.add(transfer);
			transfer.transfer();
			sock.close();
		} catch (ConnectException ex) {
			System.err.println(
				"Error connecting to "
					+ address
					+ ":"
					+ port
					+ " to send "
					+ file.getPath());
		} catch (Throwable ex) {
			ex.printStackTrace();
		} finally {
			transfers.remove(transfer);
		}
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(String, InetAddress, int)
	 * @author mog
	 */
	public void doConnectReceive(
		RemoteFile remotefile,
		InetAddress addr,
		int port)
		throws RemoteException, PermissionDeniedException {

		File file = new File(root + remotefile.getPath());
		FileOutputStream out;
		try {
			out = new FileOutputStream(file);
		} catch (FileNotFoundException ex) {
			throw new PermissionDeniedException(ex.toString());
		}
		/*
				CRC32 checksum = new CRC32();
				CheckedOutputStream cos = new CheckedOutputStream(os, checksum);
		*/
		Transfer transfer = null;
		try {
			Socket socket = new Socket(addr, port);
			InputStream in = socket.getInputStream();
			transfer = new Transfer(in, out);
			transfers.add(transfer);
			transfer.transfer();
		} catch (IOException ex) {
			ex.printStackTrace();
		} finally {
			transfers.remove(transfer);
		}
	}

	public SlaveStatus getSlaveStatus() {
		checkInited();
		float throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;

		for (Iterator i = transfers.iterator(); i.hasNext();) {
			Transfer transfer = (Transfer) i.next();
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
	public void mkdir(String fileName) throws RemoteException {
		if(!new File(root+fileName).mkdir()) {
			throw new RuntimeException("mkdir(): Permission denied");
		}
	}

}
