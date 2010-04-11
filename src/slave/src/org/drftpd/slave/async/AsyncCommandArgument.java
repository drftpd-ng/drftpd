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
	
	protected String[] _args;
	protected String _name;
	protected String _index;

	public AsyncCommandArgument(String index, String name, String args) {
		_args = new String[]{args};
		_index = index;
		_name = name;
	}

	public AsyncCommandArgument(String index, String name, String[] args) {
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
		return _args[0];
	}

	public String[] getArgsArray() {
		return _args;
	}

	public String toString() {
		StringBuilder output = new StringBuilder(getClass().getName());
		output.append("[index=");
		output.append(_index);
		output.append(",name=");
		output.append(_name);
		output.append(",args=");
		for (int i = 0; i < _args.length; i++) {
			output.append(_args[i]);
			if (i < _args.length - 1) {
				output.append(",");
			}
		}
		output.append("]");
		return output.toString();
	}
}
