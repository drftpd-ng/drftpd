package net.sf.drftpd.util;

import java.io.IOException;
import java.io.OutputStream;

/**
 * AddAsciiOutputStream that ignores \r and adds an \r before every \n.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
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
