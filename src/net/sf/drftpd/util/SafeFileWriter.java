package net.sf.drftpd.util;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

/**
 * @author mog
 * @version $Id: SafeFileWriter.java,v 1.1 2004/01/20 06:59:01 mog Exp $
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
		_tempFile =
			File.createTempFile(file.getName(), null, file.getParentFile());
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
			_actualFile.delete();
			_tempFile.renameTo(_actualFile);
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
