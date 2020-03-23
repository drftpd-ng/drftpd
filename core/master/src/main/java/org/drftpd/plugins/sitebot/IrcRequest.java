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
package org.drftpd.plugins.sitebot;

/**
 * @author djb61
 * @version $Id$
 */
public class IrcRequest {

	private String _argument;

	private String _command;

	private String _line;

	public IrcRequest(String commandline) {
		_line = commandline.trim();
		parse();
	}

	private void parse() {
		int spInd = _line.indexOf(' ');

		if (spInd != -1) {
			_command = _line.substring(0, spInd);
			_argument = _line.substring(spInd + 1);

		} else {
			_command = _line;
			_argument = null;
		}
	}

	/**
	 * Get the irc command.
	 */
	public String getCommand() {
		return _command;
	}

	/**
	 * Get irc input argument.
	 */
	public String getArgument() {
		return _argument;
	}

	/**
	 * Get the irc request line.
	 */
	public String getCommandLine() {
		return _line;
	}

	/**
	 * Has argument.
	 */
	public boolean hasArgument() {
		return _argument != null;
	}
}
