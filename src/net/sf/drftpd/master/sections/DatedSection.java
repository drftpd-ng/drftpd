package net.sf.drftpd.master.sections;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: DatedSection.java,v 1.2 2003/12/23 13:38:20 mog Exp $
 */
public class DatedSection implements Section {

	private SimpleDateFormat _dateFormat;
	private String _name;
	private LinkedRemoteFile _baseDir;

	public DatedSection(
		String name,
		LinkedRemoteFile baseDir,
		SimpleDateFormat dateFormat) {
		_name = name;
		_baseDir = baseDir;
		_dateFormat = dateFormat;
	}

	public String getName() {
		return _name;
	}

	public LinkedRemoteFile getFile() {
		String dateDir = _dateFormat.format(new Date());
		try {
			return _baseDir.lookupFile(dateDir);
		} catch (FileNotFoundException e) {
			try {
				return _baseDir.createDirectory(null, null, dateDir);
			} catch (ObjectExistsException e1) {
				throw new RuntimeException(
					"ObjectExistsException when creating a directory which gave FileNotFoundException",
					e1);
			}
		}
	}

	public Collection getFiles() {
		return _baseDir.getDirectories();
	}
}
