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
package org.drftpd.mirroring.archivetypes;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Properties;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.config.FtpConfig;

/**
 * @author zubov
 * @version $Id: MoveReleaseToSpecificSlave.java,v 1.2 2004/04/23 00:47:24 mog Exp $
 */
public class MoveReleaseToSpecificSlave extends MoveReleaseToMostFreeSlave {

	private String slavename;

	public MoveReleaseToSpecificSlave() {
		slavename = null;
	}

	public ArrayList findDestinationSlaves() {
		if (slavename == null) {
			Properties props = new Properties();
			try {
				props.load(new FileInputStream("conf/archive.conf"));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			slavename = FtpConfig.getProperty(props, getSection().getName() + ".slavename");
		}
		ArrayList slaveList = new ArrayList();
		try {
			slaveList.add(_parent.getConnectionManager().getSlaveManager().getSlave(slavename));
		} catch (ObjectNotFoundException e) {
			throw new IllegalStateException("Unabled to get slave " + slavename + " from the SlaveManager");
		}
		return slaveList;
	}

}
