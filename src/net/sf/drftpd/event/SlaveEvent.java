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
public class SlaveEvent extends Event {
	private RemoteSlave slave;
	
	/**
	 * @param command
	 */
	public SlaveEvent(String command, RemoteSlave slave) {
		this(command, slave, System.currentTimeMillis());
	}

	/**
	 * @param command
	 * @param time
	 */
	public SlaveEvent(String command, RemoteSlave slave, long time) {
		super(command, time);
		this.slave = slave;
	}
	
	public RemoteSlave getRSlave() {
		return this.slave;
	}
}
