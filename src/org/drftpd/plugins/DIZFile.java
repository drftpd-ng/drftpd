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
package org.drftpd.plugins;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.slave.RemoteIOException;

public class DIZFile {
	private static final Logger logger = Logger.getLogger(DIZFile.class);

	private LinkedRemoteFileInterface _file;

	private LinkedRemoteFileInterface _parent;

	private String _diz;

	private String _name;

	private int _total;

	public DIZFile(LinkedRemoteFileInterface file)
			throws FileNotFoundException, NoAvailableSlaveException {
		setFile(file);
		setParent(file);
		setName(file.getName());
		setDiz(fetchDiz());
		setTotal(fetchTotal());
	}

	private void setFile(LinkedRemoteFileInterface file) {
		_file = file;
	}

	private void setParent(LinkedRemoteFileInterface file)
			throws FileNotFoundException {
		_parent = file.getParentFile();

	}

	private void setDiz(String diz) {
		_diz = diz;
	}

	private void setName(String name) {
		_name = name;
	}

	private void setTotal(int total) {
		_total = total;
	}

	public LinkedRemoteFileInterface getFile() {
		return _file;
	}

	public LinkedRemoteFileInterface getParent() {
		return _parent;
	}

	public String getDiz() {
		return _diz;
	}

	public String getName() {
		return _name;
	}

	public int getTotal() {
		return _total;
	}

	public String fetchDiz() throws NoAvailableSlaveException, FileNotFoundException {
		RemoteSlave aSlave = _file.getAvailableSlaves().iterator().next();
		try {
			return aSlave.fetchDIZFileFromIndex(aSlave.issueDIZFileToSlave(_file));
		} catch (RemoteIOException e) {
			if (e.getCause() instanceof FileNotFoundException) {
				throw (FileNotFoundException) e.getCause();
			}
			aSlave.setOffline(e);
			throw new NoAvailableSlaveException();
		} catch (SlaveUnavailableException e) {
			throw new NoAvailableSlaveException();
		}
	}

	public int fetchTotal() {
		Integer total;
		Matcher m;
		Pattern p;
		String regex;

		regex = "[\\[\\(\\<\\:\\s][0-9oOxX]*/([0-9oOxX]*[0-9])[\\]\\)\\>\\s]";

		p = Pattern.compile(regex);

		// Compare the diz file to the pattern compiled above
		m = p.matcher(_diz);

		// We found the pattern in this diz file!
		if (m.find()) {
			total = new Integer(m.group(1).replaceAll("[oOxX]", "0"));
		} else {
			logger.warn("Could not retrieve total from dizFile");
			setTotal(0);
			return 0;
		}

		setTotal(total.intValue());

		return getTotal();
	}
}