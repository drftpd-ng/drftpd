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
package org.drftpd.plugins.commandmanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.commandmanager.CommandManagerInterface;
import org.drftpd.master.commandmanager.CommandRequestInterface;
import org.drftpd.master.commandmanager.CommandResponseInterface;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.master.Session;
import org.drftpd.master.util.ExtendedPropertyResourceBundle;
import org.drftpd.master.util.ThemeResourceBundle;
import org.drftpd.master.vfs.DirectoryHandle;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

/**
 * @author djb61
 * @version $Id$
 */
public class StandardCommandManager implements CommandManagerInterface {

    private static final Logger logger = LogManager.getLogger(StandardCommandManager.class);
    private static HashMap<String, CommandResponse> _genericResponses = initGenericResponses();

    private ThemeResourceBundle _theme;

    /**
     * This is a map of commands, e.x.:
     * "AUTH" -> CommandInstanceContainer (Instance of the CommandInterface and the appropriately attached Method)
     */
    private Map<String, CommandInstanceContainer> _commands;

    public StandardCommandManager() {
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public synchronized void initialize(HashMap<String, Properties> requiredCmds, String themeDir) {
        loadThemes(themeDir);

        HashMap<String, CommandInstanceContainer> commands = new HashMap<>();

        /*	Iterate over the ArrayList of commands that the calling frontend
         * 	has stated it needs. Check to see whether we have a valid Command
         * 	extension attached for the command, is so add it to the commands
         * 	map to be used
         */
        for (Entry<String, Properties> requiredCmd : requiredCmds.entrySet()) {
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
                // TODO [DONE] @JRI Plug commands
                Class<?> aClass = Class.forName(pluginString + "." + classString);
                Method commandMethod = aClass.getMethod(methodString, CommandRequest.class);
                CommandInterface cmdInstance = (CommandInterface) aClass.getConstructor().newInstance();
                cmdInstance.initialize(methodString, pluginString, this);
                commands.put(requiredCmd.getKey(), new CommandInstanceContainer(commandMethod, cmdInstance));
                logger.debug("Adding command implementation {}", requiredCmd.getKey());
            } catch (Exception e) {
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
        _theme = new ThemeResourceBundle(themeDir);
    }

    public CommandResponseInterface execute(CommandRequestInterface request) {
        CommandInstanceContainer commandContainer = _commands.get(request.getCommand());
        if (commandContainer == null) {
            return genericResponse("RESPONSE_502_COMMAND_NOT_IMPLEMENTED");
        }
        request.setProperties(request.getSession().getCommands().get(request.getCommand()));
        CommandResponseInterface response;
        request = commandContainer.getCommandInterfaceInstance().doPreHooks(request);
        if (!request.isAllowed()) {
            response = request.getDeniedResponse();
            if (response == null) {
                response = StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
            }
            return response;
        }
        try {
            try {
                response = (CommandResponseInterface) commandContainer.getMethod().invoke(commandContainer.getCommandInterfaceInstance(), new Object[]{request});
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        } catch (ImproperUsageException e) {
            response = StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
            String helpString = request.getProperties().getProperty("help.specific");
            if (helpString == null) {
                response.addComment("Bug your siteop to add help for the \""
                        + request.getCommand() + "\" command");
            } else {
                ReplacerEnvironment env = new ReplacerEnvironment();
                env.add("command", request.getCommand().toUpperCase());
                try {
                    response.addComment(SimplePrintf.jprintf(helpString, env));
                } catch (FormatterException e1) {
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
        } catch (NullPointerException e) {
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

    private static HashMap<String, CommandResponse> initGenericResponses() {
        HashMap<String, CommandResponse> genericResponses =
                new HashMap<>();

        /** 150 File status okay; about to open data connection. */
        genericResponses.put("RESPONSE_150_OK",
                new CommandResponse(150, "File status okay; about to open data connection."));

        /** 200 Command okay */
        genericResponses.put("RESPONSE_200_COMMAND_OK",
                new CommandResponse(200, "Command okay"));

        /** 202 Command not implemented, superfluous at this site. */
        genericResponses.put("RESPONSE_202_COMMAND_NOT_IMPLEMENTED",
                new CommandResponse(202, "Command not implemented, superfluous at this site."));

        /** 215 NAME system type. */
        genericResponses.put("RESPONSE_215_SYSTEM_TYPE",
                new CommandResponse(215, "UNIX system type."));

        /** 221 Service closing control connection. */
        genericResponses.put("RESPONSE_221_SERVICE_CLOSING",
                new CommandResponse(221, "Service closing control connection."));

        /** 226 Closing data connection */
        genericResponses.put("RESPONSE_226_CLOSING_DATA_CONNECTION",
                new CommandResponse(226, "Closing data connection"));

        /** 230 User logged in, proceed. */
        genericResponses.put("RESPONSE_230_USER_LOGGED_IN",
                new CommandResponse(230, "User logged in, proceed."));

        /** 250 Requested file action okay, completed. */
        genericResponses.put("RESPONSE_250_ACTION_OKAY",
                new CommandResponse(250, "Requested file action okay, completed."));

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
                new CommandResponse(450, "No transfer-slave(s) available"));

        /** 500 Syntax error, command unrecognized. */
        genericResponses.put("RESPONSE_500_SYNTAX_ERROR",
                new CommandResponse(500, "Syntax error, command unrecognized."));

        /** 501 Syntax error in parameters or arguments */
        genericResponses.put("RESPONSE_501_SYNTAX_ERROR",
                new CommandResponse(501, "Syntax error in parameters or arguments"));

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
                new CommandResponse(530, "Access denied"));

        /** 530 Not logged in. */
        genericResponses.put("RESPONSE_530_NOT_LOGGED_IN",
                new CommandResponse(530, "Not logged in."));

        genericResponses.put("RESPONSE_530_SLAVE_UNAVAILABLE",
                new CommandResponse(530, "No transfer-slave(s) available"));

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

    public Map<String, CommandInstanceContainer> getCommandHandlersMap() {
        return _commands;
    }


    public ThemeResourceBundle getResourceBundle() {
        return _theme;
    }
}
