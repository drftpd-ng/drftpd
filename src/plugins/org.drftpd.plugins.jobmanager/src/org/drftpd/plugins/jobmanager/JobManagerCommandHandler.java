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
package org.drftpd.plugins.jobmanager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.commandmanager.*;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * CommandHandler plugin for viewing and manipulating the JobManager queue.
 * 
 * @author mog
 * @version $Id: JobManagerCommandHandler.java,v 1.19 2004/07/09 17:08:38 zubov
 *          Exp $
 */
public class JobManagerCommandHandler extends CommandInterface {

	private ResourceBundle _bundle;
	private String _keyPrefix;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();
    	_keyPrefix = getClass().getName() + ".";
    }
    
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
	public CommandResponse doADDJOB(CommandRequest request)
			throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		StringTokenizer st = new StringTokenizer(request.getArgument());
		User user = request.getSession().getUserNull(request.getUser());
		FileHandle lrf;

		try {
			try {
				lrf = request.getCurrentDirectory().getFile(st.nextToken(), user);
			} catch (ObjectNotValidException e) {
				throw new ImproperUsageException(
						"addjob does not handle directories or links");
			}
		} catch (FileNotFoundException e) {
			return new CommandResponse(500, "File does not exist");
		}

		int priority;

		try {
			priority = Integer.parseInt(st.nextToken());
		} catch (Exception e) {
			throw new ImproperUsageException();
		}

		int timesToMirror;

		try {
			timesToMirror = Integer.parseInt(st.nextToken());
		} catch (NumberFormatException e) {
			throw new ImproperUsageException();
		}

		HashSet<String> destSlaves = new HashSet<>();

		while (st.hasMoreTokens()) {
			String slaveName = st.nextToken();

			try {
				GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(
						slaveName);
			} catch (ObjectNotFoundException e1) {
				response
						.addComment(slaveName
								+ "was not found, cannot add to destination slave list");

				continue;
			}

			destSlaves.add(slaveName);
		}

		if (destSlaves.size() == 0) {
			throw new ImproperUsageException();
		}

		Job job = new Job(lrf, destSlaves, priority, timesToMirror);
		getJobManager().addJobToQueue(job);

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("job", job);
		response.addComment(request.getSession().jprintf(_bundle, env,
				_keyPrefix + "addjob.success"));

		return response;
	}
	
	public JobManager getJobManager() {
		for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
			if (plugin instanceof JobManager) {
				return (JobManager) plugin;
			}
		}
		throw new RuntimeException("JobManager is not loaded");
	}

	public CommandResponse doLISTJOBS(CommandRequest request) {

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();
		TreeSet<Job> treeSet = new TreeSet<>(new JobIndexComparator());
		treeSet.addAll(getJobManager().getAllJobsFromQueue());

		for (Job job : treeSet) {
			env.add("job", job);
			env.add("count", job.getIndex());
			synchronized (job) {
				if (job.isTransferring()) {
					env.add("speed", Bytes.formatBytes(job.getSpeed()));
					env.add("progress", Bytes.formatBytes(job.getProgress()));
					try {
						env.add("total", Bytes.formatBytes(job.getFile().getSize()));
					} catch (FileNotFoundException e) {
						env.add("total", "0");
					}
					env.add("srcslave", job.getSourceSlave().getName());
					env.add("destslave", job.getDestinationSlave().getName());
					response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "listjobrunning"));
				} else {
					response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "listjobwaiting"));
				}
			}
		}
		env = new ReplacerEnvironment();
		env.add("total", treeSet.size());
		response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "sizeofjobs"));
		return response;
	}
	
	public CommandResponse doLISTRUNNINGJOBS(CommandRequest request) {

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();
		TreeSet<Job> treeSet = new TreeSet<>(new JobIndexComparator());
		treeSet.addAll(getJobManager().getAllJobsFromQueue());

		for (Job job : treeSet) {
			env.add("job", job.getFile().getPath());
			env.add("count", job.getIndex());
			synchronized (job) {
				if (job.isTransferring()) {
					env.add("speed", Bytes.formatBytes(job.getSpeed()));
					env.add("progress", Bytes.formatBytes(job.getProgress()));
					try {
						env.add("total", Bytes.formatBytes(job.getFile().getSize()));
					} catch (FileNotFoundException e) {
						env.add("total", "0");
					}
					env.add("srcslave", job.getSourceSlave().getName());
					env.add("destslave", job.getDestinationSlave().getName());
					response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "listjobrunning"));
				}
			}
		}
		env = new ReplacerEnvironment();
		env.add("total", treeSet.size());
		response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "sizeofjobs"));
		return response;
	}

	public CommandResponse doREMOVEJOBS(CommandRequest request) {
		TreeSet<Job> treeSet = new TreeSet<>(new JobIndexComparator());
		treeSet.addAll(getJobManager().getAllJobsFromQueue());
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		for (Job job : treeSet) {
			if (!job.isTransferring()) {
				getJobManager().stopJob(job);
			}
		}
		response.addComment("Removing all non transfering jobs");
		return response;		
	}
		
	public CommandResponse doREMOVEJOB(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
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

		ArrayList<Range> rangeList = new ArrayList<>();
		String rangeString = request.getArgument();
		String[] ranges = rangeString.split(" ");
		for (String range : ranges) {
			if (!range.contains("-")) {
				long val = Long.parseLong(range);
				rangeList.add(new Range(val, val));
			} else {
				String[] vals = range.split("-");
				rangeList.add(new Range(Long.parseLong(vals[0]), Long
						.parseLong(vals[1])));
			}
		}
		TreeSet<Job> treeSet = new TreeSet<>(new JobIndexComparator());
		treeSet.addAll(getJobManager().getAllJobsFromQueue());
		ReplacerEnvironment env = new ReplacerEnvironment();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		for (Job job : treeSet) {
			for (Range range : rangeList) {
				if (range.contains(job.getIndex())) {
					env.add("job", job);
					getJobManager().stopJob(job);
					response.addComment(request.getSession().jprintf(_bundle, env,
							_keyPrefix + "removejob.success"));
				}
			}
		}
		return response;
	}

	public CommandResponse doSTARTJOBS(CommandRequest request) {

		getJobManager().startJobs();

		return new CommandResponse(200, "JobTransfers will now start");
	}

	public CommandResponse doSTOPJOBS(CommandRequest request) {

		getJobManager().stopJobs();

		return new CommandResponse(200,
				"All JobTransfers will stop after their current transfer");
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
}
