/*
 * Created on 2003-jun-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.event;

import java.util.Iterator;
import java.util.Map;

import net.sf.drftpd.master.usermanager.User;

import org.jdom.Element;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class NukeEvent extends FtpEvent {

	public NukeEvent(User user, String command, String directory, int multiplier, String reason, Map nukees) {
		this(user, command, directory, System.currentTimeMillis(), multiplier, reason, nukees);
	}
	/**
	 * @param user
	 * @param preNukeName
	 * @param multiplier
	 * @param nukees
	 */
	public NukeEvent(User user, String command, String directory, long time, int multiplier, String reason, Map nukees) {
		super(user, command, time);
		this.multiplier = multiplier;
		this.reason = reason;
		this.directory = directory;
		this.nukees = nukees;
	}
	
	String directory;
	String reason;
	int multiplier;
	Map nukees;
	/**
	 * @return
	 */
	public int getMultiplier() {
		return multiplier;
	}

	/**
	 * String username as key
	 * Integer debt as value
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

	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	public String toString() {
		return "["+getDirectory()+",multiplier="+getMultiplier()+"]";
	}

	public Element toXML() {
		Element element = new Element("nuke");
		element.addContent(new Element("user").setText(this.getUser().getUsername()));
		element.addContent(new Element("path").setText(this.getDirectory()));
		element.addContent(new Element("multiplier").setText(Integer.toString(this.getMultiplier())));
		element.addContent(new Element("reason").setText(this.getReason()));
		element.addContent(new Element("time").setText(Long.toString(this.getTime())));
		element.addContent(new Element("command").setText(this.getCommand()));
		
		Element nukees = new Element("nukees");
		for (Iterator iter = this.getNukees().entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			String username = (String)entry.getKey();
			Long amount = (Long)entry.getValue();
			Element nukee = new Element("nukee");
			nukee.addContent(new Element("username").setText(username));
			nukee.addContent(new Element("amount").setText(amount.toString()));
			nukees.addContent(nukee);
		}
		return element;
	}
	/**
	 * @return
	 */
	public String getDirectory() {
		return directory;
	}

}
