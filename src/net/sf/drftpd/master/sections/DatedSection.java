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
package net.sf.drftpd.master.sections;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

/**
 * @author mog
 * @version $Id: DatedSection.java,v 1.3 2004/02/10 00:03:08 mog Exp $
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
