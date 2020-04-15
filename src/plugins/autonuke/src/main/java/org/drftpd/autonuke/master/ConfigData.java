package org.drftpd.autonuke.master;

import java.util.LinkedList;

/**
 * @author scitz0
 */
public class ConfigData {
    private final LinkedList<String> _returnData;
    private NukeItem _ni;

    public ConfigData() {
        _returnData = new LinkedList<>();
    }

    public void addReturnData(String data) {
        _returnData.addLast(data);
    }

    public LinkedList<String> getReturnData() {
        return _returnData;
    }

    public NukeItem getNukeItem() {
        return _ni;
    }

    public void setNukeItem(NukeItem ni) {
        _ni = ni;
    }

}
