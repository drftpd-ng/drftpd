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
package org.drftpd.master;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commandmanager.CommandResponseInterface;

import java.util.Iterator;
import java.util.Vector;

/**
 * @author djb61
 * @author mog
 * @version $Id$
 */
public class FtpReply {
	private static final Logger logger = LogManager.getLogger(FtpReply.class.getName());

	protected int _code;
	
	protected Vector<String> _lines = new Vector<>();
	
	protected String _message;
	
	public FtpReply() {
	}

	public FtpReply(CommandResponseInterface response) {
		setCode(response.getCode());
		setMessage(response.getMessage());
		_lines = response.getComment();
	}
	
	public FtpReply(int code) {
		setCode(code);
	}
	
	public FtpReply(int code, String response) {
		setCode(code);
		setMessage(response);
	}

	public FtpReply addComment(Object response) {
		String resp = String.valueOf(response);

		if (resp.indexOf('\n') != -1) {
			String[] lines = resp.split("\n");

            for (String line : lines) {
                _lines.add(line);
            }
		} else {
			_lines.add(resp);
		}
		return this;
	}

	public int getCode() {
		return _code;
	}

	public void setCode(int code) {
		_code = code;
	}

	public void setMessage(String response) {
		if (response == null)
			response = "No text";
		int pos = response.indexOf('\n');

		if (pos != -1) {
			addComment(response.substring(pos + 1));
			response = response.substring(0, pos);
            logger.debug("Truncated response message with multiple lines: {}", response);
		}

		_message = response;
	}

	public int size() {
		return _lines.size();
	}

	public String toString() {
		StringBuilder sb = new StringBuilder();

		// sb.append(code + "-");
		if ((_lines.size() == 0) && (_message == null)) {
			setMessage("No text specified");
		}

		for (Iterator<String> iter = _lines.iterator(); iter.hasNext();) {
			String comment = iter.next();

			if (!iter.hasNext() && (_message == null)) {
				sb.append(_code + "  " + comment + "\r\n");
			} else {
				sb.append(_code + "- " + comment + "\r\n");
			}
		}

		if (_message != null) {
			sb.append(_code + " " + _message + "\r\n");
		}

		return sb.toString();
	}

	public String getMessage() {
		return _message;
	}
}
