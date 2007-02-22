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

import java.util.ArrayList;

import org.drftpd.master.BaseFtpConnection;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author djb61
 * @version $Id$
 */
public interface CommandManagerInterface {

	/**
	 * Initialization routine.
	 * @param requiredCmds, a list of commands read from the configuration file.
	 * @see <code>org.drftpd.master.config.FtpConfig.getFtpCommandsList()</code> for more information about the ArrayList.
	 */
	public void initialize(ArrayList<String> requiredCmds);

	/**
	 * Executes the command. 
	 */
	public CommandResponseInterface execute(CommandRequestInterface request);

	/**
	 * To explain how this constructor works take this "CWD /PHOTOS/" as the request
	 * and the current directory is "/".
	 * @param argument, the argument of the command ("/PHOTOS/").
	 * @param command, the 'mapped' command. ("org.drftpd.commands.Dir.doCWD").
	 * @param directory, the current directory. ("/").
	 * @param user, the issuer of the command.
	 * @param connection, the control connection, where outputs should be written to.
	 * @param originalCommand, the actual command name. ("CWD").
	 */
	public CommandRequestInterface newRequest(String argument, String command,
			DirectoryHandle directory, String user, BaseFtpConnection connection, String originalCommand);
}
