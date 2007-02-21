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

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class CommandRequest extends KeyedMap implements CommandRequestInterface {

	public static final Key ALLOWED = new Key(CommandRequest.class, "allowed",
			Boolean.class);

	public static final Key DENIED_RESPONSE = new Key(CommandRequest.class, "denied_response",
			CommandResponse.class);

	public static final Key ARGUMENT = new Key(CommandRequest.class, "argument",
			String.class);

	public static final Key COMMAND = new Key(CommandRequest.class, "command",
			String.class);

	public static final Key CONNECTION = new Key(CommandRequest.class, "connection",
			BaseFtpConnection.class);

	public static final Key CURRENT_DIRECTORY = new Key(CommandRequest.class, "current_directory",
			DirectoryHandle.class);
	
	public static final Key ORIGINAL_COMMAND = new Key(CommandRequest.class, "original_command",
			String.class);

	public static final Key USER = new Key(CommandRequest.class, "user",
			String.class);

	public CommandRequest(String argument, String command,
			DirectoryHandle directory, String user) {
		setArgument(argument);
		setCommand(command);
		setCurrentDirectory(directory);
		setUser(user);
	}

	public CommandRequest(String argument, String command, DirectoryHandle directory,
			String user, BaseFtpConnection connection, String originalCommand) {
		setArgument(argument);
		setCommand(command);
		setConnection(connection);
		setCurrentDirectory(directory);
		setOriginalCommand(originalCommand);
		setUser(user);
	}

	public void setAllowed(Boolean allowed) {
		setObject(CommandRequest.ALLOWED, allowed);
	}

	public void setArgument(String argument) {
		if (argument != null) {
			setObject(CommandRequest.ARGUMENT, argument);
		}
	}

	public void setCommand(String command) {
		if (command != null) {
			setObject(CommandRequest.COMMAND, command);
		}
	}

	public void setConnection(BaseFtpConnection connection) {
		setObject(CommandRequest.CONNECTION, connection);
	}
	public void setCurrentDirectory(DirectoryHandle currentDirectory) {
		setObject(CommandRequest.CURRENT_DIRECTORY, currentDirectory);
	}

	public void setDeniedResponse(CommandResponse response) {
		setObject(CommandRequest.DENIED_RESPONSE, response);
	}

	public void setOriginalCommand(String command) {
		setObject(CommandRequest.ORIGINAL_COMMAND, command);
	}

	public void setUser(String currentUser) {
		if (currentUser != null) {
			setObject(CommandRequest.USER, currentUser);
		}
	}

	public boolean getAllowed() {
		return getObjectBoolean(CommandRequest.ALLOWED);
	}

	public String getArgument() {
		return (String) getObject(CommandRequest.ARGUMENT, "");
	}

	public String getCommand() {
		return (String) getObject(CommandRequest.COMMAND, "");
	}

	public CommandResponse getDeniedResponse() {
		return (CommandResponse) getObject(CommandRequest.DENIED_RESPONSE, null);
	}

	public BaseFtpConnection getConnection() {
		return (BaseFtpConnection) getObject(CommandRequest.CONNECTION, null);
	}

	public DirectoryHandle getCurrentDirectory() {
		return (DirectoryHandle) getObject(CommandRequest.CURRENT_DIRECTORY, new DirectoryHandle("/"));
	}

	public String getOriginalCommand() {
		return (String) getObject(CommandRequest.ORIGINAL_COMMAND, null);
	}

	public String getUser() {
		return (String) getObject(CommandRequest.USER, null);
	}

	public boolean hasArgument() {
		try {
			getObject(CommandRequest.ARGUMENT);
			return true;
		}
		catch (KeyNotFoundException e) {
			return false;
		}
	}
}
