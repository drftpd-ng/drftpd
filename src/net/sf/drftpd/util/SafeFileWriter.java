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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: SafeFileWriter.java,v 1.7 2004/04/22 02:10:12 mog Exp $
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
		if (!_actualFile.getAbsoluteFile().getParentFile().canWrite())
			throw new IOException("Can't write to target dir");
		
		File dir = _actualFile.getParentFile();
		if(dir == null) dir = new File(".");
		_tempFile =
			File.createTempFile(
				_actualFile.getName(),
				null,
				dir);
		_out = new OutputStreamWriter(new FileOutputStream(_tempFile), "UTF-8");
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
			Logger.getLogger(SafeFileWriter.class).debug("Renaming "+_tempFile+" ("+_tempFile.length()+") to "+_actualFile);
			if (_actualFile.exists() && !_actualFile.delete())
				throw new IOException("delete() failed");
			if (!_tempFile.exists())
				throw new IOException("source doesn't exist");
			if (!_tempFile.renameTo(_actualFile))
				throw new IOException(
					"renameTo(" + _tempFile + ", " + _actualFile + ") failed");
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
