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
package org.drftpd.sections.conf;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.Properties;

import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.sections.SectionInterface;

/**
 * @author mog
 * @version $Id: DatedSection.java,v 1.4 2004/04/20 04:11:52 mog Exp $
 */
public class DatedSection implements SectionInterface {

	private SectionManager _mgr;
	private SimpleDateFormat _dateFormat;
	private String _name;
	private String _basePath;

	public DatedSection(SectionManager mgr, int i, Properties p) {
		_mgr = mgr;
		_name = FtpConfig.getProperty(p, i + ".name");
		_basePath = FtpConfig.getProperty(p, i + ".path");
		_dateFormat =
			new SimpleDateFormat(FtpConfig.getProperty(p, i + ".dated"));
		getBaseFile();
	}

	public String getName() {
		return _name;
	}

	public LinkedRemoteFile getBaseFile() {
		try {
			return _mgr.getConnectionManager().getRoot().lookupFile(_basePath);
		} catch (FileNotFoundException e) {
			return _mgr.getConnectionManager().getRoot().createDirectories(_basePath);
		}
	}

	public LinkedRemoteFileInterface getFile() {
		String dateDir = _dateFormat.format(new Date());
		try {
			return getBaseFile().lookupFile(dateDir);
		} catch (FileNotFoundException e) {
			try {
				return getBaseFile().createDirectory(dateDir);
			} catch (FileExistsException e1) {
				throw new RuntimeException(
					"ObjectExistsException when creating a directory which gave FileNotFoundException",
					e1);
			}
		}
	}

	public Collection getFiles() {
		return getBaseFile().getDirectories();
	}

	public String getPath() {
		return _basePath + "/" + _dateFormat.format(new Date());
	}
}
