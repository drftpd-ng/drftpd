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
import java.io.InputStream;

/**
 * Not thread-safe (is any-In/OutputStream thread-safe?).
 * 
 * @author mog
 * @version $Id$
 */
public class StripAsciiInputStream extends InputStream {
	private InputStream _in;

	private int peekChar = -1;

	boolean _lastWasCarriageReturn = false;

	public StripAsciiInputStream(InputStream in) {
		_in = in;
	}

	public int read() throws IOException {
		if (peekChar != -1) {
			int ret = peekChar;
			peekChar = -1;
			System.err.println("return peeked " + ret);

			return ret;
		}

		while (true) {
			int b = _in.read();
			System.err.println("read: " + (char) b + "(" + b + ")");

			if (b == '\r') {
				System.err.println("read: was \\r");
				_lastWasCarriageReturn = true;

				continue;
			}

			if (b == '\n') {
				System.err.println("read: was \\n");
			}

			if (_lastWasCarriageReturn) {
				_lastWasCarriageReturn = false;

				if (b != '\n') {
					peekChar = b;
					System.err.println("return \\r");

					return '\r';
				}
			}

			System.err.println("return " + (char) b + " (" + b + ")");

			return b;
		}
	}

	public void close() throws IOException {
		_in.close();
	}
}
