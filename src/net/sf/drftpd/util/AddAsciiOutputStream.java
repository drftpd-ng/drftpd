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
package net.sf.drftpd.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * AddAsciiOutputStream that ignores \r and adds an \r before every \n.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @version $Id: AddAsciiOutputStream.java,v 1.3 2004/02/10 00:03:31 mog Exp $
 */
public class AddAsciiOutputStream extends OutputStream {
	private OutputStream _out;
	private boolean _lastWasCarriageReturn = false;

	/**
	 * Constructor.
	 * @param os <code>java.io.OutputStream</code> to be filtered.
	 */
	public AddAsciiOutputStream(OutputStream os) {
		_out = os;
	}

	/**
	 * Write a single byte. 
	 * ASCII characters are defined to be
	 * the lower half of an eight-bit code set (i.e., the most
	 * significant bit is zero). Change "\n" to "\r\n".
	 */
	public void write(int i) throws IOException {
		if (i == '\r') {
			_lastWasCarriageReturn = true;
		}
		if (i == '\n') {
			if (!_lastWasCarriageReturn) {
				_out.write('\r');
			}
		}
		_lastWasCarriageReturn = false;
		_out.write(i);
	}

	public void close() throws IOException {
		_out.close();
	}

	public void flush() throws IOException {
		_out.flush();
	}
}
