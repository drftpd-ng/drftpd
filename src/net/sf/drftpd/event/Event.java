/*
 * Created on 2003-aug-07
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.event;

/**
 * @author mog
 */
public class Event {
	private String command;
	public Event(String command) {
		super();
		this.command = command;
	}
	
	public Event(String command, long time) {
		this(command);
		this.time = time;
	}
	public String getCommand() {
		return command;
	}
	public void setCommand(String command) {
		this.command = command;
	}
	
	private long time;
	public long getTime() {
		return time;
	}

}
