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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.io.*;
import java.nio.charset.StandardCharsets;

/**
 * @author mog
 * @version $Id$
 */
public class SafeFileWriter extends Writer {
	private File _actualFile;

	private OutputStreamWriter _out;

	private File _tempFile;

	private boolean failed = false;

	/**
	 * @see java.io.File#File(java.io.File)
	 */
	public SafeFileWriter(File file) throws IOException {
		_actualFile = file;

		if (!_actualFile.getAbsoluteFile().getParentFile().canWrite()) {
			throw new IOException("Can't write to target dir");
		}

		File dir = _actualFile.getParentFile();

		if (dir == null) {
			dir = new File(".");
		}

		_tempFile = File.createTempFile(_actualFile.getName(), null, dir);
		_out = new OutputStreamWriter(new FileOutputStream(_tempFile), StandardCharsets.UTF_8);
	}

	/**
	 * @see java.io.File#File(java.lang.String)
	 */
	public SafeFileWriter(String fileName) throws IOException {
		this(new File(fileName));
	}

	public void close() throws IOException {
		_out.flush();
		_out.close();

		if (!failed) {
            LogManager.getLogger(SafeFileWriter.class).debug("Renaming {} ({}) to {}", _tempFile, _tempFile.length(), _actualFile);

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

	public void write(char[] cbuf, int off, int len) throws IOException {
		try {
			_out.write(cbuf, off, len);
		} catch (IOException e) {
			failed = true;
			throw e;
		}
	}
}
