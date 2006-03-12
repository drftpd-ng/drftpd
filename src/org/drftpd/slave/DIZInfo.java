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
package org.drftpd.slave;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SlaveUnavailableException;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.vfs.InodeHandle;

public class DIZInfo {
	private static final Logger logger = Logger.getLogger(DIZInfo.class);
	
	private String _diz;

	private int _total;

	public DIZInfo() {
	}

	public String getDiz() {
		return _diz;
	}
	
	public void setDiz(String diz) {
		_diz = diz;
	}

	public int getTotal() {
		return _total;
	}
	
	public void setTotal(int total) {
		_total = total;
	}

	public static DIZInfo fetchDiz(InodeHandle file) throws NoAvailableSlaveException, IOException {
		RemoteSlave aSlave = file.getAvailableSlaves().iterator().next();
		try {
			DIZInfo dizInfo = new DIZInfo();
			String diz = aSlave.fetchDIZFileFromIndex(aSlave.issueDIZFileToSlave(file));
			dizInfo.setDiz(diz);
			dizInfo.setTotal(fetchTotal(diz));
			return dizInfo;
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

	private static int fetchTotal(String diz) throws IOException {
		Integer total;
		Matcher m;
		Pattern p;
		String regex;

		regex = "[\\[\\(\\<\\:\\s][0-9oOxX]*/([0-9oOxX]*[0-9])[\\]\\)\\>\\s]";

		p = Pattern.compile(regex);

		// Compare the diz file to the pattern compiled above
		m = p.matcher(diz);

		// We found the pattern in this diz file!
		if (m.find()) {
			total = new Integer(m.group(1).replaceAll("[oOxX]", "0"));
		} else {
			throw new IOException("Could not retrieve total from dizFile");
		}

		return total.intValue();
	}
}