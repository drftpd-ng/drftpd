/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.event;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.drftpd.usermanager.User;
import org.jdom.Element;


/**
 * @author mog
 *
 * @version $Id$
 */
public class NukeEvent extends UserEvent {
    private int multiplier;
    private long nukedAmount;
    private Map nukees;
    private String path;
    private String reason;
    private long size;

    public NukeEvent(User user, String command, String path, long size,
        long nukedAmount, int multiplier, String reason, Map nukees) {
        this(user, command, path, System.currentTimeMillis(), size,
            nukedAmount, multiplier, reason, nukees);
    }

    public NukeEvent(User user, String command, String path, long time,
        long size, long nukedAmount, int multiplier, String reason, Map nukees) {
        super(user, command, time);
        this.multiplier = multiplier;
        this.reason = reason;
        this.path = path;
        this.nukees = nukees;
        this.size = size;
        this.nukedAmount = nukedAmount;
    }

    public int getMultiplier() {
        return multiplier;
    }

    public long getNukedAmount() {
        return nukedAmount;
    }

    /**
     * String username as key
     * Integer debt as value
     * @deprecated
     */
    public Map getNukees() {
        return nukees;
    }

    /**
     * Returns a list of <code>net.sf.drftpd.Nukee</code> objects.
     * @return a list of <code>net.sf.drftpd.Nukee</code> objects.
     * @see net.sf.drftpd.Nukee
     */
    public List getNukees2() {
        return SiteBot.map2nukees(nukees);
    }

    public String getPath() {
        return path;
    }

    public String getReason() {
        return reason;
    }

    public long getSize() {
        return size;
    }

    public void setReason(String string) {
        reason = string;
    }

    public Element toJDOM() {
        Element element = new Element("nuke");
        element.addContent(new Element("user").setText(getUser().getName()));
        element.addContent(new Element("path").setText(this.getPath()));
        element.addContent(new Element("multiplier").setText(Integer.toString(
                    getMultiplier())));
        element.addContent(new Element("reason").setText(this.getReason()));
        element.addContent(new Element("time").setText(Long.toString(getTime())));

        element.addContent(new Element("size").setText(Long.toString(getSize())));
        element.addContent(new Element("nukedAmount").setText(Long.toString(
                    getNukedAmount())));

        Element nukees = new Element("nukees");

        for (Iterator iter = getNukees().entrySet().iterator(); iter.hasNext();) {
            Map.Entry entry = (Map.Entry) iter.next();
            String username = (String) entry.getKey();
            Long amount = (Long) entry.getValue();
            Element nukee = new Element("nukee");
            nukee.addContent(new Element("username").setText(username));
            nukee.addContent(new Element("amount").setText(amount.toString()));
            nukees.addContent(nukee);
        }

        element.addContent(nukees);

        return element;
    }

    public String toString() {
        return "[NUKE:" + getPath() + ",multiplier=" + getMultiplier() + "]";
    }

    public void setUser(User user) {
        _user = user;
    }
}
