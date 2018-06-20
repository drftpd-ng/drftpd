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
package org.drftpd.protocol.zipscript.zip.common;

import java.io.Serializable;

import org.drftpd.dynamicdata.Key;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class DizInfo implements Serializable {

	public static final Key<DizInfo> DIZINFO = new Key<>(DizInfo.class, "diz");

	private boolean _dizValid = false;

	private int _dizTotal = 0;

	private String _dizString;

	public DizInfo() {
		
	}

	public void setValid(boolean dizValid) {
		_dizValid = dizValid;
	}

	public void setTotal(int dizTotal) {
		_dizTotal = dizTotal;
	}

	public void setString(String dizString) {
		_dizString = dizString;
	}

	public boolean isValid() {
		return _dizValid;
	}

	public int getTotal() {
		return _dizTotal;
	}

	public String getString() {
		return _dizString;
	}
}
