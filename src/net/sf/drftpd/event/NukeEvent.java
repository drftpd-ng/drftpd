/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import java.util.Hashtable;
import java.util.Map;

import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class NukeEvent extends FtpEvent {
	/**
	 * @param user
	 * @param preNukeName
	 * @param multiplier
	 * @param nukees2
	 */
	public NukeEvent(User user, FtpReq String directory, int multiplier, Hashtable nukees) {
		super(user);
		this.multiplier = multiplier;
		this.nukees = nukees;
		this.directory = directory;
		// TODO Auto-generated constructor stub
	}
	String directory;
	String multiplier;
	String reason;
	Map nukees;
}
