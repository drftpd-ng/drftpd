package net.sf.drftpd.master;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;

/**
 * @author mog
 * @version $Id: TransferThread.java,v 1.7 2003/12/23 13:38:19 mog Exp $
 */
public class TransferThread {
	private LinkedRemoteFile file;
	private RemoteSlave dstrslave;
	private boolean finished = false;
	private boolean failed = false;
	private Throwable stackTrace;

	public TransferThread(LinkedRemoteFile file, RemoteSlave dstrslave) {
		this.file = file;
		this.dstrslave = dstrslave;
	}
	class SrcXfer extends Thread {
		private Transfer srcxfer;
		private Throwable e;
		public SrcXfer(Transfer srcxfer) {
			this.srcxfer = srcxfer;
		}
		public void run() {
			try {
				srcxfer.sendFile(file.getPath(), 'I', 0L, true);
			} catch (Throwable e) {
				this.e = e;
			}
		}
	}
	class DstXfer extends Thread {
		private Transfer dstxfer;
		private Throwable e;
		public DstXfer(Transfer dstxfer) {
			this.dstxfer = dstxfer;
		}
		public void run() {
			try {
				dstxfer.receiveFile(file.getParent(), 'I', file.getName(), 0L);
			} catch (Throwable e) {
				this.e = e;
			}
		}
		/**
		 * @return
		 */
		public int getLocalPort() throws RemoteException {
			return dstxfer.getLocalPort();
		}
	}
	public void transfer() throws IOException {
		RemoteSlave srcrslave;
		srcrslave = file.getASlaveForDownload();
		//throws NoAvailableSlaveException

		DstXfer dstxfer = new DstXfer(dstrslave.getSlave().listen(false));
		SrcXfer srcxfer =
			new SrcXfer(
				srcrslave.getSlave().connect(
					new InetSocketAddress(
						dstrslave.getInetAddress(),
						dstxfer.getLocalPort()),
					false));

		//		Thread srcthread = new Thread();
		//		Thread dstthread = new Thread();
		dstxfer.start();
		srcxfer.start();
		while (srcxfer.isAlive() && dstxfer.isAlive()) {
			Thread.yield();
		}
		if (srcxfer.e != null) {
			if (srcxfer.e instanceof IOException)
				throw (IOException) srcxfer.e;
			throw new RuntimeException(srcxfer.e);
		}
		if (dstxfer.e != null) {
			if (dstxfer.e instanceof IOException)
				throw (IOException) dstxfer.e;
			throw new RuntimeException(dstxfer.e);
		}
		file.addSlave(dstrslave);
	}

	public void interruptibleSleepUntilFinished() throws Throwable {
		//	Thread callerThread = Thread.currentThread();
		while (!finished) {
			try {
				Thread.sleep(10000); // 1 sec
				System.err.println("slept 1 secs");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

		}
		//		callerThread = null;
		if (stackTrace != null)
			throw stackTrace;
	}
}
