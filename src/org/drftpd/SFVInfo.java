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
package org.drftpd;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Serializable;

import org.drftpd.dynamicdata.Key;
import org.drftpd.vfs.CaseInsensitiveTreeMap;
import org.drftpd.vfs.VirtualFileSystemDirectory;

/**
 * @author mog
 * @version $Id$
 */
public class SFVInfo implements Serializable {

	private CaseInsensitiveTreeMap<String, Long> _entries = null;

	public static final Key SFV = new Key(VirtualFileSystemDirectory.class,
			"sfv", SFVInfo.class);

	/**
	 * Constructor for SFVInfo
	 */
	public SFVInfo() {

	}

	public CaseInsensitiveTreeMap<String, Long> getEntries() {
		return _entries;
	}

	public void setEntries(CaseInsensitiveTreeMap<String, Long> entries) {
		_entries = entries;
	}

	public int getSize() {
		return _entries.size();
	}

	public static SFVInfo importSFVInfoFromFile(BufferedReader in) throws IOException {
		String line;
		CaseInsensitiveTreeMap<String, Long> entries = new CaseInsensitiveTreeMap<String, Long>();
		try {
			while ((line = in.readLine()) != null) {
				if (line.length() == 0) {
					continue;
				}

				if (line.charAt(0) == ';') {
					continue;
				}

				int separator = line.indexOf(" ");

				if (separator == -1) {
					continue;
				}

				String fileName = line.substring(0, separator);
				String checkSumString = line.substring(separator + 1);
				Long checkSum;

				try {
					checkSum = Long.valueOf(checkSumString, 16);
				} catch (NumberFormatException e) {
					continue;
				}
				entries.put(fileName, checkSum);
			}
		} finally {
			if (in != null) {
				in.close();
			}
		}
		SFVInfo tmp = new SFVInfo();
		tmp.setEntries(entries);
		return tmp;
	}
}
