package net.sf.drftpd.slave;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.PermissionDeniedException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.FileRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;

/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public class SlaveImpl extends UnicastRemoteObject implements Slave {
	private Vector transfers = new Vector();
	//Properties cfg;

	SlaveManager slavemanager;
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
		String root;
		
		try {
			cfg.load(new FileInputStream("drftpd.conf"));
		} catch (IOException ex) {
			ex.printStackTrace();
			System.err.println("Could not open drftpd.conf, exiting.");
			System.exit(-1);
			return;
		}
		root = cfg.getProperty("slave.root");
		
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

		try {
			LinkedRemoteFile slaveroot = SlaveImpl.getDefaultRoot(root, slave);

			System.out.println("manager.addSlave() root: " + slaveroot);
			manager.addSlave(slave, slaveroot);
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(-1);
			return;
		} catch(IOException ex) {
			ex.printStackTrace();
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
	
	/**
	 * returns the {LinkedRemoteFile} directory that will be serialized and registered at the master.
	 */
	public static LinkedRemoteFile getDefaultRoot(
		String root,
		RemoteSlave slave) throws IOException {

		File rootfile = new File(root);
		if (!rootfile.isDirectory()) {
			throw new InvalidDirectoryException(
				"slave.root = " + rootfile.getPath() + " is not a directory!");
		}
		LinkedRemoteFile linkedroot = new LinkedRemoteFile(slave, new FileRemoteFile(root, rootfile));
		/* DEBUG */
		if(!linkedroot.isDirectory()) throw new RuntimeException("LinkedRemoteFile root is not a directory while FileRemoteRoot was.");
		return linkedroot;
	}

	public SlaveStatus getSlaveStatus() {
		checkInited();
		float throughputUp = 0, throughputDown = 0;
		int transfersUp = 0, transfersDown = 0;

		for (Iterator i = transfers.iterator(); i.hasNext();) {
			TransferImpl transfer = (TransferImpl) i.next();
			if (transfer.getDirection() == Transfer.TRANSFER_RECEIVING) {
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
	public void mkdir(User user, String path) throws PermissionDeniedException {
		System.out.println("Will create "+root+path);
		if(!new File(root+path).mkdir()) {
			throw new PermissionDeniedException("mkdir(): Permission denied");
		}
		//TODO: serialize directory ownership
	}
	
	/**
	 * @see net.sf.drftpd.slave.Slave#ping()
	 */
	public void ping() {
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
		
		TransferImpl transfer = new TransferImpl(transfers, in, conn, mode);
		return transfer;
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
		throws IOException {
		
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
	private Transfer doReceive(RemoteFile dir, String filename, User user, long offset, Connection conn) throws IOException {
		
		File file = new File(root + dir.getPath() +"/" + filename);
		System.out.println("Will write "+file);
		FileOutputStream out = new FileOutputStream(file);

//		Socket sock = conn.connect();
//		InputStream in = sock.getInputStream();
		/*
				CRC32 checksum = new CRC32();
				CheckedOutputStream cos = new CheckedOutputStream(os, checksum);
		*/
		TransferImpl transfer = new TransferImpl(transfers, conn, out);
		return transfer;
	}

	/**
	 * @see net.sf.drftpd.slave.Slave#doConnectReceive(RemoteFile, long, InetAddress, int)
	 */
	public Transfer doConnectReceive(
		RemoteFile dir,
		String filename,
		User user,
		long offset,
		InetAddress addr,
		int port)
		throws IOException {

		//Socket sock = new Socket(addr, port);
		return doReceive(dir, filename, user, offset, new ActiveConnection(addr, port));
	}


	/**
	 * @see net.sf.drftpd.slave.Slave#doListenReceive(RemoteFile, long, int)
	 */
	public Transfer doListenReceive(
		RemoteFile dir,
		String filename, User user, long offset)
		throws IOException {
//		ServerSocket server = new ServerSocket(0, 1);
		//server.setSoTimeout(xxx);
//		Socket sock = server.accept();
//		server.close();
		return doReceive(dir, filename, user, offset, new PassiveConnection());
	}


	/**
	 * @see net.sf.drftpd.slave.Slave#checkSum(String)
	 */
	public long checkSum(String path) throws IOException {
		System.out.println("Checksumming: "+path);
		CRC32 crc32 = new CRC32();
		InputStream in = new CheckedInputStream(new FileInputStream(root+path), crc32);
		byte buf[] = new byte[1024];
		while(in.read(buf) != -1);
		return crc32.getValue();
	}

	public SFVFile getSFVFile(String path) throws IOException {
		return new SFVFile(new BufferedReader(new FileReader(root+path)));
	}
	/**
	 * @see net.sf.drftpd.slave.Slave#rename(String, String)
	 */
	public void rename(String from, String to)
		throws IOException {
		
		File fromfile = new File(root+from);
		if(!fromfile.exists()) throw new FileNotFoundException("cannot rename from "+from+", file does not exist");
		File tofile = new File(root+to);
		if(tofile.exists()) throw new FileExistsException("cannot rename from "+from+" to "+to+", destination exists");
		fromfile.renameTo(tofile);
	}

	public void delete(String path) throws IOException {
		File file = new File(root+path);
		if(!file.exists()) throw new FileNotFoundException("cannot delete "+path+", file does not exist");
		if(!file.delete()) throw new PermissionDeniedException("could not delete "+path);
	}
}
