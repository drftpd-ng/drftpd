/*
 * Created on 2004-jan-04
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.mirroring;

import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;

/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 * 
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.2 2004/01/11 23:11:54 mog Exp $
 */
public class JobManagerCommandHandler implements CommandHandler {

	public JobManagerCommandHandler() {
		super();
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		String cmd = conn.getRequest().getCommand();
		if ("LISTJOBS".equals(cmd)) {
			return doLISTJOBS(conn);
		}

		throw UnhandledCommandException.create(
			JobManagerCommandHandler.class,
			conn.getRequest());

	}

	private FtpReply doLISTJOBS(BaseFtpConnection conn) {
		FtpReply reply = new FtpReply(200);
		List jobs = conn.getConnectionManager().getJobManager().getAllJobs();
		for (Iterator iter = jobs.iterator(); iter.hasNext();) {
			reply.addComment(((Job) iter.next()).toString());
		}
		return reply;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
