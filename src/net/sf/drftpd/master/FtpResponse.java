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

	/** 226 Closing data connection */
	public static final FtpResponse RESPONSE_226_CLOSING_DATA_CONNECTION = 
		new FtpResponse(226, "Closing data connection");
	
	/** 501 Syntax error in parameters or arguments */
	public static final FtpResponse RESPONSE_501_SYNTAX_ERROR =
		new FtpResponse(501, "Syntax error in parameters or arguments");
	
	/** 530 Access denied */
	public static final FtpResponse RESPONSE_530_ACCESS_DENIED =
		new FtpResponse(530, "Access denied");

	protected Vector lines;
	protected int code;
	protected String response;

	public FtpResponse() {
	}
	public FtpResponse(int code) {
		this.code = code;
	}
	public FtpResponse(int code, String response) {
		this.code = code;
		this.response = response;
	}

	public void addComment(String response) {
		lines.add(response);
	}

	public void setResponse(String response) {
		this.response = response;
	}
	public void setCode(int code) {
		this.code = code;
	}

	public String toString() {
		StringBuffer sb = new StringBuffer();
		sb.append(code + "-");
		for (Iterator iter = lines.iterator(); iter.hasNext();) {
			sb.append((String) iter.next() + "\r\n");
		}
		sb.append(code + " " + response + "\r\n");
		return sb.toString();
	}

	public void print(PrintWriter out) {
		out.print(toString());
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#clone()
	 */
	protected Object clone() {
		try {
			return super.clone();
		} catch(CloneNotSupportedException ex) {
			throw new RuntimeException(ex);
		}
	}

}
