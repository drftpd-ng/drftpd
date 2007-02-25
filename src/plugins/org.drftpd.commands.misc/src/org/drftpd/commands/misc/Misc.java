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
package org.drftpd.commands.misc;

import java.io.PrintWriter;
import java.util.Date;

import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.CommandWrapper;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.slave.Slave;


/**
 * @version $Id$
 */
public class Misc extends CommandInterface {

	private StandardCommandManager _cManager;
	
	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_cManager = cManager;
    }

    /**
     * <code>ABOR &lt;CRLF&gt;</code><br>
     *
     * This command tells the server to abort the previous FTP
     * service command and any associated transfer of data.
     * No action is to be taken if the previous command
     * has been completed (including data transfer).  The control
     * connection is not to be closed by the server, but the data
     * connection must be closed.
     * Current implementation does not do anything. As here data
     * transfers are not multi-threaded.
     */
    public CommandResponse doABOR(CommandRequest request) {
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
    	response = StandardCommandManager.genericResponse("RESPONSE_226_CLOSING_DATA_CONNECTION");
    	doPostHooks(request, response);
        return response;
    }

    // LIST;NLST;RETR;STOR
    public CommandResponse doFEAT(CommandRequest request) {
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
        PrintWriter out = request.getConnection().getControlWriter();
        out.print("211-Extensions supported:\r\n");

        for (CommandWrapper wrapper : _cManager.getCommandHandlersMap().values()) {
        	CommandInterface hnd = wrapper.getCommandInterface();
        	String[] feat = hnd.getFeatReplies();  
        	if (feat == null) {
        		continue;  
        	}

        	for (int i = 0; i < feat.length; i++) {
                out.print(" " + feat[i] + "\r\n");
            }
        }

        out.print("211 End\r\n");

        doPostHooks(request, null);
        return null;
    }

    /**
     * <code>HELP [&lt;SP&gt; <string>] &lt;CRLF&gt;</code><br>
     *
     * This command shall cause the server to send helpful
     * information regarding its implementation status over the
     * control connection to the user.  The command may take an
     * argument (e.g., any command name) and return more specific
     * information as a response.
     */

    //TODO implement HELP, SITE HELP would be good too.
    //	public FtpReply doHELP(BaseFtpConnection conn) {
    //
    // print global help
    //		if (!request.hasArgument()) {
    //		FtpReply response = new FtpReply(214);
    //		response.addComment("The following commands are recognized.");
    //out.write(ftpStatus.getResponse(214, null, user, null));
    //		Method methods[] = this.getClass().getDeclaredMethods();
    //		for (int i = 0; i < methods.length; i++) {
    //			Method method = methods[i];
    //			Class parameterTypes[] = method.getParameterTypes();
    //			if (parameterTypes.length == 2
    //				&& parameterTypes[0] == FtpRequest.class
    //				&& parameterTypes[1] == PrintWriter.class) {
    //				String commandName =
    //					method.getName().substring(2).replace('_', ' ');
    //				response.addComment(commandName);
    //			}
    //		}
    //		out.print(response);
    //		return;
    //		}
    //
    //		// print command specific help
    //		String ftpCmd = request.getArgument().toUpperCase();
    //		String args[] = null;
    //		FtpRequest tempRequest = new FtpRequest(ftpCmd);
    //		out.write(ftpStatus.getResponse(214, tempRequest, user, args));
    //		return;
    //	}
    public CommandResponse doSITE_STAT(CommandRequest request) {
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
        if (request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM");
        	doPostHooks(request, response);
            return response;
        }

        response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        /* TODO maybe think of another way of doing this so as to not pull in
         * BaseFtpConnection
         */
        BaseFtpConnection conn = request.getConnection();
        response.addComment(conn.status());

        doPostHooks(request, response);
        return response;
    }

    public CommandResponse doSITE_TIME(CommandRequest request) {
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
    	if (request.hasArgument()) {
        	response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        	doPostHooks(request, response);
            return response;
        }

    	response = new CommandResponse(200, "Server time is: " + new Date());
        doPostHooks(request, response);
        return response;
    }

    /*private Reply doSITE_HELP(BaseFtpConnection conn) throws ReplyException {
    	Map handlers = conn.getCommandManager().getCommandHandlersMap();
    	if (conn.getRequest().hasArgument()) {
    		String cmd = conn.getRequest().getArgument().toLowerCase();
    		for (Iterator iter = handlers.keySet().iterator(); iter.hasNext();) {
    			CommandHandler hnd = (CommandHandler) handlers.get(iter.next());
    			if (conn.getCommandManager().getHandledCommands(hnd.getClass())
    					.contains("SITE " + cmd.toUpperCase())) {
    				if (!conn.getGlobalContext().getConfig()
    						.checkPathPermission(cmd, conn.getUserNull(),
    								conn.getCurrentDirectory(), true)) {
    					return new Reply(501,
								"You do not have permissions for that command");
    				}
    				try {
    					Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
    					return response.addComment(ResourceBundle.getBundle(
    							hnd.getClass().getName()).getString(
    									"help." + cmd + ".specific"));
    				} catch (MissingResourceException e) {
    					throw new ReplyException(cmd + " in "
    							+ hnd.getClass().getName()
								+ " does not have any specific help, bug your siteop", e);
    				}
    			}
    		}
    		throw new ReplyException("the " + cmd + " command does not exist");
    	}
    	// global list of commands with help
    	HashMap<String, String> helpInfo = new HashMap<String, String>();
    	String pad = "            ";
    	for (Iterator iter = handlers.keySet().iterator(); iter.hasNext();) {
    		CommandHandler hnd = (CommandHandler) handlers.get(iter.next());
    		List<String> handledCmds = conn.getCommandManager()
			.getHandledCommands(hnd.getClass());
    		
    		for (String cmd : handledCmds) {
    			try {
    				cmd = cmd.substring("SITE ".length()).toLowerCase();
    				if (!conn.getGlobalContext().getConfig()
    						.checkPathPermission(cmd, conn.getUserNull(),
    								conn.getCurrentDirectory(), true)) {
    					continue;
    				}
    				try {
    					String help = ResourceBundle.getBundle(
    							hnd.getClass().getName()).getString(
    									"help." + cmd);
    					helpInfo.put(cmd, pad.substring(cmd.length()) + cmd.toUpperCase() + " : " + help);
    				} catch (MissingResourceException e) {
    					helpInfo.put(cmd, cmd + " in "
								+ hnd.getClass().getName()
								+ " does not have any help, bug your siteop");
    				}
    			} catch (java.lang.StringIndexOutOfBoundsException e) {
    			}
    		}
    	}
    	ArrayList<String> sortedList = new ArrayList<String>(helpInfo.keySet());
    	Collections.sort(sortedList);
    	Reply response = (Reply) Reply.RESPONSE_200_COMMAND_OK.clone();
    	try {
    		response.addComment(ResourceBundle.getBundle(Misc.class.getName())
    				.getString("help.header"));
    	} catch (MissingResourceException e) {
    		response.addComment("Help has no header");
    	}
    	for (Iterator i = sortedList.iterator(); i.hasNext();) {
    		response.addComment(helpInfo.get(i.next()));
    	}
    	try {
    		response.addComment(ResourceBundle.getBundle(Misc.class.getName())
    				.getString("help.footer"));
    	} catch (MissingResourceException e) {
    		response.addComment("Help has no footer");
    	}
    	return response;
    }*/

    public CommandResponse doSITE_VERS(CommandRequest request) {
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
    	response = new CommandResponse(200, Slave.VERSION);
        doPostHooks(request, response);
        return response;
    }

    public void unload() {
    }
}
