/*
 * Created on 2003-nov-28
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream filter that strips the \r from \r\n sequences.
 * 
 * @author mog
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
