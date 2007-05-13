/*
 * Created on 2004-okt-12
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.drftpd.slave.async;

import java.io.Serializable;

/**
 * @author mog
 * 
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class AsyncCommandArgument implements Serializable {
	private static final long serialVersionUID = 2937803123798047158L;
	
	protected String _args;
	protected String _name;
	protected String _index;

	public AsyncCommandArgument(String index, String name, String args) {
		_args = args;
		_index = index;
		_name = name;
	}

	public String getIndex() {
		return _index;
	}

	public String getName() {
		return _name;
	}
	
	public String getArgs() {
		return _args;
	}

	public String toString() {
		return getClass().getName() + "[index=" + getIndex() + ",name="
				+ getName() + ",args=" + getArgs() + "]";
	}
}
