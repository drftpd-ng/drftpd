/*
 * Created on 2003-aug-09
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.io.IOException;
import java.rmi.RemoteException;

import net.sf.drftpd.slave.Transfer;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class TransferThread implements Runnable {
	private Transfer transfer;
	private Thread thread;
	private Thread callerThread;
	private RemoteSlave rslave;
	private boolean finished = false;
	private boolean failed = false;
	private Throwable stackTrace;
	
	public TransferThread(RemoteSlave rslave, Transfer transfer) {
		this.transfer = transfer;
		this.rslave = rslave;
		this.thread = new Thread(this);
		this.thread.start();
	}
	
	public void run() {
		try {
			System.err.println("transfer.transfer()");
			this.transfer.transfer();
			System.err.println("transfer.transfer() returned");
		} catch (RemoteException e) {
			rslave.handleRemoteException(e);
			stackTrace = e;
			failed = true;
		} catch (IOException e) {
			e.printStackTrace();
			stackTrace = e;
			failed = true;
		} finally {
			finished = true;
			if(this.callerThread != null)
				this.callerThread.interrupt();
		}
	}

	public void interruptibleSleepUntilFinished() throws Throwable {
		callerThread = Thread.currentThread();
		while (!finished) {
			try {
				Thread.sleep(10000); // 1 sec
				System.err.println("slept 1 secs");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			
		}
		callerThread = null;
		if(stackTrace != null) throw stackTrace;
	}
}
