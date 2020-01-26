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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.FatalException;
import org.drftpd.master.Session;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.ExtendedPropertyResourceBundle;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * @author djb61
 * @version $Id$
 */
public class StandardCommandManager implements CommandManagerInterface {

	private static final Logger logger = LogManager.getLogger(StandardCommandManager.class);

	private static final String _defaultThemeDir = "conf/themes/ftp";
	private static HashMap<String,CommandResponse> _genericResponses = initGenericResponses();

	private ExtendedPropertyResourceBundle _defaultTheme;

	private ExtendedPropertyResourceBundle _theme;

	private ExtendedPropertyResourceBundle _fallbackTheme;

	/**
	 * This is a map of commands, e.x.:
	 * "AUTH" -> CommandInstanceContainer (Instance of the CommandInterface and the appropriately attached Method)
	 */
	private Map<String, CommandInstanceContainer> _commands;

	public StandardCommandManager() {
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public synchronized void initialize(HashMap<String,Properties> requiredCmds, String themeDir) {
		loadThemes(themeDir);

		HashMap<String,CommandInstanceContainer> commands = new HashMap<>();

		/*	Iterate over the ArrayList of commands that the calling frontend
		 * 	has stated it needs. Check to see whether we have a valid Command
		 * 	extension attached for the command, is so add it to the commands
		 * 	map to be used
		 */
		for (Entry<String,Properties> requiredCmd : requiredCmds.entrySet()) {
			String methodString = requiredCmd.getValue().getProperty("method");
			String classString = requiredCmd.getValue().getProperty("class");
			String pluginString = requiredCmd.getValue().getProperty("plugin");
			if (methodString == null || classString == null
					|| pluginString == null) {
				throw new FatalException(
						"Cannot load command "
						+ requiredCmd.getKey()
						+ ", make sure method, class, and plugin are all specified");
			}

			try {
				PluginObjectContainer<CommandInterface> container =
					CommonPluginUtils.getSinglePluginObjectInContainer(this, "org.drftpd.commandmanager", "Command",
							pluginString+"."+classString, methodString, pluginString, new Class[] { CommandRequest.class });
				CommandInterface cmdInstance = container.getPluginObject();
				cmdInstance.initialize(methodString, pluginString, this);
				commands.put(requiredCmd.getKey(),new CommandInstanceContainer(container.getPluginMethod(),cmdInstance));
                logger.debug("Adding CommandInstance {}", requiredCmd.getKey());
			} catch(Exception e) {
				/* Should be safe to continue, just means this command class won't be
				 * available
				 */
                logger.info("Failed to add command handler: {}", requiredCmd, e);
			}
		}
		_commands = commands;
	}

	private void loadThemes(String themeDir) {
		// Load the default theme for the frontend as well as any user overrides
		FileInputStream defaultIs = null;
		try {
			defaultIs = new FileInputStream(new File(themeDir+File.separator+"core.theme.default"));
			_defaultTheme = new ExtendedPropertyResourceBundle(defaultIs);
		} catch (FileNotFoundException e) {
            logger.error("No default theme file core.theme.default found in: {}", themeDir, e);
		} catch (IOException e) {
            logger.error("Error loading core.theme.default from: {}", themeDir, e);
		} finally {
			try {
				if (defaultIs != null) {
					defaultIs.close();
				}
			} catch (IOException e) {
				// FileInputStream already closed
			}
		}
		FileInputStream userIs = null;
		try {
			userIs = new FileInputStream(new File(themeDir+File.separator+"core.theme"));
			_theme = new ExtendedPropertyResourceBundle(userIs);
		} catch (FileNotFoundException e) {
			// Means the user hasn't specified any overrides, since we can't have an
			// empty bundle, just point this to the default
			_theme = _defaultTheme;
		} catch (IOException e) {
			// Means the user did specify overrides but they can't be loaded, point
			// to the default to keep things functional but log a warning to make
			// the user aware of the problem
			_theme = _defaultTheme;
            logger.warn("Error loading core.theme from: {}", themeDir, e);
		} finally {
			try {
				if (userIs != null) {
					userIs.close();
				}
			} catch (IOException e) {
				// FileInputStream already closed
			}
		}
		// Set the parent bundle to the default to allow access to any non-overriden values
		// if there is an override file
		if (!_theme.equals(_defaultTheme)) {
			_theme.setParent(_defaultTheme);
		}
		// If this isn't the base ftp frontend then add the default ftp theme as a final
		// fallback theme
		if (!themeDir.equals(_defaultThemeDir)) {
			FileInputStream fallbackIs = null;
			try {
				fallbackIs = new FileInputStream(new File(_defaultThemeDir+File.separator+"core.theme.default"));
				_fallbackTheme = new ExtendedPropertyResourceBundle(fallbackIs);
			} catch (FileNotFoundException e) {
                logger.error("Base ftp default theme not found: " + _defaultThemeDir + "{}core.theme.default", File.separator, e);
			} catch (IOException e) {
                logger.error("Error loading base ftp default theme: " + _defaultThemeDir + "{}core.theme.default", File.separator, e);
			} finally {
				if (fallbackIs != null) {
					try {
						fallbackIs.close();
					} catch (IOException e) {
						logger.debug("could not close fallbackIs",e);
					}
				}
			}
			_defaultTheme.setParent(_fallbackTheme);
		}
	}

	public CommandResponseInterface execute(CommandRequestInterface request) {
		CommandInstanceContainer commandContainer = _commands.get(request.getCommand());
		if (commandContainer == null) {
			CommandResponseInterface cmdFailed = genericResponse("RESPONSE_502_COMMAND_NOT_IMPLEMENTED");
			return cmdFailed;
		}
		request.setProperties(request.getSession().getCommands().get(request.getCommand()));
		CommandResponseInterface response = null;
		request = commandContainer.getCommandInterfaceInstance().doPreHooks(request);
		if(!request.isAllowed()) {
			response = request.getDeniedResponse();
			if (response == null) {
				response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}
			return response;
		}
		try {
			try {
				response = (CommandResponseInterface) commandContainer.getMethod().invoke(commandContainer.getCommandInterfaceInstance(), new Object[] {request});
			}
			catch (InvocationTargetException e) {
				throw e.getCause();
			}
		} catch (ImproperUsageException e) {
			response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
			String helpString = request.getProperties().getProperty("help.specific");
			if (helpString == null) {
				response.addComment("Bug your siteop to add help for the \""
						+ request.getCommand() + "\" command");
			}
			else {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("command", request.getCommand().toUpperCase());
				try {
					response.addComment(SimplePrintf.jprintf(helpString,env));
				}
				catch (FormatterException e1) {
					response.addComment(request.getCommand().toUpperCase()
							+ " command has an invalid help.specific definition");
				}
			}
		} catch (Throwable t) {
			if (!(t instanceof Error)) {
				CommandResponseInterface cmdFailed = new CommandResponse(540, "Command execution failed");
                logger.error("Command {} failed: '{}'", request.getCommand(), request.getArgument(), t);
				return cmdFailed;
			}
			throw (Error) t;
		}

		commandContainer.getCommandInterfaceInstance().doPostHooks(request, response);
		return response;
	}

	public static CommandResponse genericResponse(String type) {
		try {
			return (CommandResponse) _genericResponses.get(type).clone();
		}
		catch (NullPointerException e) {
            logger.error("An unknown generic FTP response was used: {} this is almost certainly a bug", type);
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

	private static HashMap<String,CommandResponse> initGenericResponses() {
		HashMap<String,CommandResponse> genericResponses =
                new HashMap<>();

		/** 150 File status okay; about to open data connection. */
		genericResponses.put("RESPONSE_150_OK",
				new CommandResponse(150,"File status okay; about to open data connection."));

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
				new CommandResponse(550, "Requested action not taken. File unavailable (e.g., file not found, no access)"));

		/**
		 * 553 Requested action not taken. File name not allowed.
		 */
		genericResponses.put("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN",
				new CommandResponse(553, "Requested action not taken.  File name not allowed"));

		/**
		 * 550 Requested action not taken. File exists.
		 */
		genericResponses.put("RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS",
				new CommandResponse(553, "Requested action not taken. File exists."));

		return genericResponses;
	}

	public CommandRequestInterface newRequest(String argument,
			String command, DirectoryHandle directory, String user) {
		return new CommandRequest(argument, command, directory, user);
	}

	public CommandRequestInterface newRequest(String originalCommand, String argument,
			DirectoryHandle directory, String user, Session session, Properties config) {
		return new CommandRequest(originalCommand, argument, directory, user, session, config);
	}

	public Map<String,CommandInstanceContainer> getCommandHandlersMap() {
		return _commands;
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		HashMap<String,CommandInstanceContainer> clonedCommands = null;
		String currentPlugin = CommonPluginUtils.getPluginIdForObject(this);
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String plugin = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (plugin.equals(currentPlugin) && extension.equals("Command")) {
				if (clonedCommands == null) {
					clonedCommands = new HashMap<>(_commands);
				}
				boolean removedCmd = false;
				for (Iterator<Entry<String,CommandInstanceContainer>> iter = clonedCommands.entrySet().iterator(); iter.hasNext();) {
					Entry<String, CommandInstanceContainer> entry = iter.next();
					if (CommonPluginUtils.getPluginIdForObject(entry.getValue().getCommandInterfaceInstance()).equals(event.getPlugin())) {
                        logger.debug("Removing command {}", entry.getKey());
						iter.remove();
						entry.getValue().getCommandInterfaceInstance().unload();
						removedCmd = true;
					}
				}
				if (removedCmd) {
					_commands = clonedCommands;
				}
			}
		}
	}

	public ExtendedPropertyResourceBundle getResourceBundle() {
		return _theme;
	}
}
