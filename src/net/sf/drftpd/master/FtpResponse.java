/*
 * Created on 2003-maj-18
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Iterator;
import java.util.Vector;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 */
public class FtpResponse implements Cloneable {
	private static Logger logger =
		Logger.getLogger(FtpResponse.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}

	/** 150 File status okay; about to open data connection. */
	public static final String RESPONSE_150_OK =
		"150 File status okay; about to open data connection.\r\n";

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

	/** 250 Requested file action okay, completed. */
	public static final FtpResponse RESPONSE_250_ACTION_OKAY =
		new FtpResponse(250, "Requested file action okay, completed.");

	/** 331 User name okay, need password. */
	public static final FtpResponse RESPONSE_331_USERNAME_OK_NEED_PASS =
		new FtpResponse(331, "User name okay, need password.");

	/** 350 Requested file action pending further information. */
	public static final FtpResponse RESPONSE_350_PENDING_FURTHER_INFORMATION =
		new FtpResponse(
			350,
			"Requested file action pending further information.");

	/** 425 Can't open data connection. */
	public static final String RESPONSE_425_CANT_OPEN_DATA_CONNECTION =
		"425 Can't open data connection.\r\n";
	
	/** 426 Connection closed; transfer aborted. */
	public static final FtpResponse RESPONSE_426_CONNECTION_CLOSED_TRANSFER_ABORTED =
		new FtpResponse(426, "Connection closed; transfer aborted.");
		
	/** 450 No transfer-slave(s) available
	 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
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

	/** 503 Bad sequence of commands. */
	public static final FtpResponse RESPONSE_503_BAD_SEQUENCE_OF_COMMANDS =
		new FtpResponse(503, "Bad sequence of commands.");

	/** 504 Command not implemented for that parameter. */
	public static final FtpResponse RESPONSE_504_COMMAND_NOT_IMPLEMENTED_FOR_PARM =
		new FtpResponse(504, "Command not implemented for that parameter.");

	/** 530 Access denied */
	public static final FtpResponse RESPONSE_530_ACCESS_DENIED =
		new FtpResponse(530, "Access denied");

	public static final FtpResponse RESPONSE_530_SLAVE_UNAVAILABLE = 
	new FtpResponse(530, "No transfer-slave(s) available");

	/** 530 Not logged in. */
	public static final FtpResponse RESPONSE_530_NOT_LOGGED_IN =
		new FtpResponse(530, "Not logged in.");

	/** 550 Requested action not taken. File unavailable.
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
	protected String message;

	public FtpResponse() {
	}
	public FtpResponse(int code) {
		setCode(code);
	}
	public FtpResponse(int code, String response) {
		setCode(code);
		setMessage(response);
	}

	public FtpResponse addComment(Object response) {
		lines.add(String.valueOf(response));
		return this;
	}

	public FtpResponse addComment(BufferedReader in) throws IOException {
		String line;
		while ((line = in.readLine()) != null) { //throws IOException
			this.addComment(line);
		}
		return this;
	}
	public void setMessage(String response) {
		if (response.indexOf('\n') != -1) {
			response = response.substring(0, response.indexOf('\n'));
			logger.log(Level.WARNING, "Truncated response message with multiple lines: "+response);
		}
		this.message = response;
	}
	public void setCode(int code) {
		this.code = code;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		//sb.append(code + "-");
		if(lines.size() == 0 && message == null) setMessage("No text specified");
		for (Iterator iter = lines.iterator(); iter.hasNext();) {
			String comment = (String) iter.next();
			if (!iter.hasNext() && message == null) {
				sb.append(code + "  " + comment + "\r\n");
			} else {
				sb.append(code + "- " + comment + "\r\n");
			}
		}
		if (message != null)
			sb.append(code + " " + message + "\r\n");
		return sb.toString();
	}

	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	protected Object clone() {
		try {
			FtpResponse r = (FtpResponse) super.clone();
			r.lines = (Vector) this.lines.clone();
			return r;
		} catch (CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}

}
