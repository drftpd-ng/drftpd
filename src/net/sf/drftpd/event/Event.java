/*
 * Created on 2003-aug-07
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Event {
	private String command;
	/**
	 * 
	 */
	public Event(String command) {
		super();
		this.command = command;
	}
	
	public Event(String command, long time) {
		this(command);
		this.time = time;
	}
	/**
	 * @return
	 */
	public String getCommand() {
		return command;
	}
	
	private long time;
	/**
		 * @return
		 */
	public long getTime() {
		return time;
	}

}
