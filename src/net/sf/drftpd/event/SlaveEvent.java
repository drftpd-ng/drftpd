/*
 * Created on 2003-aug-07
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

import net.sf.drftpd.master.RemoteSlave;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
