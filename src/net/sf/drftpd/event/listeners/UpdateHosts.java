/*
 * Created on 2003-sep-02
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event.listeners;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Iterator;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class UpdateHosts implements FtpListener {

	/**
	 * 
	 */
	private ConnectionManager connManager;
	public UpdateHosts(FtpConfig config, ConnectionManager connManager, String args[]) {
		super();
		this.connManager = connManager;
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.Event)
	 */
	public void actionPerformed(Event event) {
		if(event instanceof SlaveEvent) actionPerformed((SlaveEvent)event);
	}
	public void actionPerformed(SlaveEvent event) {
			PrintWriter out;
				try {
					out = new PrintWriter(new FileWriter("hosts"));
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			for (Iterator iter =
				this
					.connManager
					.getSlavemanager()
					.getSlaves()
					.iterator();
				iter.hasNext();
				) {
				RemoteSlave rslave = (RemoteSlave) iter.next();
				if(!rslave.isAvailable()) continue;
				out.println(rslave.getName() + " " + rslave.getInetAddress());
			}
	}
}
