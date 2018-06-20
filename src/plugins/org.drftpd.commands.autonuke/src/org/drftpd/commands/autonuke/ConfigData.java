package org.drftpd.commands.autonuke;

import java.util.LinkedList;

/**
 * @author scitz0
 */
public class ConfigData {
	private LinkedList<String> _returnData;
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

	public void setNukeItem(NukeItem ni) {
		_ni = ni;
	}

	public NukeItem getNukeItem() {
		return _ni;
	}

}
