/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.command.plugins;

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.slave.SlaveStatus;

/**
 * @author mog
 *
 * @version $Id: SlaveManagment.java,v 1.7 2004/04/20 04:11:48 mog Exp $
 */
public class SlaveManagment implements CommandHandler {
	public void unload() {}
	public void load(CommandManagerFactory initializer) {}

	private FtpReply doSITE_CHECKSLAVES(BaseFtpConnection conn) {
		return new FtpReply(
			200,
			"Ok, "
				+ conn.getSlaveManager().verifySlaves()
				+ " stale slaves removed");
	}

	private FtpReply doSITE_KICKSLAVE(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		RemoteSlave rslave;
		try {
			rslave =
				conn.getConnectionManager().getSlaveManager().getSlave(
					conn.getRequest().getArgument());
		} catch (ObjectNotFoundException e) {
			return new FtpReply(200, "No such slave");
		}
		if (!rslave.isAvailable()) {
			return new FtpReply(200, "Slave is already offline");
		}
		rslave.setOffline(
			"Slave kicked by " + conn.getUserNull().getUsername());
		return FtpReply.RESPONSE_200_COMMAND_OK;
	}

	/** Lists all slaves used by the master
	 * USAGE: SITE SLAVES
	 * 
	 */
	private FtpReply doSITE_SLAVES(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		Collection slaves = conn.getSlaveManager().getSlaves();
		FtpReply response =
			new FtpReply(200, "OK, " + slaves.size() + " slaves listed.");

		for (Iterator iter = conn.getSlaveManager().getSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			response.addComment(rslave.toString());
			try {
				SlaveStatus status = rslave.getStatus();
				
			} catch (SlaveUnavailableException e) {
				//slave is offline
			}
			
			
		}
		return response;
	}
	
	private FtpReply doSITE_REMERGE(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin()) {
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		}
		if (!conn.getRequest().hasArgument()) {
			return FtpReply.RESPONSE_501_SYNTAX_ERROR;
		}
		RemoteSlave rslave;
		try {
			rslave =
				conn.getConnectionManager().getSlaveManager().getSlave(
					conn.getRequest().getArgument());
		} catch (ObjectNotFoundException e) {
			return new FtpReply(200, "No such slave");
		}
		if (!rslave.isAvailable()) {
			return new FtpReply(200, "Slave is offline");
		}
		try {
			conn.getConnectionManager().getSlaveManager().remerge(rslave);
		} catch (IOException e) {
			rslave.setOffline("IOException during remerge()");
			return new FtpReply(200, "IOException during remerge()");			
		} catch (SlaveUnavailableException e) {
			rslave.setOffline("Slave Unavailable during remerge()");
			return new FtpReply(200, "Slave Unavailable during remerge()");
		}
		return FtpReply.RESPONSE_200_COMMAND_OK;

	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE CHECKSLAVES".equals(cmd))
			return doSITE_CHECKSLAVES(conn);
		if ("SITE KICKSLAVE".equals(cmd))
			return doSITE_KICKSLAVE(conn);
		if ("SITE SLAVES".equals(cmd))
			return doSITE_SLAVES(conn);
		if ("SITE REMERGE".equals(cmd))
			return doSITE_REMERGE(conn);
		throw UnhandledCommandException.create(
			SlaveManagment.class,
			conn.getRequest());
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
	 */
	public CommandHandler initialize(BaseFtpConnection conn, CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}
}
