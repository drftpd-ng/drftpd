package net.sf.drftpd.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: SafeFileWriter.java,v 1.4 2004/02/09 23:35:03 mog Exp $
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
