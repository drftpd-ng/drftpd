package org.drftpd.imdb.master;

import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.imdb.common.IMDBInfo;

public class ConfigImdb extends ConfigElement<IMDBInfo> {

    public ConfigImdb(IMDBInfo value) {
        super(value);
    }
}
