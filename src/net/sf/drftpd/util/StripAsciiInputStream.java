/*
 * Copyright (c) 2004 Morgan Christiansson
 * Redistribution in source form not allowed without permission.
 */
package net.sf.drftpd.util;

import java.io.IOException;
import java.io.InputStream;

/**
 * @author mog
 * @version $Id: StripAsciiInputStream.java,v 1.1 2004/07/02 19:58:55 mog Exp $
 */
public class StripAsciiInputStream extends InputStream {
	private InputStream _in;

	boolean _lastWasCarriageReturn = false;

	public StripAsciiInputStream(InputStream out) {
		_in = out;
	}

	public int read() throws IOException {
		while (true) {
			int b = _in.read();
			if (b == '\r') {
				_lastWasCarriageReturn = true;
				continue;
			}
			if (_lastWasCarriageReturn) {
				_lastWasCarriageReturn = false;
				if (b != '\n') {
					return '\r';
				}
			}
			return b;
		}
	}

	public void close() throws IOException {
		_in.close();
	}

}
