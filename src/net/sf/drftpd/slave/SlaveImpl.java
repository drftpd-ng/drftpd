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

import net.sf.drftpd.LinkedRemoteFile;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.master.SlaveManager;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public final class SlaveImpl extends UnicastRemoteObject implements Slave {
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

	/////////////////// TRANSFER METHODS ///////////////////////////
	/////////////////// TRANSFER METHODS ///////////////////////////
	/////////////////// TRANSFER METHODS ///////////////////////////
	/////////////////// TRANSFER METHODS ///////////////////////////
	/////////////////// TRANSFER METHODS ///////////////////////////
	// ;-)
	// SEND

	/**
	 * Starts sending 'remotefile' starting at 'offset' bytes to a outputstream from the already connected socket 'sock'.
	 */
	private Transfer doSend(RemoteFile remotefile, long offset, Socket sock) throws IOException {
		File file = new File(root + remotefile.getPath());
		if (!file.exists())
			throw new FileNotFoundException(
				"File " + file + " not found, Remotefile: " + remotefile);
		
		FileInputStream in = new FileInputStream(file);
		in.skip(offset);
		
		TransferImpl transfer = null;

		OutputStream out = sock.getOutputStream();
		return new TransferImpl(in, out);
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectSend(REmoteFile, long, InetADdress, int)
	 */
	public Transfer doConnectSend(
		RemoteFile rfile,
		long offset,
		InetAddress address,
		int port)
		throws IOException {

		Socket sock;
		sock = new Socket(address, port);
		return doSend(rfile, offset, sock);
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doListenSend(RemoteFile, long, int)
	 */
	public Transfer doListenSend(RemoteFile remotefile, long offset, int port)
		throws RemoteException, IOException {
		
		ServerSocket server = new ServerSocket(port, 1);
		//server.setSoTimeout(xxx);
		Socket sock = server.accept();
		server.close();
		return doSend(remotefile, offset, sock);
	}

	//RECEIVE
	/**
	 * Generic receive method.
	 */
	private Transfer doReceive(RemoteFile remotefile, long offset, Socket sock) throws IOException {
		
		File file = new File(root + remotefile.getPath());
		FileOutputStream out;
		out = new FileOutputStream(file);

		InputStream in = sock.getInputStream();
		/*
				CRC32 checksum = new CRC32();
				CheckedOutputStream cos = new CheckedOutputStream(os, checksum);
		*/

		return new TransferImpl(in, out);
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(RemoteFile, long, InetAddress, int)
	 */
	public Transfer doConnectReceive(
		RemoteFile remotefile,
		long offset,
		InetAddress addr,
		int port)
		throws RemoteException, PermissionDeniedException, IOException {

		Socket sock = new Socket(addr, port);
		return doReceive(remotefile, offset, sock);
	}


	/**
	 * @see net.sf.drftpd.slave.Slave#doListenReceive(RemoteFile, long, int)
	 */
	public Transfer doListenReceive(RemoteFile remotefile, long offset, int port)
		throws IOException {
		ServerSocket server = new ServerSocket(port, 1);
		//server.setSoTimeout(xxx);
		Socket sock = server.accept();
		server.close();
		return doSend(remotefile, offset, sock);
	}


}
