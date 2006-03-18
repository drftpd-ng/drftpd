package org.drftpd.slave.async;

import java.io.File;

import org.drftpd.slave.CaseInsensitiveHashtable;

/**
 * @author zubov
 * @version $Id$
 */
public class AsyncResponseRemerge extends AsyncResponse {
	private CaseInsensitiveHashtable _files;

	private String _directory;

	public AsyncResponseRemerge(String directory, CaseInsensitiveHashtable files) {
		super("Remerge");
		_files = files;
		if (File.separatorChar == '\\') { // stupid win32 hack
			directory = directory.replaceAll("\\\\", "/");
		}
		if (directory.indexOf('\\') != -1) {
			throw new RuntimeException(
					"\\ is not an acceptable character in a directory path");
		}
		_directory = directory;
	}

	public String getDirectory() {
		return _directory;
	}

	public CaseInsensitiveHashtable getFiles() {
		return _files;
	}

	public String toString() {
		return getClass().getName() + "[path=" + getDirectory() + "]";
	}
}
