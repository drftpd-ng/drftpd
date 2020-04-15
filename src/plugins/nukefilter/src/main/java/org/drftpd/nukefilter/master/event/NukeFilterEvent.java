package org.drftpd.nukefilter.master.event;

import org.drftpd.nukefilter.master.NukeFilterNukeItem;

/**
 * @author phew
 */
public class NukeFilterEvent {
    private final NukeFilterNukeItem nfni;
    private final String ircString;

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
