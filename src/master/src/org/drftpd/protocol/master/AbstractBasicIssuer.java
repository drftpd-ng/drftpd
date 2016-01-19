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
package org.drftpd.protocol.master;

import org.drftpd.exceptions.SSLUnavailableException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slave.TransferIndex;

/**
 * In order to Master be able to scope the BasicIssuer, this abstract class was need so
 * we could instead of referecing everything to the BasicIssuer, which is out of master's scope,
 * we are referencing to this AbstractBasicIssuer which is, later on, mapped to a BasicIssuer instance.
 * @author fr0w
 * @version $Id$
 */
public abstract class AbstractBasicIssuer extends AbstractIssuer {
	public abstract String issueChecksumToSlave(RemoteSlave rslave, String path) throws SlaveUnavailableException;

	public abstract String issueConnectToSlave(RemoteSlave rslave, String ip, int port,
			boolean encryptedDataChannel, boolean useSSLClientHandshake) throws SlaveUnavailableException, SSLUnavailableException;

	public abstract String issueDeleteToSlave(RemoteSlave rslave, String sourceFile) throws SlaveUnavailableException;

	public abstract String issueListenToSlave(RemoteSlave rslave, boolean isSecureTransfer,
			boolean useSSLClientMode) throws SlaveUnavailableException, SSLUnavailableException;

	public abstract String issueMaxPathToSlave(RemoteSlave rslave) throws SlaveUnavailableException;

	public abstract String issuePingToSlave(RemoteSlave rslave) throws SlaveUnavailableException;

	public abstract String issueReceiveToSlave(RemoteSlave rslave, String name, char c, long position,
			String inetAddress, TransferIndex tindex, long minSpeed, long maxSpeed) throws SlaveUnavailableException;

	public abstract String issueRenameToSlave(RemoteSlave rslave, String from, String toDirPath,
			String toName) throws SlaveUnavailableException;

	public abstract String issueStatusToSlave(RemoteSlave rslave) throws SlaveUnavailableException;

	public abstract void issueAbortToSlave(RemoteSlave rslave, TransferIndex transferIndex, String reason)
		throws SlaveUnavailableException;


	public abstract String issueSendToSlave(RemoteSlave rslave, String name, char c, long position,
			String inetAddress, TransferIndex tindex, long minSpeed, long maxSpeed) throws SlaveUnavailableException;

	public abstract String issueRemergeToSlave(RemoteSlave rslave, String path, boolean partialRemerge,
            long skipAgeCutoff, long masterTime, boolean instantOnline) throws SlaveUnavailableException;

	public abstract void issueRemergePauseToSlave(RemoteSlave rslave) throws SlaveUnavailableException;

	public abstract void issueRemergeResumeToSlave(RemoteSlave rslave) throws SlaveUnavailableException;

	public abstract String issueCheckSSL(RemoteSlave rslave) throws SlaveUnavailableException;
}
