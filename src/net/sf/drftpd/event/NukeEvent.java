/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import java.util.Hashtable;
import java.util.Map;

import net.sf.drftpd.master.FtpRequest;
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
	public NukeEvent(User user, FtpRequest request, String directory, int multiplier, String reason, Hashtable nukees) {
		super(user, request);
		this.multiplier = multiplier;
		this.nukees = nukees;
		this.directory = directory;
		this.reason = reason;
		// TODO Auto-generated constructor stub
	}
	String reason;
	String directory;
	int multiplier;
	Map nukees;
	/**
	 * @return
	 */
	public String getDirectory() {
		return directory;
	}

	/**
	 * @return
	 */
	public int getMultiplier() {
		return multiplier;
	}

	/**
	 * @return
	 */
	public Map getNukees() {
		return nukees;
	}

	/**
	 * @return
	 */
	public String getReason() {
		return reason;
	}

	/**
	 * @param string
	 */
	public void setReason(String string) {
		reason = string;
	}

}
