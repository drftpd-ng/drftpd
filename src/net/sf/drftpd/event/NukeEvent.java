package net.sf.drftpd.event;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.usermanager.User;

import org.jdom.Element;

/**
 * @author mog
 *
 * @version $Id: NukeEvent.java,v 1.16 2003/12/23 13:38:18 mog Exp $
 */
public class NukeEvent extends UserEvent {
	private long nukedAmount;
	private long size;
	private String path;
	
	public NukeEvent(User user, String command, String path, long size, long nukedAmount, int multiplier, String reason, Map nukees) {
		this(user, command, path, System.currentTimeMillis(), size, nukedAmount, multiplier, reason, nukees);
	}
	/**
	 * @param user
	 * @param preNukeName
	 * @param multiplier
	 * @param nukees
	 */
	public NukeEvent(User user, String command, String path, long time, long size, long nukedAmount, int multiplier, String reason, Map nukees) {
		super(user, command,  time);
		this.multiplier = multiplier;
		this.reason = reason;
		this.path = path;
		this.nukees = nukees;
		this.size = size;
		this.nukedAmount = nukedAmount;
	}
	
	private String reason;
	private int multiplier;
	private Map nukees;

	public int getMultiplier() {
		return multiplier;
	}

	/**
	 * String username as key
	 * Integer debt as value
	 * @deprecated
	 */
	public Map getNukees() {
		return nukees;
	}

	public Collection getNukees2() {
		return IRCListener.map2nukees(nukees);
	}

	public String getReason() {
		return reason;
	}

	public void setReason(String string) {
		reason = string;
	}

	public String toString() {
		return "[NUKE:"+getPath()+",multiplier="+getMultiplier()+"]";
	}

	public Element toJDOM() {
		Element element = new Element("nuke");
		element.addContent(new Element("user").setText(this.getUser().getUsername()));
		element.addContent(new Element("path").setText(this.getPath()));
		element.addContent(new Element("multiplier").setText(Integer.toString(this.getMultiplier())));
		element.addContent(new Element("reason").setText(this.getReason()));
		element.addContent(new Element("time").setText(Long.toString(this.getTime())));
		
		element.addContent(new Element("size").setText(Long.toString(getSize())));
		element.addContent(new Element("nukedAmount").setText(Long.toString(getNukedAmount())));
		
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
		element.addContent(nukees);
		return element;
	}

	public String getPath() {
		return path;
	}

	public long getSize() {
		return size;
	}

	public long getNukedAmount() {
		return nukedAmount;
	}

}
