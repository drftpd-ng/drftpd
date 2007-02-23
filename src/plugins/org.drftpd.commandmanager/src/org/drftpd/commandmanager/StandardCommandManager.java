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
package org.drftpd.commandmanager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.vfs.DirectoryHandle;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author djb61
 * @version $Id$
 */
public class StandardCommandManager implements CommandManagerInterface {

	private static final Logger logger = Logger
			.getLogger(StandardCommandManager.class);

	private static Hashtable<String,CommandResponse> _genericResponses;

	private HashMap<String, Object[]> _commands;

	public void initialize(ArrayList<String> requiredCmds) {
		initGenericResponses();

		_commands = new HashMap<String, Object[]>();
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint cmdExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"org.drftpd.commandmanager", "Command");
		
		/* Iterate over the available extensions connected to this extension
		 * point and build a hashtable which the next section can use to retrieve
		 * an extension based on its declaring plugin name.
		 */
		Hashtable<String,Extension> extensions = new Hashtable<String,Extension>();
		for (Iterator commands = cmdExtPoint.getConnectedExtensions().iterator();
		commands.hasNext();) { 

			Extension cmd = (Extension) commands.next();

			String pluginId = cmd.getDeclaringPluginDescriptor().getId();
			extensions.put(pluginId,cmd);
		}
		/*	Iterate over the ArrayList of commands that the calling frontend
		 * 	has stated it needs. Check to see whether we have a valid Command
		 * 	extension attached for the command, is so add it to the commands
		 * 	map to be used
		 */
		for (String requiredCmd : requiredCmds) {
			int methodIndex = requiredCmd.lastIndexOf(".");
			int classIndex = requiredCmd.lastIndexOf(".", methodIndex-1);
			String methodString = requiredCmd.substring(methodIndex+1);
			String classString = requiredCmd.substring(classIndex+1, methodIndex);
			String pluginString = requiredCmd.substring(0, classIndex);

			if(!extensions.containsKey(pluginString)) {
				logger.info("Command plugin "+pluginString+" not found");
				continue;
			}
			Extension cmd = extensions.get(pluginString);
			//	If plugin isn't already activated then activate it
			if (!manager.isPluginActivated(cmd.getDeclaringPluginDescriptor())) {
				try {
					manager.activatePlugin(pluginString);
				}
				catch (PluginLifecycleException e) {
					// Not overly concerned about this
				}
			}
			ClassLoader cmdLoader = manager.getPluginClassLoader( 
					cmd.getDeclaringPluginDescriptor());
			try {
				Class cmdCls = cmdLoader.loadClass(pluginString+"."+classString);
				CommandInterface cmdInstance = (CommandInterface) cmdCls.newInstance();
				cmdInstance.initialize(methodString, pluginString);

				Method m = cmdInstance.getClass().getMethod(methodString,
						new Class[] {CommandRequest.class});
				_commands.put(requiredCmd,new Object[] {m,cmdInstance});
			}
			catch(Exception e) {
				/* Should be safe to continue, just means this command class won't be
				 * available
				 */
				logger.info("Failed to add command handler: "+requiredCmd);
			}
		}
	}

	public CommandResponseInterface execute(CommandRequestInterface request) {
		Object[] cmdHandler = _commands.get(request.getCommand());
		if (cmdHandler == null) {
			CommandResponseInterface cmdFailed = genericResponse("RESPONSE_502_COMMAND_NOT_IMPLEMENTED");
			cmdFailed.setUser(request.getUser());
			cmdFailed.setCurrentDirectory(request.getCurrentDirectory());
			return cmdFailed;
		}
		Method m = (Method) cmdHandler[0];
		try {
			return (CommandResponseInterface) m.invoke(cmdHandler[1], new Object[] {request});
		}
		catch (Exception e) {
			CommandResponseInterface cmdFailed = new CommandResponse(540, "Command execution failed");
			cmdFailed.setUser(request.getUser());
			cmdFailed.setCurrentDirectory(request.getCurrentDirectory());
			logger.error("Command "+request.getCommand()+" failed",e);
			return cmdFailed;
		}
	}

	public static CommandResponse genericResponse(String type) {
		try {
			return (CommandResponse) _genericResponses.get(type).clone();
		}
		catch (NullPointerException e) {
			logger.error("An unknown generic FTP response was used: "+type+
					" this is almost certainly a bug");
			return new CommandResponse(540, "No response message defined");
		}
	}

	public static CommandResponse genericResponse(String type, DirectoryHandle directory,
			String user) {
		CommandResponse response = null;
		response = genericResponse(type);
		response.setCurrentDirectory(directory);
		response.setUser(user);
		return response;
	}

	private static synchronized void initGenericResponses() {
		if (_genericResponses != null) {
			return;
		}
		
		Hashtable<String,CommandResponse> genericResponses = 
			new Hashtable<String,CommandResponse>();

		/** 150 File status okay; about to open data connection. */
		genericResponses.put("RESPONSE_150_OK",
				new CommandResponse(150,"File status okay; about to open data connection.\r\n"));

		/** 200 Command okay */
		genericResponses.put("RESPONSE_200_COMMAND_OK",
				new CommandResponse(200,"Command okay"));

		/** 202 Command not implemented, superfluous at this site. */
		genericResponses.put("RESPONSE_202_COMMAND_NOT_IMPLEMENTED",
				new CommandResponse(202, "Command not implemented, superfluous at this site."));

		/** 215 NAME system type. */
		genericResponses.put("RESPONSE_215_SYSTEM_TYPE",
				new CommandResponse(215,"UNIX system type."));

		/** 221 Service closing control connection. */
		genericResponses.put("RESPONSE_221_SERVICE_CLOSING",
				new CommandResponse(221,"Service closing control connection."));

		/** 226 Closing data connection */
		genericResponses.put("RESPONSE_226_CLOSING_DATA_CONNECTION",
				new CommandResponse(226, "Closing data connection"));

		/** 230 User logged in, proceed. */
		genericResponses.put("RESPONSE_230_USER_LOGGED_IN",
				new CommandResponse(230,"User logged in, proceed."));

		/** 250 Requested file action okay, completed. */
		genericResponses.put("RESPONSE_250_ACTION_OKAY",
				new CommandResponse(250,"Requested file action okay, completed."));

		/** 331 User name okay, need password. */
		genericResponses.put("RESPONSE_331_USERNAME_OK_NEED_PASS",
				new CommandResponse(331, "User name okay, need password."));

		/** 350 Requested file action pending further information. */
		genericResponses.put("RESPONSE_350_PENDING_FURTHER_INFORMATION",
				new CommandResponse(350, "Requested file action pending further information."));

		/** 425 Can't open data connection. */
		genericResponses.put("RESPONSE_425_CANT_OPEN_DATA_CONNECTION",
				new CommandResponse(425, "Can't open data connection.\r\n"));

		/** 426 Connection closed; transfer aborted. */
		genericResponses.put("RESPONSE_426_CONNECTION_CLOSED_TRANSFER_ABORTED",
				new CommandResponse(426, "Connection closed; transfer aborted."));

		/** 450 Requested file action not taken. */
		genericResponses.put("RESPONSE_450_REQUESTED_ACTION_NOT_TAKEN",
				new CommandResponse(450, "Requested file action not taken."));

		/**
		 * 450 No transfer-slave(s) available author <a
		 * href="mailto:drftpd@mog.se">Morgan Christiansson</a>
		 */
		genericResponses.put("RESPONSE_450_SLAVE_UNAVAILABLE",
				new CommandResponse(450,"No transfer-slave(s) available"));

		/** 500 Syntax error, command unrecognized. */
		genericResponses.put("RESPONSE_500_SYNTAX_ERROR",
				new CommandResponse(500,"Syntax error, command unrecognized."));

		/** 501 Syntax error in parameters or arguments */
		genericResponses.put("RESPONSE_501_SYNTAX_ERROR",
				new CommandResponse(501,"Syntax error in parameters or arguments"));

		/** 502 Command not implemented. */
		genericResponses.put("RESPONSE_502_COMMAND_NOT_IMPLEMENTED",
				new CommandResponse(502, "Command not implemented."));

		/** 503 Bad sequence of commands. */
		genericResponses.put("RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS",
				new CommandResponse(503, "Bad sequence of commands."));

		/** 504 Command not implemented for that parameter. */
		genericResponses.put("RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM",
				new CommandResponse(504, "Command not implemented for that parameter."));

		/** 530 Access denied */
		genericResponses.put("RESPONSE_530_ACCESS_DENIED",
				new CommandResponse(530,"Access denied"));

		/** 530 Not logged in. */
		genericResponses.put("RESPONSE_530_NOT_LOGGED_IN",
				new CommandResponse(530,"Not logged in."));

		genericResponses.put("RESPONSE_530_SLAVE_UNAVAILABLE",
				new CommandResponse(530,"No transfer-slave(s) available"));

		/**
		 * 550 Requested action not taken. File unavailable. File unavailable (e.g.,
		 * file not found, no access).
		 */
		genericResponses.put("RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN",
				new CommandResponse(550, "Requested action not taken. File unavailable (e.g., file not found, no access"));

		/**
		 * 553 Requested action not taken. File name not allowed.
		 */
		genericResponses.put("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN",
				new CommandResponse(553, "Requested action not taken.  File name not allowed"));

		/**
		 * 550 Requested action not taken. File exists.
		 */
		genericResponses.put("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS",
				new CommandResponse(550, "Requested action not taken. File exists."));

		_genericResponses = genericResponses;
	}

	public CommandRequestInterface newRequest(String argument,
			String command, DirectoryHandle directory, String user) {
		return new CommandRequest(argument, command, directory, user);
	}

	public CommandRequestInterface newRequest(String argument, String command,
			DirectoryHandle directory, String user, BaseFtpConnection connection, String originalCommand) {
		return new CommandRequest(argument, command, directory, user, connection, originalCommand);
	}
}
