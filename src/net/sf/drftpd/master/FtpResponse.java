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
public class FtpResponse {
	protected Vector lines;
	protected int code;
	protected String response;
	
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
		sb.append(code+"-");
		for (Iterator iter = lines.iterator(); iter.hasNext();) {
			sb.append((String) iter.next()+"\r\n");
		}
		sb.append(code+" "+response);
		return sb.toString();
	}

	public void print(PrintWriter out) {
		out.print(toString());
	}
}
