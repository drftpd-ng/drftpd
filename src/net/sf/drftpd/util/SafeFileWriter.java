package net.sf.drftpd.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author mog
 * @version $Id: SafeFileWriter.java,v 1.2 2004/02/03 00:28:29 mog Exp $
 */
public class SafeFileWriter extends Writer {
	private File _actualFile;
	private FileWriter _out;
	private File _tempFile;
	private boolean failed = false;

	/**
	 * @see java.io.File#File(java.io.File)
	 */
	public SafeFileWriter(File file) throws IOException {
		_actualFile = file.getAbsoluteFile();
		if (!_actualFile.getParentFile().canWrite())
			throw new IOException("Can't write to target dir");
		_tempFile =
			File.createTempFile(
				_actualFile.getName(),
				null,
				_actualFile.getParentFile());
		_out = new FileWriter(_tempFile);
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
			//Logger.getLogger(SafeFileWriter.class).debug("Renaming "+_tempFile+" ("+_tempFile.length()+") to "+_actualFile);
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
