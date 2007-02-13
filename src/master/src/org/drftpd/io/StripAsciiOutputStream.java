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
package org.drftpd.io;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream filter that strips the \r from \r\n sequences.
 * 
 * @author mog
 * @version $Id$
 */
public class StripAsciiOutputStream extends OutputStream {
	private OutputStream _out;

	boolean _lastWasCarriageReturn = false;

	public StripAsciiOutputStream(OutputStream out) {
		_out = out;
	}

	public void write(int b) throws IOException {
		if (b == '\r') {
			_lastWasCarriageReturn = true;

			return;
		}

		if (_lastWasCarriageReturn) {
			_lastWasCarriageReturn = false;

			if (b != '\n') {
				_out.write('\r');
			}
		}

		_out.write(b);
	}

	public void close() throws IOException {
		_out.close();
	}

	public void flush() throws IOException {
		_out.flush();
	}
}
