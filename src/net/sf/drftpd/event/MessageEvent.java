package net.sf.drftpd.event;

/**
 * @author mog
 *
 * @version $Id: MessageEvent.java,v 1.2 2003/12/23 13:38:18 mog Exp $
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
