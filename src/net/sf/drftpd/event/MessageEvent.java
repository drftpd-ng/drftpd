/*
 * Created on 2003-aug-26
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class MessageEvent extends Event {
	private String message;
	public MessageEvent(String command, String message) {
		this(command, message, System.currentTimeMillis());
	}

	public MessageEvent(String command, String message, long time) {
		super(command, time);
		this.message = message;
	}
	public String getMessage() {
		return this.message;
	}
}
