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

import org.drftpd.master.Session;
import org.drftpd.util.ExtendedPropertyResourceBundle;
import org.drftpd.vfs.DirectoryHandle;

import java.util.HashMap;
import java.util.Properties;

/**
 * @author djb61
 * @version $Id$
 */
public interface CommandManagerInterface {

	/**
	 * Initialization routine.
	 * @param requiredCmds, a map of commands and corresponding Properties read from the configuration file.
	 * @param themeDir the location relative to drftpd root path of the theme files for the calling frontend
	 * @see <code>org.drftpd.master.config.FtpConfig.getFtpCommandsMap()</code> for more information about the Map.
	 */
    void initialize(HashMap<String, Properties> requiredCmds, String themeDir);

	/**
	 * Executes the command. 
	 */
    CommandResponseInterface execute(CommandRequestInterface request);

	/**
	 * To explain how this constructor works take this "CWD /PHOTOS/" as the request
	 * and the current directory is "/".
	 * @param originalCommand, the actual command name. ("CWD").
	 * @param argument, the argument of the command ("/PHOTOS/").
	 * @param directory, the current directory. ("/").
	 * @param user, the issuer of the command.
	 * @param session, the Session object provided by the frontend for storing data.
	 * @param config, the Properties object containing details and setting for the command sent.
	 */
    CommandRequestInterface newRequest(String originalCommand, String argument,
                                       DirectoryHandle directory, String user, Session session, Properties config);

	ExtendedPropertyResourceBundle getResourceBundle();
}
