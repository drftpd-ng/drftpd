package org.drftpd.request.master;

import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.request.master.metadata.RequestData;

public class ConfigRequestData extends ConfigElement<RequestData> {

    public ConfigRequestData(RequestData value) {
        super(value);
    }
}
