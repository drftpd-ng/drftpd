package net.sf.drftpd.mirroring;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.rmi.RemoteException;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Transfer;

/**
 * @author mog
 * @author zubov
 * @version $Id
 */
public class SlaveTransfer {
	class DstXfer extends Thread {
		private Transfer dstxfer;
		private Throwable e;
		public DstXfer(Transfer dstxfer) {
			this.dstxfer = dstxfer;
		}
		/**
		 * @return
		 */
		public int getLocalPort() throws RemoteException {
			return dstxfer.getLocalPort();
		}
		public void run() {
			try {
				dstxfer.receiveFile(
					_file.getParent(),
					'I',
					_file.getName(),
					0L);
			} catch (Throwable e) {
				this.e = e;
			}
		}
	}

	class SrcXfer extends Thread {
		private Throwable e;
		private Transfer srcxfer;
		public SrcXfer(Transfer srcxfer) {
			this.srcxfer = srcxfer;
		}
		public void run() {
			try {
				srcxfer.sendFile(_file.getPath(), 'I', 0L, true);
			} catch (Throwable e) {
				this.e = e;
			}
		}
	}
	private RemoteSlave _destSlave;
	private LinkedRemoteFile _file;
	private RemoteSlave _sourceSlave;
	//private boolean failed = false;
	private boolean finished = false;
	private Throwable stackTrace;
	/**
	 * Slave to Slave Transfers
	 */
	public SlaveTransfer(
		LinkedRemoteFile file,
		RemoteSlave sourceSlave,
		RemoteSlave destSlave) {
		_file = file;
		_sourceSlave = sourceSlave;
		_destSlave = destSlave;
	}
	public void interruptibleSleepUntilFinished() throws Throwable {
		while (!finished) {
			try {
				Thread.sleep(1000); // 1 sec
				//System.err.println("slept 1 secs");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		if (stackTrace != null)
			throw stackTrace;
	}
	public void transfer() throws IOException {
		DstXfer dstxfer = new DstXfer(_destSlave.getSlave().listen(false));
		SrcXfer srcxfer =
			new SrcXfer(
				_sourceSlave.getSlave().connect(
					new InetSocketAddress(
						_destSlave.getInetAddress(),
						dstxfer.getLocalPort()),
					false));
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
		_file.addSlave(_destSlave);
	}
}
