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
 * @version $Id: UpdateHosts.java,v 1.4 2003/12/23 13:38:19 mog Exp $
 */
public class UpdateHosts implements FtpListener {
	private ConnectionManager connManager;
	public UpdateHosts(FtpConfig config, ConnectionManager connManager, String args[]) {
		super();
		this.connManager = connManager;
	}

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
					.getSlaveManager()
					.getSlaves()
					.iterator();
				iter.hasNext();
				) {
				RemoteSlave rslave = (RemoteSlave) iter.next();
				if(!rslave.isAvailable()) continue;
				out.println(rslave.getName() + " " + rslave.getInetAddress());
			}
	}

	public void init(ConnectionManager mgr) {}
}
