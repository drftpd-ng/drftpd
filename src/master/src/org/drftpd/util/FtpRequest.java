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
package org.drftpd.util;

import java.io.Serializable;

/**
 * Ftp command request class. We can access command, line and argument using
 * <code>{CMD}, {ARG}</code> within ftp status file. This represents single
 * Ftp request.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @version $Id$
 */
public class FtpRequest implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = -8967203000005023929L;

	private String line = null;

	private String command = null;

	private String argument = null;

	/**
	 * Constructor.
	 * 
	 * @param commandLine
	 *            ftp input command line.
	 */
	public FtpRequest(String commandLine) {
		line = commandLine.trim();
		parse();
	}

	/**
	 * Parse the ftp command line.
	 */
	private void parse() {
		int spInd = line.indexOf(' ');

		if (spInd != -1) {
			command = line.substring(0, spInd).toUpperCase();
			argument = line.substring(spInd + 1);

			if (command.equals("SITE")) {
				spInd = line.indexOf(' ', spInd + 1);

				if (spInd != -1) {
					command = line.substring(0, spInd).toUpperCase();
					argument = line.substring(spInd + 1);
				} else {
					command = line.toUpperCase();
					argument = null;
				}
			}
		} else {
			command = line.toUpperCase();
			argument = null;
		}

		// if ((command.length() > 0) && (command.charAt(0) == 'X')) {
		// command = command.substring(1);
		// }
	}

	/**
	 * Get the ftp command.
	 */
	public String getCommand() {
		return command;
	}

	/**
	 * Get ftp input argument.
	 */
	public String getArgument() {
		return argument;
	}

	/**
	 * Get the ftp request line.
	 */
	public String getCommandLine() {
		return line;
	}

	/**
	 * Has argument.
	 */
	public boolean hasArgument() {
		return argument != null;
	}
}
