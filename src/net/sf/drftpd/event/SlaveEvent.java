package net.sf.drftpd.event;

import net.sf.drftpd.master.RemoteSlave;

/**
 * @author mog
 *
 * @version $Id: SlaveEvent.java,v 1.3 2003/12/23 13:38:18 mog Exp $
 */
public class SlaveEvent extends MessageEvent {
	private RemoteSlave slave;
	
	public SlaveEvent(String command, RemoteSlave rslave) {
		this(command, null, rslave);
	}
	/**
	 * @param command
	 */
	public SlaveEvent(String command, String message, RemoteSlave rslave) {
		this(command, message, rslave, System.currentTimeMillis());
	}

	/**
	 * @param command
	 * @param time
	 */
	public SlaveEvent(String command, String message, RemoteSlave slave, long time) {
		super(command, message, time);
		this.slave = slave;
	}
	
	public RemoteSlave getRSlave() {
		return this.slave;
	}
}
