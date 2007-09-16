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
package org.drftpd.jobmanager;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.StringTokenizer;
import java.util.TreeSet;


import org.drftpd.Bytes;
import org.drftpd.commandmanager.CommandHandler;
import org.drftpd.commandmanager.CommandHandlerFactory;
import org.drftpd.commandmanager.CommandManager;
import org.drftpd.commandmanager.CommandManagerFactory;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.Reply;
import org.drftpd.commandmanager.ReplyException;
import org.drftpd.commandmanager.UnhandledCommandException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 * 
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.19 2004/07/09 17:08:38 zubov
 *          Exp $
 */
public class JobManagerCommandHandler implements CommandHandler,
		CommandHandlerFactory {

	public JobManagerCommandHandler() {
		super();
	}

	/**
	 * USAGE: <file><priority>[destslave ...]
	 * 
	 * @param conn
	 * @return
	 * @throws ImproperUsageException
	 * @throws ReplyException
	 * @throws FileNotFoundException
	 */
	private Reply doADDJOB(BaseFtpConnection conn)
			throws ImproperUsageException, ReplyException {

		if (!conn.getRequest().hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer st = new StringTokenizer(conn.getRequest()
				.getArgument());
		FileHandle lrf;

		try {
			try {
				lrf = conn.getCurrentDirectory().getFile(st.nextToken());
			} catch (ObjectNotValidException e) {
				throw new ImproperUsageException(
						"addjob does not handle directories or links");
			}
		} catch (FileNotFoundException e) {
			return new Reply(500, "File does not exist");
		}

		int priority;

		try {
			priority = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e) {
			throw new ImproperUsageException();
		}

		int timesToMirror;

		try {
			timesToMirror = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e) {
			throw new ImproperUsageException();
		}

		HashSet<RemoteSlave> destSlaves = new HashSet<RemoteSlave>();
		Reply reply = new Reply(200);

		while (st.hasMoreTokens()) {
			String slaveName = st.nextToken();
			RemoteSlave rslave;

			try {
				rslave = conn.getGlobalContext().getSlaveManager()
						.getRemoteSlave(slaveName);
			} catch (ObjectNotFoundException e1) {
				reply
						.addComment(slaveName
								+ "was not found, cannot add to destination slave list");

				continue;
			}

			destSlaves.add(rslave);
		}

		if (destSlaves.size() == 0) {
			throw new ImproperUsageException();
		}

		Job job = new Job(lrf, destSlaves, priority, timesToMirror);
		conn.getGlobalContext().getJobManager().addJobToQueue(job);

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("job", job);
		reply.addComment(conn.jprintf(JobManagerCommandHandler.class,
				"addjob.success", env));

		return reply;
	}

	private Reply doLISTJOBS(BaseFtpConnection conn) {

		Reply reply = new Reply(200);
		ReplacerEnvironment env = new ReplacerEnvironment();
		TreeSet<Job> treeSet = new TreeSet<Job>(new JobIndexComparator());
		treeSet.addAll(conn.getGlobalContext().getJobManager()
				.getAllJobsFromQueue());

		for (Job job : treeSet) {
			env.add("job", job);
			env.add("count", job.getIndex());
			synchronized (job) {
				if (job.isTransferring()) {
					env.add("speed", Bytes.formatBytes(job.getSpeed()));
					env.add("progress", Bytes.formatBytes(job.getProgress()));
					try {
						env.add("total", Bytes.formatBytes(job.getFile()
								.getSize()));
					} catch (FileNotFoundException e) {
						env.add("total", "0");
					}
					env.add("srcslave", job.getSourceSlave().getName());
					env.add("destslave", job.getDestinationSlave().getName());
					reply.addComment(conn.jprintf(
							JobManagerCommandHandler.class, "listjobrunning",
							env));
				} else {
					reply.addComment(conn.jprintf(
							JobManagerCommandHandler.class, "listjobwaiting",
							env));
				}
			}
		}
		env = new ReplacerEnvironment();
		env.add("total", treeSet.size());
		reply.addComment(conn.jprintf(JobManagerCommandHandler.class,
				"sizeofjobs", env));
		return reply;
	}

	private Reply doREMOVEJOB(BaseFtpConnection conn) throws ReplyException,
			ImproperUsageException {

		if (!conn.getRequest().hasArgument()) {
			throw new ImproperUsageException();
		}
		class Range {
			long _low, _high;

			Range(long low, long high) {
				if (0 >= low || low > high) {
					throw new IllegalArgumentException("0 < low <= high");
				}
				_low = low;
				_high = high;
			}

			public boolean contains(long val) {
				return _low <= val && val <= _high;
			}
		}

		ArrayList<Range> rangeList = new ArrayList<Range>();
		String rangeString = conn.getRequest().getArgument();
		String[] ranges = rangeString.split(" ");
		for (String range : ranges) {
			if (range.indexOf("-") == -1) {
				long val = Long.parseLong(range);
				rangeList.add(new Range(val, val));
			} else {
				String[] vals = range.split("-");
				rangeList.add(new Range(Long.parseLong(vals[0]), Long
						.parseLong(vals[1])));
			}
		}
		TreeSet<Job> treeSet = new TreeSet<Job>(new JobIndexComparator());
		treeSet.addAll(conn.getGlobalContext().getJobManager()
				.getAllJobsFromQueue());
		ReplacerEnvironment env = new ReplacerEnvironment();

		Reply r = new Reply(200);
		for (Job job : treeSet) {
			for (Range range : rangeList) {
				if (range.contains(job.getIndex())) {
					env.add("job", job);
					conn.getGlobalContext().getJobManager().stopJob(job);
					r.addComment(conn.jprintf(JobManagerCommandHandler.class,
							"removejob.success", env));
				}
			}
		}
		return r;
	}

	private Reply doSTARTJOBS(BaseFtpConnection conn) {

		conn.getGlobalContext().getJobManager().startJobs();

		return new Reply(200, "JobTransfers will now start");
	}

	private Reply doSTOPJOBS(BaseFtpConnection conn) {

		conn.getGlobalContext().getJobManager().stopJobs();

		return new Reply(200,
				"All JobTransfers will stop after their current transfer");
	}

	public Reply execute(BaseFtpConnection conn) throws ReplyException,
			ImproperUsageException {
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

		throw UnhandledCommandException.create(JobManagerCommandHandler.class,
				conn.getRequest());
	}

	// public String getHelp(String cmd) {
	// ResourceBundle bundle = ResourceBundle.getBundle(Misc.class.getName());
	// if ("".equals(cmd))
	// return bundle.getString("help.general")+"\n";
	// else if("listjobs".equals(cmd) ||
	// "addjob".equals(cmd) ||
	// "removejob".equals(cmd) ||
	// "startjob".equals(cmd) ||
	// "stopjob".equals(cmd))
	// return bundle.getString("help."+cmd)+"\n";
	// else
	// return "";
	// }

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(BaseFtpConnection conn,
			CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}
}
