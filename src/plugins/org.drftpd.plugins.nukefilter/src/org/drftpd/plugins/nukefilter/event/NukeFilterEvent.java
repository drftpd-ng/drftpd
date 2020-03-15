package org.drftpd.master.plugins.nukefilter.event;

import org.drftpd.master.plugins.nukefilter.NukeFilterNukeItem;

/**
 * @author phew
 */
public class NukeFilterEvent {
	private NukeFilterNukeItem nfni;
	private String ircString;
	
	public NukeFilterEvent(NukeFilterNukeItem nfni, String ircString) {
		this.nfni = nfni;
		this.ircString = ircString;
	}
	
	public NukeFilterNukeItem getNukeFilterNukeItem() {
		return nfni;
	}
	
	public String getIRCString() {
		return ircString;
	}
	
}
