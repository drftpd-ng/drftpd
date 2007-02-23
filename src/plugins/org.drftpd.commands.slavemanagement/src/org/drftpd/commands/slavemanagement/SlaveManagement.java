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

    public CommandResponse doSITE_SLAVESELECT(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }
        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);
        if (arguments.hasMoreTokens() == false) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }
        String type = arguments.nextToken();
        if (arguments.hasMoreTokens() == false) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }
        String path = arguments.nextToken();
        if (!path.startsWith(VirtualFileSystem.separator)) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }
        char direction = Transfer.TRANSFER_UNKNOWN;
        if (type.equalsIgnoreCase("up")) {
        	direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
        } else if (type.equalsIgnoreCase("down")) {
        	direction = Transfer.TRANSFER_SENDING_DOWNLOAD;
        } else {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }
        Collection<RemoteSlave> slaves;
		try {
			slaves = GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves();
		} catch (NoAvailableSlaveException e1) {
			response = StandardCommandManager.genericResponse("RESPONSE_530_SLAVE_UNAVAILABLE");
        	doPostHooks(request, response);
            return response;
		}
        SlaveSelectionManager ssm = null;
        try {
        	ssm = (SlaveSelectionManager) GlobalContext.getGlobalContext().getSlaveSelectionManager();
        } catch (ClassCastException e) {
        	response = new CommandResponse(500,
        			"You are attempting to test filter.SlaveSelectionManager yet you're using def.SlaveSelectionManager");
            doPostHooks(request, response);
            return response;
        }
        response = new CommandResponse(500, "***End of SlaveSelection output***");
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
        doPostHooks(request, response);
        return response;
    }

    public CommandResponse doSITE_KICKSLAVE(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        RemoteSlave rslave;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
        } catch (ObjectNotFoundException e) {
        	response = new CommandResponse(200, "No such slave");
            doPostHooks(request, response);
            return response;
        }

        if (!rslave.isOnline()) {
        	response = new CommandResponse(200, "Slave is already offline");
            doPostHooks(request, response);
            return response;
        }

        rslave.setOffline("Slave kicked by " +
            getUserNull(request.getUser()).getName());

        response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    	doPostHooks(request, response);
        return response;
    }

    /**
     * Lists all slaves used by the master
     * USAGE: SITE SLAVES
     */
    public CommandResponse doSITE_SLAVES(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	boolean showMore = request.hasArgument() &&
            (request.getArgument().equalsIgnoreCase("more"));

        if (showMore && !getUserNull(request.getUser()).isAdmin()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        Collection slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
        response = new CommandResponse(200,
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

        doPostHooks(request, response);
        return response;
    }

    public CommandResponse doSITE_REMERGE(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	/* TODO reminder to consider whether this permissions check
    	 * would be better suited as a pre hook
    	 */
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        RemoteSlave rslave;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
        } catch (ObjectNotFoundException e) {
        	response = new CommandResponse(200, "No such slave");
            doPostHooks(request, response);
            return response;
        }

        if (!rslave.isAvailable()) {
        	response = new CommandResponse(200,
            	"Slave is still merging from initial connect");
            doPostHooks(request, response);
            return response;
        }

        try {
            rslave.fetchRemergeResponseFromIndex(rslave.issueRemergeToSlave(
                    request.getCurrentDirectory().getPath()));
        } catch (IOException e) {
            rslave.setOffline("IOException during remerge()");

            response = new CommandResponse(200, "IOException during remerge()");
            doPostHooks(request, response);
            return response;
        } catch (SlaveUnavailableException e) {
            rslave.setOffline("Slave Unavailable during remerge()");

            response = new CommandResponse(200, "Slave Unavailable during remerge()");
            doPostHooks(request, response);
            return response;
        }

        response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    	doPostHooks(request, response);
        return response;
    }

    /**
     * Usage: site slave slavename [set,addmask,delmask]
     * @throws ImproperUsageException
     */
    public CommandResponse doSITE_SLAVE(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        response = new CommandResponse(200);
        ReplacerEnvironment env = new ReplacerEnvironment();

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);

        RemoteSlave rslave = null;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(jprintf(_bundle,
                    "slave.notfound", env, request.getUser()));

            doPostHooks(request, response);
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

            doPostHooks(request, response);
            return response;
        }

        String command = arguments.nextToken();

        if (command.equalsIgnoreCase("set")) {
            if (arguments.countTokens() != 2) {
            	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            	doPostHooks(request, response);
                return response;
            }

            String key = arguments.nextToken();
            String value = arguments.nextToken();
            rslave.setProperty(key, value);
            env.add("key", key);
            env.add("value", value);
            response.addComment(jprintf(_bundle,
                    "slave.set.success", env, request.getUser()));

            doPostHooks(request, response);
            return response;
        } else if (command.equalsIgnoreCase("unset")) {
        	if (arguments.countTokens() != 1) {
        		response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            	doPostHooks(request, response);
                return response;
        	}

        	String key = arguments.nextToken();
        	env.add("key", key);
        	String value;
        	try {
        		value = rslave.removeProperty(key);
        	} catch (KeyNotFoundException e) {
        		response.addComment(jprintf(_bundle,
        				"slave.unset.failure", env, request.getUser()));
        		doPostHooks(request, response);
        		return response;
        	}
        	env.add("value", value);
        	response.addComment(jprintf(_bundle,
        			"slave.unset.success", env, request.getUser()));
        	doPostHooks(request, response);
        	return response;
        } else if (command.equalsIgnoreCase("addmask")) {
            if (arguments.countTokens() != 1) {
            	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            	doPostHooks(request, response);
                return response;
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);
            try {
				rslave.addMask(mask);
				response.addComment(jprintf(_bundle,
						"slave.addmask.success", env, request.getUser()));
				doPostHooks(request, response);
				return response;
			} catch (DuplicateElementException e) {
				response = new CommandResponse(501, jprintf(_bundle,
						"slave.addmask.dupe", env, request.getUser()));
	            doPostHooks(request, response);
	            return response;
			}
        } else if (command.equalsIgnoreCase("delmask")) {
            if (arguments.countTokens() != 1) {
            	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            	doPostHooks(request, response);
                return response;
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);

            if (rslave.removeMask(mask)) {
            	response = new CommandResponse(200, jprintf(_bundle,
                        "slave.delmask.success", env, request.getUser()));
                doPostHooks(request, response);
                return response;
            }
            response = new CommandResponse(501, jprintf(_bundle,
                    "slave.delmask.failed", env, request.getUser()));
            doPostHooks(request, response);
            return response;
        }
        response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
    	doPostHooks(request, response);
        return response;
    }

    public CommandResponse doSITE_DELSLAVE(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        response = new CommandResponse(200);
        ReplacerEnvironment env = new ReplacerEnvironment();

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);

        try {
            GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(jprintf(_bundle,
                    "delslave.notfound", env, request.getUser()));

            doPostHooks(request, response);
            return response;
        }

        GlobalContext.getGlobalContext().getSlaveManager().delSlave(slavename);
        response.addComment(jprintf(_bundle,
                "delslave.success", env, request.getUser()));

        doPostHooks(request, response);
        return response;
    }

    public CommandResponse doSITE_ADDSLAVE(CommandRequest request) {
    	CommandResponse response;
    	request = doPreHooks(request);
    	if(!request.isAllowed()) {
    		response = request.getDeniedResponse();
    		if (response == null) {
    			response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
    		}
    		doPostHooks(request, response);
    		return response;
    	}
    	if (!getUserNull(request.getUser()).isAdmin()) {
    		response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
        	doPostHooks(request, response);
            return response;
        }

        response = new CommandResponse(200);
        ReplacerEnvironment env = new ReplacerEnvironment();

        if (!request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        StringTokenizer arguments = new StringTokenizer(request.getArgument());

        if (!arguments.hasMoreTokens()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);
        
        if (arguments.hasMoreTokens()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        	// only one argument
        }

        try {
            GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);

            response = new CommandResponse(501,
                    jprintf(_bundle, "addslave.exists", request.getUser()));
            doPostHooks(request, response);
            return response;
        } catch (ObjectNotFoundException e) {
        }

        GlobalContext.getGlobalContext().getSlaveManager().newSlave(slavename);
        response.addComment(jprintf(_bundle,
                "addslave.success", env, request.getUser()));

        doPostHooks(request, response);
        return response;
    }

}
