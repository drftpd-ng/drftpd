package net.sf.drftpd.event;

/**
 * @author mog
 *
 * @version $Id: InviteEvent.java,v 1.3 2003/12/23 13:38:18 mog Exp $
 */
public class InviteEvent extends Event {
	private String ircUser;
	public InviteEvent(String command, String ircUser) {
		super(command, System.currentTimeMillis());
		this.ircUser = ircUser;
	}

	public String getUser() {
		return this.ircUser;
	}
}
