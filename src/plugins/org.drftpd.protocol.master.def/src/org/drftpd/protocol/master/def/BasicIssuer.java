/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.master.def;

import org.drftpd.exceptions.SSLUnavailableException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.master.AbstractBasicIssuer;
import org.drftpd.slave.TransferIndex;
import org.drftpd.slave.async.AsyncCommand;
import org.drftpd.slave.async.AsyncCommandArgument;

/**
 * @author fr0w
 * @see AbstractBasicIssuer
 * @version $Id$
 */
public class BasicIssuer extends AbstractBasicIssuer {
	public String issueChecksumToSlave(RemoteSlave rslave, String path)	throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "checksum", path));

		return index;
	}

	public String issueConnectToSlave(RemoteSlave rslave, String ip, int port,
			boolean encryptedDataChannel, boolean useSSLClientHandshake) throws SlaveUnavailableException, SSLUnavailableException {
		
		boolean sslReady = rslave.getTransientKeyedMap().getObjectBoolean(RemoteSlave.SSL);
		if (!sslReady && encryptedDataChannel) {
			// althought ssl was requested the slave does not support ssl.
			throw new SSLUnavailableException("Encryption was requested but '"+rslave.getName()+"' doesn't support it");
		}
		
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "connect", 
				new String[]{ip + ":" + port, String.valueOf(encryptedDataChannel), String.valueOf(useSSLClientHandshake)}));

		return index;
	}

	/**
	 * @return String index, needs to be used to fetch the response
	 */
	public String issueDeleteToSlave(RemoteSlave rslave, String sourceFile) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "delete", sourceFile));

		return index;
	}

	public String issueListenToSlave(RemoteSlave rslave, boolean isSecureTransfer,
			boolean useSSLClientMode) throws SlaveUnavailableException, SSLUnavailableException {
		
		boolean sslReady = rslave.getTransientKeyedMap().getObjectBoolean(RemoteSlave.SSL);
		if (!sslReady && isSecureTransfer) {
			// althought ssl was requested the slave does not support ssl.
			throw new SSLUnavailableException("The transfer needed SSL but '"+rslave.getName()+"' doesn't support it");
		}
		
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "listen", ""
				+ isSecureTransfer + ":" + useSSLClientMode));

		return index;
	}

	public String issueMaxPathToSlave(RemoteSlave rslave) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommand(index, "maxpath"));

		return index;
	}

	public String issuePingToSlave(RemoteSlave rslave) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommand(index, "ping"));

		return index;
	}

	public String issueReceiveToSlave(RemoteSlave rslave, String name, char c, long position,
			String inetAddress, TransferIndex tindex, long minSpeed, long maxSpeed) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "receive", 
				new String[]{String.valueOf(c), String.valueOf(position),
				tindex.toString(), inetAddress, name, String.valueOf(minSpeed), String.valueOf(maxSpeed)}));

		return index;
	}

	public String issueRenameToSlave(RemoteSlave rslave, String from, String toDirPath,
			String toName) throws SlaveUnavailableException {
		if (toDirPath.length() == 0) { // needed for files in root
			toDirPath = "/";
		}
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "rename", 
				new String[]{from, toDirPath, toName}));

		return index;
	}

	public String issueStatusToSlave(RemoteSlave rslave) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommand(index, "status"));

		return index;
	}


	public void issueAbortToSlave(RemoteSlave rslave, TransferIndex transferIndex, String reason)
		throws SlaveUnavailableException {
		if (reason == null) {
			reason = "null";
		}
		rslave.sendCommand(new AsyncCommandArgument("abort", "abort", 
				new String[]{transferIndex.toString(), reason}));
	}


	public String issueSendToSlave(RemoteSlave rslave, String name, char c, long position,
			String inetAddress, TransferIndex tindex, long minSpeed, long maxSpeed) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "send",
				new String[]{String.valueOf(c), String.valueOf(position), tindex.toString(),
				inetAddress, name, String.valueOf(minSpeed), String.valueOf(maxSpeed)}));

		return index;
	}

	public String issueRemergeToSlave(RemoteSlave rslave, String path, boolean partialRemerge, long skipAgeCutoff, long masterTime, boolean instantOnline)
		throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommandArgument(index, "remerge", new String[]{path, 
				Boolean.toString(partialRemerge), Long.toString(skipAgeCutoff), Long.toString(masterTime), Boolean.toString(instantOnline)}));
		return index;
	}

	public void issueRemergePauseToSlave(RemoteSlave rslave) throws SlaveUnavailableException {
		rslave.sendCommand(new AsyncCommand("remergePause", "remergePause"));

    }

	public void issueRemergeResumeToSlave(RemoteSlave rslave) throws SlaveUnavailableException {
		rslave.sendCommand(new AsyncCommand("remergeResume", "remergeResume"));

    }

	@Override
	public String issueCheckSSL(RemoteSlave rslave) throws SlaveUnavailableException {
		String index = rslave.fetchIndex();
		rslave.sendCommand(new AsyncCommand(index, "checkSSL"));
		
		return index;
	}
}
