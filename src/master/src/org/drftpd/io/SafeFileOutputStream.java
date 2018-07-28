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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * @author mog
 * @version $Id$
 */
public class SafeFileOutputStream extends OutputStream {
	private File _actualFile;

	private FileOutputStream _out;

	private File _tempFile;

	// failed until it works
	private boolean failed = true;

	public SafeFileOutputStream(File file) throws IOException {
		_actualFile = file;

		if (!_actualFile.getAbsoluteFile().getParentFile().canWrite()) {
			throw new IOException("Can't write to target dir");
		}

		File dir = _actualFile.getParentFile();

		if (dir == null) {
			dir = new File(".");
		}
		String prefix = _actualFile.getName();
		while (prefix.length() < 3) {
			prefix = "x" + prefix;
		}
		_tempFile = File.createTempFile(prefix, null, dir);
		_out = new FileOutputStream(_tempFile);
	}

	public SafeFileOutputStream(String fileName) throws IOException {
		this(new File(fileName));
	}

	public void close() throws IOException {
		if (_out == null) {
			return;
		}
		_out.flush();
		_out.close();
		_out = null;
		if (!failed) {
			// logger.debug("Renaming " +
			// _tempFile + " (" + _tempFile.length() + ") to " + _actualFile);

			if (_actualFile.exists() && !_actualFile.delete()) {
				throw new IOException("delete() failed");
			}

			if (!_tempFile.exists()) {
				throw new IOException("source doesn't exist");
			}

			if (!_tempFile.renameTo(_actualFile)) {
				throw new IOException("renameTo(" + _tempFile + ", "
						+ _actualFile + ") failed");
			}
		}
	}

	public void flush() throws IOException {
		_out.flush();
	}

	public void write(int b) throws IOException {
		try {
			_out.write(b);
			// ensures the file gets written to
			failed = false;
		} catch (IOException e) {
			failed = true;
			throw e;
		}
	}
}
