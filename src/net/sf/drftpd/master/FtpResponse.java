/*
 * Created on 2003-maj-18
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.io.PrintWriter;
import java.util.Iterator;
import java.util.Vector;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpResponse implements Cloneable {

	/** 200 Command okay */
	public static final FtpResponse RESPONSE_200_COMMAND_OK =
		new FtpResponse(200, "Command okay");

	/** 202 Command not implemented, superfluous at this site. */
	public static final FtpResponse RESPONSE_202_COMMAND_NOT_IMPLEMENTED =
		new FtpResponse(
			202,
			"Command not implemented, superfluous at this site.");

	/** 215 NAME system type. */
	public static final FtpResponse RESPONSE_215_SYSTEM_TYPE =
		new FtpResponse(215, "UNIX system type.");

	/** 221 Service closing control connection. */
	public static final FtpResponse RESPONSE_221_SERVICE_CLOSING =
		new FtpResponse(221, "Service closing control connection.");

	/** 226 Closing data connection */
	public static final FtpResponse RESPONSE_226_CLOSING_DATA_CONNECTION =
		new FtpResponse(226, "Closing data connection");

	/** 230 User logged in, proceed. */
	public static final FtpResponse RESPONSE_230_USER_LOGGED_IN =
		new FtpResponse(230, "User logged in, proceed.");

	/** 331 User name okay, need password. */
	public static final FtpResponse RESPONSE_331_USERNAME_OK_NEED_PASS =
		new FtpResponse(331, "User name okay, need password.");
		
	/** 450 No transfer-slave(s) available
	 * @author mog
	 */
	public static final FtpResponse RESPONSE_450_SLAVE_UNAVAILABLE =
		new FtpResponse(450, "No transfer-slave(s) available");
		
	/** 500 Syntax error, command unrecognized. */
	public static final FtpResponse RESPONSE_500_SYNTAX_ERROR =
		new FtpResponse(500, "Syntax error, command unrecognized.");

	/** 501 Syntax error in parameters or arguments */
	public static final FtpResponse RESPONSE_501_SYNTAX_ERROR =
		new FtpResponse(501, "Syntax error in parameters or arguments");

	/** 502 Command not implemented. */
	public static final FtpResponse RESPONSE_502_COMMAND_NOT_IMPLEMENTED =
		new FtpResponse(502, "Command not implemented.");

	/** 530 Access denied */
	public static final FtpResponse RESPONSE_530_ACCESS_DENIED =
		new FtpResponse(530, "Access denied");

	/** 530 Not logged in. */
	public static final FtpResponse RESPONSE_530_NOT_LOGGED_IN =
		new FtpResponse(530, "Not logged in.");

	/** 550 Requested action not taken.
	 * File unavailable (e.g., file not found, no access).
	 */
	public static final FtpResponse RESPONSE_550_REQUESTED_ACTION_NOT_TAKEN =
		new FtpResponse(550, "Requested action not taken. File unavailable.");

	/** 553 Requested action not taken.
	 * File name not allowed.
	 */
	public static final FtpResponse RESPONSE_553_REQUESTED_ACTION_NOT_TAKEN =
		new FtpResponse(553, "Requested action not taken.");

	protected Vector lines = new Vector();
	protected int code;
	protected String response;

	public FtpResponse() {
	}
	public FtpResponse(int code) {
		this.code = code;
	}
	public FtpResponse(int code, String response) {
		setCode(code);
		setResponse(response);
	}

	public void addComment(String response) {
		lines.add(response);
	}

	public void setResponse(String response) {
		if (response.indexOf('\n') != -1)
			throw new IllegalArgumentException("newlines not allowed in the response");
		this.response = response;
	}
	public void setCode(int code) {
		this.code = code;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		//sb.append(code + "-");
		for (Iterator iter = lines.iterator(); iter.hasNext();) {
			sb.append(code + "- " + (String) iter.next() + "\r\n");
		}
		sb.append(code + " " + response + "\r\n");
		return sb.toString();
	}
	/**
	 * 
	 * @deprecated
	 */
	public void print(PrintWriter out) {
		out.print(toString());
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	protected Object clone() {
		try {
			return super.clone();
		} catch (CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}

}
