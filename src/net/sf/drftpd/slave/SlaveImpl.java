package net.sf.drftpd.slave;

import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.RemoteFile;
import net.sf.drftpd.DrftpdFileFilter;
import net.sf.drftpd.RemoteSlave;

import java.rmi.server.UnicastRemoteObject;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.NotBoundException;
import java.util.Properties;
import java.util.Hashtable;
import java.util.Map;
import java.util.Stack;
import java.util.EmptyStackException;

import java.io.InputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedInputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.net.ConnectException;
import java.net.Socket;
import java.net.InetAddress;

public class SlaveImpl extends UnicastRemoteObject implements Slave {

	Properties cfg = new Properties();
	SlaveManager manager;

	public SlaveImpl() throws RemoteException {
		super();

		try {
			cfg.load(new FileInputStream("dftpd.conf"));
		} catch (IOException ex) {
			ex.printStackTrace();
		}

		try {
			File root = new File(cfg.getProperty("slave.root"));
			if(!root.isDirectory()) {
				System.out.println("slave.root = "+root.getPath()+" is not a directory!");
				System.exit(-1);
			}
			manager = (SlaveManager) Naming.lookup(cfg.getProperty("slavemanager.url"));
			manager.addSlave(
				cfg.getProperty("slave.name"),
				this,
				new RemoteFile(new RemoteSlave(this), root));

		} catch (RemoteException ex) {
			ex.printStackTrace();
		} catch (NotBoundException ex) {
			ex.printStackTrace();
		} catch (java.net.MalformedURLException ex) {
			ex.printStackTrace();
		}
	}

	public static void main(String args[]) {
		try {
			new SlaveImpl();
		} catch (RemoteException ex) {
			ex.printStackTrace();
			System.exit(0);
		}
	}

	//public void doPassiveTransfer(RemoteFile file) {}

	public void doConnectSend(
		RemoteFile file,
		long offset,
		InetAddress address,
		int port)
		throws FileNotFoundException, ConnectException {
		doConnectSend(file.getPath(), offset, address, port);
	}

	public void doConnectSend(
		String path,
		long offset,
		InetAddress address,
		int port)
		throws FileNotFoundException, ConnectException {
//cfg.getProperty("slave.root") +
		File file = new File(path);
		//System.out.println("SEND "+cfg.getProperty("slave.root")+path);
		try {
			Socket sock;
			sock = new Socket(address, port);
			//sock.setSoTimeout(60000);
			System.out.println(sock.getSendBufferSize());
			//sock.setSendBufferSize((int) file.length());

			FileInputStream is = new FileInputStream(file);
			//RAF for RESUME
			//RandomAccessFile is = new RandomAccessFile(file, "r");

			OutputStream os = sock.getOutputStream();

			transfer(is, os);
			sock.close();
		} catch (Throwable ex) {
			ex.printStackTrace();
		}
	}

	private void transfer(InputStream is, OutputStream os) throws IOException {
		try {
			byte[] buff = new byte[20480];
			int count;
			while ((count = is.read(buff)) != -1) {
				os.write(buff, 0, count);
			}
			os.close();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/*
	public Map importDatabase() {
	File root = new File(cfg.getProperty("slave.root"));
	long startTime = System.currentTimeMillis();
	Map map = serialize(root);
	System.out.println("importDatabase() finished: "+((System.currentTimeMillis()-startTime)/1000f)+" seconds elapsed");
	return map;
	}
	*/
	/*
	private Map serialize(File dir) {
	File files[] = dir.listFiles(new DftpdFileFilter());
	Hashtable table = new Hashtable(files.length);
	//if(files.length == 0) return table;
	
	Stack stack = new Stack();
	
	File cache = new File(dir.getPath()+"/.dftpd");
	//System.out.println(dir.getPath().substring(cfg.getProperty("slave.root").length()));
	Hashtable oldtable=null;
	if(cache.exists()) {
	    try {
		ObjectInputStream is = new ObjectInputStream(new FileInputStream(cache));
		oldtable = (Hashtable)is.readObject();
	    } catch(Exception ex) {
		ex.printStackTrace();
	    }
	}
	
	//System.out.println("\r\033[K"+dir.getPath()+": "+files.length+" entries");
	for(int i=0; i<files.length; i++) {
	    if(files[i].isDirectory()) {
		stack.push(files[i]);
	    }
	    //System.out.print("\r\033[K"+files[i].getPath());
	    //updateStatus(i, files.length);
	
	    RemoteFile rfile=null; // = new RemoteFile(files[i]);
	    if(oldtable != null) {
		rfile = (RemoteFile)oldtable.get(files[i].getName());
	    }
	    if(rfile == null) {
		rfile = new RemoteFile(files[i]);
	    }
	    table.put(rfile.getName(), rfile);
	}
	
	System.out.println("cache ["+table.size()+"] = "+cache);
	if(table.isEmpty()) {	
	    cache.delete();
	} else {
	    try {
		new ObjectOutputStream(new FileOutputStream(cache)).writeObject(table);
	    } catch(FileNotFoundException ex) {
		System.out.println("Could not open file: "+ex.getMessage());
	    } catch(Exception ex) {
		ex.printStackTrace();
	    }
	}
	
	while(true) {
	    try {
		dir = (File)stack.pop();
	    } catch(EmptyStackException ex) {
		break;
	    }
	    //clearStatus();
	    table.put(dir.getName(), serialize(dir));
	}
	return (Map)table;
	}
	*/
	/*
	int status=-1;
	private void updateStatus() {
	char chars[] = {'-', '\\', '|', '/'};
	status++;
	if(status >= chars.length) {
	    status = 0;
	}
	}
	
	int progress_last;
	int i=0;
	private void updateStatus(int done, int total) {
	int progress = (int)((double)done/(double)total*80);
	if(progress > progress_last) {
	    //clearStatus();
		System.out.print("=");
	    }
	}
	}
	private void clearStatus() {
	System.out.print("\r\033[K");
	i=0;
	}
	*/
}
