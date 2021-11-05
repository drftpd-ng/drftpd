package org.drftpd.master.commands.nuke;

import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.master.commands.nuke.metadata.NukeData;

public class ConfigNuke extends ConfigElement<NukeData> {

    public ConfigNuke(NukeData value) {
        super(value);
    }
}
