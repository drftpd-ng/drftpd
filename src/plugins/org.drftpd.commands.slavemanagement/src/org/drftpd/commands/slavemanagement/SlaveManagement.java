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

import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.StringTokenizer;


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

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = ResourceBundle.getBundle(this.getClass().getName());
    }

    public CommandResponse doSITE_SLAVESELECT(CommandRequest request) throws ImproperUsageException {
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

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
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

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
            getUserNull(request.getUser()).getName());

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    /**
     * Lists all slaves used by the master
     * USAGE: SITE SLAVES
     */
    public CommandResponse doSITE_SLAVES(CommandRequest request) {
    	boolean showMore = request.hasArgument() &&
            (request.getArgument().equalsIgnoreCase("more"));

        if (showMore && !getUserNull(request.getUser()).isAdmin()) {
        	return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        Collection slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
        CommandResponse response = new CommandResponse(200,
                "OK, " + slaves.size() + " slaves listed.");

        for (Iterator iter = GlobalContext.getGlobalContext().getSlaveManager()
                                 .getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (showMore) {
                response.addComment(rslave.moreInfo());
            }

            ReplacerEnvironment env = new ReplacerEnvironment();
            env.add("slave", rslave.getName());

            try {
                SlaveStatus status = rslave.getSlaveStatusAvailable();
                // what the hell is this doing here?!?
                /*SiteBot.fillEnvSlaveStatus(env, status,
                    conn.getGlobalContext().getSlaveManager());*/
                
                response.addComment(jprintf(_bundle,
                        "slaves", env, request.getUser()));
            } catch (SlaveUnavailableException e) {
                response.addComment(jprintf(_bundle,
                        "slaves.offline", env, request.getUser()));
            }
        }

        return response;
    }

    public CommandResponse doSITE_REMERGE(CommandRequest request) {
    	/* TODO reminder to consider whether this permissions check
    	 * would be better suited as a pre hook
    	 */
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

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
            rslave.fetchRemergeResponseFromIndex(rslave.issueRemergeToSlave(
                    request.getCurrentDirectory().getPath()));
        } catch (IOException e) {
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
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        CommandResponse response = new CommandResponse(200);
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
            response.addComment(jprintf(_bundle,
                    "slave.notfound", env, request.getUser()));

            return response;
        }

        if (!arguments.hasMoreTokens()) {
            if (!rslave.getMasks().isEmpty()) {
                env.add("masks", rslave.getMasks());
                response.addComment(jprintf(_bundle,
                        "slave.masks", env, request.getUser()));
            }

            response.addComment(jprintf(_bundle,
                    "slave.data.header", request.getUser()));

            Map props = rslave.getProperties();

            for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                Object value = props.get(key);
                env.add("key", key);
                env.add("value", value);
                response.addComment(jprintf(_bundle,
                        "slave.data", env, request.getUser()));
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
            response.addComment(jprintf(_bundle,
                    "slave.set.success", env, request.getUser()));

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
        		response.addComment(jprintf(_bundle,
        				"slave.unset.failure", env, request.getUser()));
        		return response;
        	}
        	env.add("value", value);
        	response.addComment(jprintf(_bundle,
        			"slave.unset.success", env, request.getUser()));
        	return response;
        } else if (command.equalsIgnoreCase("addmask")) {
            if (arguments.countTokens() != 1) {
            	throw new ImproperUsageException();
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);
            try {
				rslave.addMask(mask);
				response.addComment(jprintf(_bundle,
						"slave.addmask.success", env, request.getUser()));
				return response;
			} catch (DuplicateElementException e) {
				response = new CommandResponse(501, jprintf(_bundle,
						"slave.addmask.dupe", env, request.getUser()));
	            return response;
			}
        } else if (command.equalsIgnoreCase("delmask")) {
            if (arguments.countTokens() != 1) {
            	throw new ImproperUsageException();
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);

            if (rslave.removeMask(mask)) {
            	return new CommandResponse(200, jprintf(_bundle,
                        "slave.delmask.success", env, request.getUser()));
            }
            return new CommandResponse(501, jprintf(_bundle,
                    "slave.delmask.failed", env, request.getUser()));
        }
        throw new ImproperUsageException();
    }

    public CommandResponse doSITE_DELSLAVE(CommandRequest request) throws ImproperUsageException {
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        CommandResponse response = new CommandResponse(200);
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
            response.addComment(jprintf(_bundle,
                    "delslave.notfound", env, request.getUser()));

            return response;
        }

        GlobalContext.getGlobalContext().getSlaveManager().delSlave(slavename);
        response.addComment(jprintf(_bundle,
                "delslave.success", env, request.getUser()));

        return response;
    }

    public CommandResponse doSITE_ADDSLAVE(CommandRequest request) throws ImproperUsageException {
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        }

        CommandResponse response = new CommandResponse(200);
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
                    jprintf(_bundle, "addslave.exists", request.getUser()));
         } catch (ObjectNotFoundException e) {
        }

        GlobalContext.getGlobalContext().getSlaveManager().newSlave(slavename);
        response.addComment(jprintf(_bundle,
                "addslave.success", env, request.getUser()));

        return response;
    }

}
