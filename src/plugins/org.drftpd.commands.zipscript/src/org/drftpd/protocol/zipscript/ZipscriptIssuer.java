package org.drftpd.protocol.zipscript;

import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.master.AbstractIssuer;
import org.drftpd.slave.async.AsyncCommandArgument;

public class ZipscriptIssuer extends AbstractIssuer {
	public String issueSFVFileToSlave(RemoteSlave rslave, String path)throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		AsyncCommandArgument ac = new AsyncCommandArgument(index, "sfvfile", path);
		rslave.sendCommand(ac);

		return index;
	}
}
