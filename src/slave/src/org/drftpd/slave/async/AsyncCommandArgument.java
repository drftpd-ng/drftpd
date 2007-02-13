/*
 * Created on 2004-okt-12
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package org.drftpd.slave.async;

/**
 * @author mog
 * 
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class AsyncCommandArgument extends AsyncCommand {
	protected String _args;

	public AsyncCommandArgument(String index, String name, String args) {
		super(index, name);
		_args = args;
	}

	public String getArgs() {
		return _args;
	}

	public String toString() {
		return getClass().getName() + "[index=" + getIndex() + ",name="
				+ getName() + ",args=" + getArgs() + "]";
	}
}
