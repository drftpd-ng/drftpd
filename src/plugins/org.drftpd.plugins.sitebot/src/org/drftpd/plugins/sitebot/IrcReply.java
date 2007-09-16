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

import java.util.Vector;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.CommandResponseInterface;

/**
 * @author djb61
 * @version $Id$
 */
public class IrcReply {

	private static final Logger logger = Logger.getLogger(IrcReply.class);

	protected int _code;

	protected Vector<String> _lines = new Vector<String>();

	protected String _message;

	public IrcReply(CommandResponseInterface response) {
		setCode(response.getCode());
		setMessage(response.getMessage());
		_lines = response.getComment();
	}

	public IrcReply addComment(Object response) {
		String resp = String.valueOf(response);

		if (resp.indexOf('\n') != -1) {
			String[] lines = resp.split("\n");

			for (int i = 0; i < lines.length; i++) {
				_lines.add(lines[i]);
			}
		} else {
			_lines.add(resp);
		}
		return this;
	}

	public void setCode(int code) {
		_code = code;
	}

	public void setMessage(String response) {
		if (response == null) {
			return;
		}
			
		int pos = response.indexOf('\n');

		if (pos != -1) {
			addComment(response.substring(pos + 1));
			response = response.substring(0, pos);
			logger.debug("Truncated response message with multiple lines: "
					+ response);
		}
		_message = response;
	}

	public int size() {
		return _lines.size();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();

		if ((_lines.size() == 0) && (_message == null)) {
			setMessage("No text specified");
		}

		for (String line: _lines) {
			sb.append(line + "\n");
		}

		if (_message != null && _code != 200) {
			sb.append(_message + "\n");
		}

		return sb.toString();
	}

	public String getMessage() {
		return _message;
	}
}
