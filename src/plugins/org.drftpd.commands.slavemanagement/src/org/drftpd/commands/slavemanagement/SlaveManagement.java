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

import java.util.Collection;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.master.SlaveManager;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.slave.Transfer;
import org.drftpd.slaveselection.filter.Filter;
import org.drftpd.slaveselection.filter.ScoreChart;
import org.drftpd.slaveselection.filter.SlaveSelectionManager;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

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
		if (arguments.hasMoreTokens() == false) {
			throw new ImproperUsageException();
		}
		String type = arguments.nextToken();
		if (arguments.hasMoreTokens() == false) {
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

		int slavesFound = 0;
		for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
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
		boolean showMore = false;
		StringTokenizer arguments = new StringTokenizer(request.getArgument());
		String slaveName = arguments.nextToken();
		if (arguments.hasMoreTokens()) {
			if (arguments.nextToken().equalsIgnoreCase("more")) {
				showMore = true;
			} else {
				throw new ImproperUsageException();
			}
		}
		if (arguments.hasMoreTokens()) {
			throw new ImproperUsageException();
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		try {
			RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
			response = addSlaveStatus(request, response, showMore, rslave);
		} catch (ObjectNotFoundException e) {
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("slavename", slaveName);
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"slave.notfound", env, request.getUser()));
		}
		return response;
	}

	private CommandResponse addSlaveStatus(CommandRequest request, CommandResponse response, boolean showMore, RemoteSlave rslave) {
		Session session = request.getSession();
		if (showMore) {
			response.addComment(rslave.moreInfo());
		}

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("slavename", rslave.getName());

		if (rslave.isOnline()) {
			if (!rslave.isAvailable()) {
				response.addComment(session.jprintf(_bundle, _keyPrefix+"slave.remerging", env, request.getUser()));
			} else {            		
				try {
					SlaveStatus status = rslave.getSlaveStatus();
					fillEnvWithSlaveStatus(env, status);
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

		try { 
			rslave.fetchResponse(SlaveManager.getBasicIssuer().issueRemergeToSlave(rslave, 
					request.getCurrentDirectory().getPath(), false, 0L, 0L), 0); 
		} catch (RemoteIOException e) { 
			rslave.setOffline("IOException during remerge()"); 


			return new CommandResponse(200, "IOException during remerge()");
		} catch (SlaveUnavailableException e) {
			rslave.setOffline("Slave Unavailable during remerge()");

			return new CommandResponse(200, "Slave Unavailable during remerge()");
		}

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
		env.add("throughputup",	Bytes.formatBytes(status.getThroughputReceiving()) + "/s");
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
		response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"diskfree", env, request.getUser()));
		return response;
	}
}
