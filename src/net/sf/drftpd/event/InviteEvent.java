/*
 * Created on 2003-oct-25
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
