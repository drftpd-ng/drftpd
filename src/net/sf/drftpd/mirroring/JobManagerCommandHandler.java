/*
 * Created on 2004-jan-04
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
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
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 * 
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.3 2004/01/21 07:52:46 zubov Exp $
 */
public class JobManagerCommandHandler implements CommandHandler {

	public JobManagerCommandHandler() {
		super();
	}

	private FtpReply doADDJOB(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		LinkedRemoteFile lrf;
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
		List jobs = conn.getConnectionManager().getJobManager().getAllJobs();
		for (Iterator iter = jobs.iterator(); iter.hasNext();) {
			reply.addComment(((Job) iter.next()).toString());
		}
		return reply;
	}

	private FtpReply doREMOVEJOB(BaseFtpConnection conn) {
		if (!conn.getUserNull().isAdmin())
			return FtpReply.RESPONSE_530_ACCESS_DENIED;
		return FtpReply.RESPONSE_502_COMMAND_NOT_IMPLEMENTED;
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
