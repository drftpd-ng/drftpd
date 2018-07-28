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
package org.drftpd.commands.slavemanagement;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.SlaveEvent;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.*;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.filter.Filter;
import org.drftpd.slaveselection.filter.ScoreChart;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.slaveselection.filter.SlaveSelectionManager;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.*;
import java.util.Map.Entry;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SlaveManagement extends CommandInterface {

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSITE_SLAVESELECT(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		String argument = request.getArgument();
		StringTokenizer arguments = new StringTokenizer(argument);
		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}
		String type = arguments.nextToken();
		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}
		String path = arguments.nextToken();
		if (!path.startsWith(VirtualFileSystem.separator)) {
			throw new ImproperUsageException();
		}
		char direction = Transfer.TRANSFER_UNKNOWN;
		if (type.equalsIgnoreCase("up")) {
			direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
		} else if (type.equalsIgnoreCase("down")) {
			direction = Transfer.TRANSFER_SENDING_DOWNLOAD;
		} else {
			throw new ImproperUsageException();
		}
		Collection<RemoteSlave> slaves;
		try {
			slaves = GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e1) {
			return StandardCommandManager.genericResponse("RESPONSE_530_SLAVE_UNAVAILABLE");
		}
		SlaveSelectionManager ssm = null;
		try {
			ssm = (SlaveSelectionManager) GlobalContext.getGlobalContext().getSlaveSelectionManager();
		} catch (ClassCastException e) {
			return new CommandResponse(500,
					"You are attempting to test filter.SlaveSelectionManager yet you're using def.SlaveSelectionManager");
		}
		CommandResponse response = new CommandResponse(500, "***End of SlaveSelection output***");
		Collection<Filter> filters = ssm.getFilterChain(type).getFilters();
		ScoreChart sc = new ScoreChart(slaves);
		for (Filter filter : filters) {
			try {
				filter.process(sc, null, null, direction, new FileHandle(path), null);
			} catch (NoAvailableSlaveException e) {
			}
		}
		for (SlaveScore ss : sc.getSlaveScores()) {
			response.addComment(ss.getRSlave().getName() + "=" + ss.getScore());
		}
		return response;
	}

	public CommandResponse doSITE_KICKSLAVE(CommandRequest request) {
		Session session = request.getSession();

		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		RemoteSlave rslave;

		try {
			rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
		} catch (ObjectNotFoundException e) {
			return new CommandResponse(200, "No such slave");
		}

		if (!rslave.isOnline()) {
			return new CommandResponse(200, "Slave is already offline");
		}

		rslave.setOffline("Slave kicked by " +
				session.getUserNull(request.getUser()).getName());

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	/**
	 * Lists all slaves used by the master
	 * USAGE: SITE SLAVES
	 */
	public CommandResponse doSITE_SLAVES(CommandRequest request) {
		boolean showMore = request.hasArgument() &&
				(request.getArgument().equalsIgnoreCase("more"));

		Collection<RemoteSlave> slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
		CommandResponse response = new CommandResponse(200, "OK, " + slaves.size() + " slaves listed.");

		String slavestofind = GlobalContext.getConfig().getMainProperties().getProperty("default.slave.output");
		int slavesFound = 0;
		String slave = "all";
		if(request.hasArgument()){
			slave=request.getArgument().toLowerCase();
		} else if (slavestofind != null)
		{
			slave = slavestofind;
		}

		for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
			String name=rslave.getName().toLowerCase();

			if((!name.startsWith(slave))&&(!slave.equals("all")))
			{
				continue;
			}

			response = addSlaveStatus(request, response, showMore, rslave);
			slavesFound = slavesFound + 1;
		}

		if (slavesFound == 0) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"slave.none", null, request.getUser()));
		}
		return response;
	}

	public CommandResponse doSlave(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		boolean showMore = false;
		StringTokenizer arguments = new StringTokenizer(request.getArgument());
		String slaveName = arguments.nextToken();
		RemoteSlave rslave;
		try {
			rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
		} catch (ObjectNotFoundException e) {
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("slavename", slaveName);
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"slave.notfound", env, request.getUser()));
			return response;
		}
		if (arguments.hasMoreTokens()) {
			String option = arguments.nextToken();
			if (option.equalsIgnoreCase("more")) {
				showMore = true;
			} else if (option.equalsIgnoreCase("queues")) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("slavename", slaveName);
				env.add("renamesize", rslave.getRenameQueue().size());
				env.add("remergesize", rslave.getRemergeQueue().size());
				env.add("remergecrcsize", rslave.getCRCQueue().size());
				response.addComment(request.getSession().jprintf(_bundle,
						_keyPrefix+"slave.queues", env, request.getUser()));
				return response;
			} else {
				throw new ImproperUsageException();
			}
		}
		if (arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}
		return addSlaveStatus(request, response, showMore, rslave);
	}

	private CommandResponse addSlaveStatus(CommandRequest request, CommandResponse response, boolean showMore, RemoteSlave rslave) {
		Session session = request.getSession();
		if (showMore) {
			response.addComment(rslave.moreInfo());
		}

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("slavename", rslave.getName());
		try {
			env.add("slaveip", rslave.getPASVIP());
		} catch (SlaveUnavailableException e) {
			env.add("slaveip", "OFFLINE");
		}

		if (rslave.isOnline()) {
			if (!rslave.isAvailable()) {
				response.addComment(session.jprintf(_bundle, _keyPrefix+"slave.remerging", env, request.getUser()));
			} else {
				try {
					SlaveStatus status = rslave.getSlaveStatus();
					fillEnvWithSlaveStatus(env, status);
					env.add("status", rslave.isRemerging() ? "REMERGING" : "ONLINE");
					response.addComment(session.jprintf(_bundle, _keyPrefix+"slave.online", env, request.getUser()));
				} catch (SlaveUnavailableException e) {
					// should never happen since we tested slave status w/ isOnline and isAvaiable.
					throw new RuntimeException("There's a bug somewhere in the code, the slave was available now it isn't.", e);
				}
			}
		} else {
			response.addComment(session.jprintf(_bundle, _keyPrefix+"slave.offline", env, request.getUser()));
		}
		return response;
	}

	public CommandResponse doSITE_REMERGE(CommandRequest request) {
		if (!request.hasArgument()) {
			return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
		}

		RemoteSlave rslave;

		try {
			rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
		} catch (ObjectNotFoundException e) {
			return new CommandResponse(200, "No such slave");
		}

		if (!rslave.isAvailable()) {
			return new CommandResponse(200,
					"Slave is still merging from initial connect");
		}

		if (rslave.isRemerging()) {
			return new CommandResponse(200,
					"Slave is still remerging by a previous remerge command");
		}

		rslave.setRemerging(true);
		try {
			rslave.fetchResponse(SlaveManager.getBasicIssuer().issueRemergeToSlave(rslave,
					request.getCurrentDirectory().getPath(), false, 0L, 0L, false), 0);
		} catch (RemoteIOException e) {
			rslave.setOffline("IOException during remerge()");

			return new CommandResponse(200, "IOException during remerge()");
		} catch (SlaveUnavailableException e) {
			rslave.setOffline("Slave Unavailable during remerge()");

			return new CommandResponse(200, "Slave Unavailable during remerge()");
		}

		// set remerge and crc threads to status finished so that threads may terminate cleanly
		rslave.setCRCThreadFinished();
		rslave.putRemergeQueue(new RemergeMessage(rslave));

		// Wait for remerge and crc queues to drain
		while (!rslave.getRemergeQueue().isEmpty() && !rslave.getCRCQueue().isEmpty()) {
			try {
				Thread.sleep(500);
			} catch (InterruptedException e) { }
		}

		if (rslave._remergePaused.get()) {
			String message = "Remerge was paused on slave after completion, issuing resume so not to break manual remerges";
			GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, rslave));
			try {
				SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(rslave);
				rslave._remergePaused.set(false);
			} catch (SlaveUnavailableException e) {
				rslave.setOffline("Slave Unavailable during remerge()");
				return new CommandResponse(200, "Slave Unavailable during remerge()");
			}
		}

		String message = ("Remerge queueprocess finished");
		GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, rslave));
		rslave.setRemerging(false);

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	/**
	 * Usage: site slave slavename [set,addmask,delmask]
	 * @throws ImproperUsageException
	 */
	public CommandResponse doSITE_SLAVE(CommandRequest request) throws ImproperUsageException {
		Session session = request.getSession();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String argument = request.getArgument();
		StringTokenizer arguments = new StringTokenizer(argument);

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String slavename = arguments.nextToken();
		env.add("slavename", slavename);

		RemoteSlave rslave = null;

		try {
			rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
		} catch (ObjectNotFoundException e) {
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"slave.notfound", env, request.getUser()));

			return response;
		}

		if (!arguments.hasMoreTokens()) {
			if (!rslave.getMasks().isEmpty()) {
				env.add("masks", rslave.getMasks());
				response.addComment(session.jprintf(_bundle,
						_keyPrefix+"slave.masks", env, request.getUser()));
			}

			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"slave.data.header", request.getUser()));

			Map<Object,Object> props = rslave.getProperties();

			for(Entry<Object,Object> entry : props.entrySet()) {
				env.add("key", entry.getKey());
				env.add("value", entry.getValue());
				response.addComment(session.jprintf(_bundle,
						_keyPrefix+"slave.data", env, request.getUser()));
			}

			return response;
		}

		String command = arguments.nextToken();

		if (command.equalsIgnoreCase("set")) {
			if (arguments.countTokens() != 2) {
				throw new ImproperUsageException();
			}

			String key = arguments.nextToken();
			String value = arguments.nextToken();
			rslave.setProperty(key, value);
			env.add("key", key);
			env.add("value", value);
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"slave.set.success", env, request.getUser()));

			return response;
		} else if (command.equalsIgnoreCase("unset")) {
			if (arguments.countTokens() != 1) {
				throw new ImproperUsageException();
			}

			String key = arguments.nextToken();
			env.add("key", key);
			String value;
			try {
				value = rslave.removeProperty(key);
			} catch (KeyNotFoundException e) {
				response.addComment(session.jprintf(_bundle,
						_keyPrefix+"slave.unset.failure", env, request.getUser()));
				return response;
			}
			env.add("value", value);
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"slave.unset.success", env, request.getUser()));
			return response;
		} else if (command.equalsIgnoreCase("addmask")) {
			if (arguments.countTokens() != 1) {
				throw new ImproperUsageException();
			}

			String mask = arguments.nextToken();
			env.add("mask", mask);
			try {
				rslave.addMask(mask);
				response.addComment(session.jprintf(_bundle,
						_keyPrefix+"slave.addmask.success", env, request.getUser()));
				return response;
			} catch (DuplicateElementException e) {
				response = new CommandResponse(501, session.jprintf(_bundle,
						_keyPrefix+"slave.addmask.dupe", env, request.getUser()));
				return response;
			}
		} else if (command.equalsIgnoreCase("delmask")) {
			if (arguments.countTokens() != 1) {
				throw new ImproperUsageException();
			}

			String mask = arguments.nextToken();
			env.add("mask", mask);

			if (rslave.removeMask(mask)) {
				return new CommandResponse(200, session.jprintf(_bundle,
						_keyPrefix+"slave.delmask.success", env, request.getUser()));
			}
			return new CommandResponse(501, session.jprintf(_bundle,
					_keyPrefix+"slave.delmask.failed", env, request.getUser()));
		} else if (command.equalsIgnoreCase("shutdown")) {
			rslave.shutdown();
			return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		} else if (command.equalsIgnoreCase("queues")) {
			env.add("renamesize", rslave.getRenameQueue().size());
			env.add("remergesize", rslave.getRemergeQueue().size());
			env.add("remergecrcsize", rslave.getCRCQueue().size());
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"slave.queues", env, request.getUser()));
			return response;
		}
		throw new ImproperUsageException();
	}

	public CommandResponse doSITE_DELSLAVE(CommandRequest request) throws ImproperUsageException {
		Session session = request.getSession();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String argument = request.getArgument();
		StringTokenizer arguments = new StringTokenizer(argument);

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String slavename = arguments.nextToken();
		env.add("slavename", slavename);

		try {
			GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
		} catch (ObjectNotFoundException e) {
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"delslave.notfound", env, request.getUser()));

			return response;
		}

		GlobalContext.getGlobalContext().getSlaveManager().delSlave(slavename);
		response.addComment(session.jprintf(_bundle,
				_keyPrefix+"delslave.success", env, request.getUser()));

		return response;
	}

	public CommandResponse doSITE_ADDSLAVE(CommandRequest request) throws ImproperUsageException {
		Session session = request.getSession();

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		StringTokenizer arguments = new StringTokenizer(request.getArgument());

		if (!arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}

		String slavename = arguments.nextToken();
		env.add("slavename", slavename);

		if (arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
			// only one argument
		}

		try {
			GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);

			return new CommandResponse(501,
					session.jprintf(_bundle, _keyPrefix+"addslave.exists", request.getUser()));
		} catch (ObjectNotFoundException e) {
		}

		GlobalContext.getGlobalContext().getSlaveManager().newSlave(slavename);
		response.addComment(session.jprintf(_bundle,
				_keyPrefix+"addslave.success", env, request.getUser()));

		return response;
	}

	public static void fillEnvWithSlaveStatus(ReplacerEnvironment env, SlaveStatus status) {
		env.add("disktotal", Bytes.formatBytes(status.getDiskSpaceCapacity()));
		env.add("diskfree", Bytes.formatBytes(status.getDiskSpaceAvailable()));
		env.add("diskused", Bytes.formatBytes(status.getDiskSpaceUsed()));
		try {
			env.add("slavesonline",""+GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves().size());
		} catch (NoAvailableSlaveException e) {
			env.add("slavesonline","0");
		}
		env.add("slavestotal", ""+GlobalContext.getGlobalContext().getSlaveManager().getSlaves().size());

		if (status.getDiskSpaceCapacity() == 0) {
			env.add("diskfreepercent", "n/a");
			env.add("diskusedpercent", "n/a");
		} else {
			env.add("diskfreepercent", ((status.getDiskSpaceAvailable() * 100) / status.getDiskSpaceCapacity()) +"%");
			env.add("diskusedpercent", ((status.getDiskSpaceUsed() * 100) / status.getDiskSpaceCapacity()) +"%");
		}

		env.add("xfers", "" + status.getTransfers());
		env.add("xfersdn", "" + status.getTransfersSending());
		env.add("xfersup", "" + status.getTransfersReceiving());
		env.add("xfersdown", "" + status.getTransfersSending());

		env.add("throughput", Bytes.formatBytes(status.getThroughput()) + "/s");
		env.add("throughputup", Bytes.formatBytes(status.getThroughputReceiving()) + "/s");
		env.add("throughputdown", Bytes.formatBytes(status.getThroughputSending()) + "/s");
	}

	public CommandResponse doDiskfree(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		SlaveStatus status = GlobalContext.getGlobalContext().getSlaveManager().getAllStatus();
		fillEnvWithSlaveStatus(env, status);
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix + "diskfree", env, request.getUser()));
		return response;
	}


	public CommandResponse doRemergequeue(CommandRequest request) throws ImproperUsageException {
        String slavestofind = GlobalContext.getConfig().getMainProperties().getProperty("default.slave.output");
        String slave = "all";
        if(request.hasArgument()){
            slave=request.getArgument().toLowerCase();
        } else if (slavestofind != null)
        {
            slave = slavestofind;
        }

		ArrayList<String> arr = new ArrayList<>();

		for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
			String name=rslave.getName().toLowerCase();

			if((!name.startsWith(slave))&&(!slave.equals("all"))) {
				continue;
			}

			int renameSize = rslave.getRenameQueue().size();
			int remergeSize = rslave.getRemergeQueue().size();
			int remergeCRCSize = rslave.getCRCQueue().size();
			if (!rslave.isOnline()) {
				arr.add(rslave.getName() +" is offline");
			}
			else if (!rslave.isRemerging()) {
				arr.add(rslave.getName() +" is online and not remerging");
			}
			else if (renameSize > 0 || remergeSize > 0 || remergeCRCSize > 0) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("slavename", rslave.getName());
				env.add("renamesize", rslave.getRenameQueue().size());
				env.add("remergesize", rslave.getRemergeQueue().size());
				env.add("remergecrcsize", rslave.getCRCQueue().size());
				arr.add((request.getSession().jprintf(_bundle,
						_keyPrefix+"slave.queues", env, request.getUser())));
			}
			else {
				arr.add(rslave.getName() +" remergequeue size is 0 but remerge is ongoing");
			}
		}
		arr.add("Total commit:" + CommitManager.getCommitManager().getQueueSize());

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		for (String str : arr) {
			response.addComment(str);
		}

		return response;
	}

}

