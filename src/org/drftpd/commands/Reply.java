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
package org.drftpd.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id$
 */
public class Reply implements Cloneable {
	private static final Logger logger = Logger
			.getLogger(Reply.class.getName());

	/** 150 File status okay; about to open data connection. */
	public static final String RESPONSE_150_OK = "150 File status okay; about to open data connection.\r\n";

	/** 200 Command okay */
	public static final Reply RESPONSE_200_COMMAND_OK = new Reply(200,
			"Command okay");

	/** 202 Command not implemented, superfluous at this site. */
	public static final Reply RESPONSE_202_COMMAND_NOT_IMPLEMENTED = new Reply(
			202, "Command not implemented, superfluous at this site.");

	/** 215 NAME system type. */
	public static final Reply RESPONSE_215_SYSTEM_TYPE = new Reply(215,
			"UNIX system type.");

	/** 221 Service closing control connection. */
	public static final Reply RESPONSE_221_SERVICE_CLOSING = new Reply(221,
			"Service closing control connection.");

	/** 226 Closing data connection */
	public static final Reply RESPONSE_226_CLOSING_DATA_CONNECTION = new Reply(
			226, "Closing data connection");

	/** 230 User logged in, proceed. */
	public static final Reply RESPONSE_230_USER_LOGGED_IN = new Reply(230,
			"User logged in, proceed.");

	/** 250 Requested file action okay, completed. */
	public static final Reply RESPONSE_250_ACTION_OKAY = new Reply(250,
			"Requested file action okay, completed.");

	/** 331 User name okay, need password. */
	public static final Reply RESPONSE_331_USERNAME_OK_NEED_PASS = new Reply(
			331, "User name okay, need password.");

	/** 350 Requested file action pending further information. */
	public static final Reply RESPONSE_350_PENDING_FURTHER_INFORMATION = new Reply(
			350, "Requested file action pending further information.");

	/** 425 Can't open data connection. */
	public static final String RESPONSE_425_CANT_OPEN_DATA_CONNECTION = "425 Can't open data connection.\r\n";

	/** 426 Connection closed; transfer aborted. */
	public static final Reply RESPONSE_426_CONNECTION_CLOSED_TRANSFER_ABORTED = new Reply(
			426, "Connection closed; transfer aborted.");

	/** 450 Requested file action not taken. */
	public static final Reply RESPONSE_450_REQUESTED_ACTION_NOT_TAKEN = new Reply(
			450, "Requested file action not taken.");

	/**
	 * 450 No transfer-slave(s) available author <a
	 * href="mailto:drftpd@mog.se">Morgan Christiansson</a>
	 */
	public static final Reply RESPONSE_450_SLAVE_UNAVAILABLE = new Reply(450,
			"No transfer-slave(s) available");

	/** 500 Syntax error, command unrecognized. */
	public static final Reply RESPONSE_500_SYNTAX_ERROR = new Reply(500,
			"Syntax error, command unrecognized.");

	/** 501 Syntax error in parameters or arguments */
	public static final Reply RESPONSE_501_SYNTAX_ERROR = new Reply(501,
			"Syntax error in parameters or arguments");

	/** 502 Command not implemented. */
	public static final Reply RESPONSE_502_COMMAND_NOT_IMPLEMENTED = new Reply(
			502, "Command not implemented.");

	/** 503 Bad sequence of commands. */
	public static final Reply RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS = new Reply(
			503, "Bad sequence of commands.");

	/** 504 Command not implemented for that parameter. */
	public static final Reply RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM = new Reply(
			504, "Command not implemented for that parameter.");

	/** 530 Access denied */
	public static final Reply RESPONSE_530_ACCESS_DENIED = new Reply(530,
			"Access denied");

	/** 530 Not logged in. */
	public static final Reply RESPONSE_530_NOT_LOGGED_IN = new Reply(530,
			"Not logged in.");

	public static final Reply RESPONSE_530_SLAVE_UNAVAILABLE = new Reply(530,
			"No transfer-slave(s) available");

	/**
	 * 550 Requested action not taken. File unavailable. File unavailable (e.g.,
	 * file not found, no access).
	 */
	public static final Reply RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN = new Reply(
			550, "Requested action not taken. File unavailable.");

	/**
	 * 553 Requested action not taken. File name not allowed.
	 */
	public static final Reply RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN = new Reply(
			553, "Requested action not taken.  File name not allowed");

	/**
	 * 550 Requested action not taken. File exists.
	 */
	public static final Reply RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN_FILE_EXISTS = new Reply(
			550, "Requested action not taken. File exists.");

	protected int _code;

	protected Vector<String> _lines = new Vector<String>();

	protected String _message;

	public Reply() {
	}

	public Reply(int code) {
		setCode(code);
	}

	public Reply(int code, String response) {
		setCode(code);
		setMessage(response);
	}

	public Reply addComment(BufferedReader in) throws IOException {
		String line;

		while ((line = in.readLine()) != null) { // throws IOException
			this.addComment(line);
		}

		return this;
	}

	public Reply addComment(Object response) {
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

	public Object clone() {
		try {
			Reply r = (Reply) super.clone();
			r._lines = (Vector) _lines.clone();

			return r;
		} catch (CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
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
			logger.log(Level.DEBUG,
					"Truncated response message with multiple lines: "
							+ response);
		}

		_message = response;
	}

	public int size() {
		return _lines.size();
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();

		// sb.append(code + "-");
		if ((_lines.size() == 0) && (_message == null)) {
			setMessage("No text specified");
		}

		for (Iterator iter = _lines.iterator(); iter.hasNext();) {
			String comment = (String) iter.next();

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
