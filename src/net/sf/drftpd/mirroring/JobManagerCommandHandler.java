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
package net.sf.drftpd.mirroring;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 * 
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.10 2004/03/01 04:21:04 zubov Exp $
 */
public class JobManagerCommandHandler implements CommandHandler {

	public JobManagerCommandHandler() {
		super();
	}

	private FtpReply doADDJOB(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		if (!conn.getRequest().hasArgument()) {
			return new FtpReply(
				501,
				conn.jprintf(JobManager.class.getName(), "addjob.usage"));
		}
		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		LinkedRemoteFileInterface lrf;
		FtpReply reply = new FtpReply(200);
		try {
			lrf = conn.getCurrentDirectory().getFile(st.nextToken());
		} catch (FileNotFoundException e) {
			return new FtpReply(500, "File does not exist");
		}
		int priority = Integer.parseInt(st.nextToken());
		ArrayList destSlaves = new ArrayList();
		while (st.hasMoreTokens()) {
			String slaveName = st.nextToken();
			if (slaveName.equals("null"))
				destSlaves.add(null);
			else {
				RemoteSlave rslave;
				try {
					rslave = conn.getSlaveManager().getSlave(slaveName);
				} catch (ObjectNotFoundException e1) {
					reply.addComment(
						slaveName
							+ "was not found, cannot add to destination slave list");
					continue;
				}
				destSlaves.add(rslave);
			}
		}
		if (destSlaves.size() == 0) {
			reply.addComment(
				"You must specify at least one destination slave, use null for any slave");
			return reply;
		}
		Job job = new Job(lrf, destSlaves, this, conn.getUserNull(), priority);
		conn.getConnectionManager().getJobManager().addJob(job);
		reply.addComment("Added job to queue");
		return reply;
	}

	private FtpReply doLISTJOBS(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		FtpReply reply = new FtpReply(200);
		int count = 0;
		List jobs = new ArrayList(conn.getConnectionManager().getJobManager().getAllJobs());
		for (Iterator iter = jobs.iterator(); iter.hasNext();) {
			reply.addComment(((Job) iter.next()).toString());
			count ++;
		}
		reply.addComment("There are " + count + " jobs in all");
		return reply;
	}

	private FtpReply doREMOVEJOB(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		return FtpReply.RESPONSE_502_COMMAND_NOT_IMPLEMENTED;
	}

	private FtpReply doSTARTJOBS(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		conn.getConnectionManager().getJobManager().startJobs();
		return new FtpReply(200, "JobTransfers will now start");
	}

	private FtpReply doSTOPJOBS(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		conn.getConnectionManager().getJobManager().stopJobs();
		return new FtpReply(
			200,
			"All JobTransfers will stop after their current transfer");
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("SITE LISTJOBS".equals(cmd)) {
			return doLISTJOBS(conn);
		}
		if ("SITE REMOVEJOB".equals(cmd)) {
			return doREMOVEJOB(conn);
		}
		if ("SITE ADDJOB".equals(cmd)) {
			return doADDJOB(conn);
		}
		if ("SITE STOPJOBS".equals(cmd)) {
			return doSTOPJOBS(conn);
		}
		if ("SITE STARTJOBS".equals(cmd)) {
			return doSTARTJOBS(conn);
		}

		throw UnhandledCommandException.create(
			JobManagerCommandHandler.class,
			conn.getRequest());

	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
